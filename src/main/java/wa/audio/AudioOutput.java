package wa.audio;
/**
 * Copyright 2016-2017 IBM Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import wa.audio.AudioSocket.SocketNotAvailable;

public class AudioOutput {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(AudioOutput.class);

    /**
     * Class with a specific name so we can tell when the lock it held.
     */
    private class StateLock {
        StateLock() {}
    }
    private final StateLock stateLock = new StateLock();
    
    private byte[] buffer;
    private SourceDataLine speaker;
    private AudioSocket audioSocket;

    private Thread speakerThread;
    
    private Runnable endOfAudioOutputTask;
    
    private volatile boolean audioDataComplete = false;
    private volatile boolean outputDisabled = false;
    private volatile boolean outputToAudioSocket = false;
    private volatile boolean useDefaultAudio = true;

    private AudioFormat format = new AudioFormat(AudioConstants.PlaybackSampleRate,
            AudioConstants.PlaybackSampleSizeInBits, AudioConstants.PlaybackChannels, AudioConstants.isSigned,
            AudioConstants.isBigEndian);


    private boolean audioDevicesListed = false; // only accessed by audioOutputThread

    private static AudioOutput sharedInstance;

    public void setUseDefaultAudio(boolean value) {
        useDefaultAudio = value; // volatile
    }

    public boolean isOutputToAudioSocket() {
        return outputToAudioSocket; // volatile
    }

    public void setOutputToAudioSocket(boolean value) {
        outputToAudioSocket = value; // volatile
    }

    public void setAudioSocket(AudioSocket audioSocket) {
        synchronized (stateLock) {
            this.audioSocket = audioSocket;
        }
    }

    public void writeNativeBytes(byte[] bytes) {
        if (outputDisabled) {
            LOG.debug(">>> Output disabled...");
            return;
        }
        LOG.debug(">>>> AudioOutput.writeNativeBytes adding incoming audio data...");
        
        // Add incoming bytes to the buffer
        synchronized (stateLock) {
            int byteSize = bytes.length;

            if (this.buffer == null) {
                // create a new buffer
                this.buffer = new byte[byteSize];
                System.arraycopy(bytes, 0, this.buffer, 0, byteSize);
            } else {
                // add to existing buffer
                int oldLength = this.buffer.length;
                byte[] newSpeakerBuffer = new byte[oldLength + byteSize];
                System.arraycopy(this.buffer, 0, newSpeakerBuffer, 0, oldLength);
                System.arraycopy(bytes, 0, newSpeakerBuffer, oldLength, byteSize);
                this.buffer = newSpeakerBuffer;
            }
        }
    }

    private AudioOutput() {
        speaker = null;
        clearBuffer();
    }

    public static AudioOutput getInstance() {
        if (sharedInstance == null) {
            sharedInstance = new AudioOutput();
        }
        return sharedInstance;
    }

    public synchronized void startAudioOutput(Runnable endOfAudioOutputTask) {
        this.endOfAudioOutputTask = endOfAudioOutputTask;
        if (speakerThread != null) {
            speakerThread.interrupt();
        }
        // Start the output thread, data will be written
        // to internal buffer with 'writeNativeBytes' method
        speakerThread = audioOutputThread();
        speakerThread.start();
    }

    public void finish() {
        LOG.debug("AudioOutput.finish()");
        off();
    }
    
    public void cancel() { // ZZZ - how to correctly synchronize?
        LOG.debug("AudioOutput.cancel()");
        clearBuffer();
        if (null != speakerThread) {
            speakerThread.interrupt();
        }
    }
    
    private void clearBuffer() {
        synchronized (stateLock) {
            buffer = null;
        }
    }
        
    private synchronized void off() {
        LOG.debug("AudioOutput.off()");
        if (speakerThread == null) {
            return;
        }

        // Calling an interrupt(), join() will cause the speaker
        // thread to write out its remaining buffer and wait for
        // the thread to finish running
        try {
            speakerThread.interrupt();
            speakerThread.join();
        } catch (InterruptedException e) {
            // Restore the interrupted state
            Thread.currentThread().interrupt();
        } finally {
            speakerThread = null;
            clearBuffer();
        }
    }

    private void runEndOfAudioOutputTask() {
        LOG.debug("AudioOutput.runEndOfAudioOutputTask()");
        if (null != endOfAudioOutputTask) {
            endOfAudioOutputTask.run();
        }
    }
    
    private Thread audioOutputThread() {
        return new Thread(() -> {
            Thread.currentThread().setName("Audio Output - " + System.currentTimeMillis());
            int writeCount = 0;
            int byteCount = 0;
            try {
                if (!outputToAudioSocket) { // volatile
                    // added these lines to get Mixer to support DSP board.
                    Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
                    Mixer.Info finalMixerInfo = null;
                    DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, this.format);

                    if (!useDefaultAudio) { // volatile
                        for (Mixer.Info aMixerInfo : mixerInfo) {
                            if (!audioDevicesListed) {
                                LOG.info(String.format("Mixer Name:\"%s\" Description:\"%s\"", aMixerInfo.getName(), aMixerInfo.getDescription()));
                            }
                            if (aMixerInfo.getDescription().contains("USB") && !aMixerInfo.getName().contains("Port")) {
                                finalMixerInfo = aMixerInfo;
                            }
                        }
                        audioDevicesListed = true;
                        if (finalMixerInfo != null) {
                            LOG.info(String.format("Using audio output: %s", finalMixerInfo.getDescription()));
                            Mixer mixer = AudioSystem.getMixer(finalMixerInfo);
                            this.speaker = (SourceDataLine) mixer.getLine(dataLineInfo);
                        }
                    }
                    if (null == finalMixerInfo) {
                        // Get the default audio output
                        LOG.info("Using default audio output");
                        this.speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
                    }
                    speaker.open(format);
                    speaker.start();
                    while (true) {
                        try {
                            synchronized (stateLock) {
                                int writeOutSize = this.speaker.available();

                                // Push as much as possible to the speaker without blocking and remove data from buffer
                                if (buffer != null && buffer.length > 0 && writeOutSize > 0) {
                                    if (writeOutSize > buffer.length) writeOutSize = buffer.length;
                                    writeOutSize &= ~3; // round down to frame boundary
                                    int currentBufferOffset = buffer.length - writeOutSize;
                                    writeCount++;
                                    byteCount += writeOutSize;
                                    System.out.print('@');
                                    this.speaker.write(this.buffer, 0, writeOutSize);
                                    byte[] newSpeakerBuffer = new byte[currentBufferOffset];
                                    System.arraycopy(buffer, writeOutSize, newSpeakerBuffer, 0, currentBufferOffset);
                                    this.buffer = newSpeakerBuffer;
                                }
                            }

                            Thread.sleep(80);
                        } catch (InterruptedException e) {
                            LOG.debug(" AudioOutput - Interrupted... Finish audio output to speaker and stop.");
                            synchronized (stateLock) {
                                if (buffer != null) {
                                    // Frame size is 4, so the framing needs to be a multiple of 4
                                    // Should not make an audible difference to clip 1-3 bytes from the end
                                    // of the speaker byte array
                                    int length = buffer.length - (buffer.length % 4);
                                    speaker.write(buffer, 0, length);
                                }
                                int average = (writeCount > 0 ? byteCount/writeCount : 0);
                                LOG.info(String.format("\nAudio output. Times: %d Total Bytes: %d Avg: %d", writeCount, byteCount, average));
                                //speaker.drain();
                                speaker.flush();
                                speaker.close();
                                runEndOfAudioOutputTask();
                            }
                            break;
                        }
                    }
                }
                else { // Send data to the Audio Socket
                    try {
                        Socket socket;
                        synchronized (stateLock) {
                            socket = audioSocket.getSocket();
                        }
                        boolean socketAvailable = !socket.isClosed() && socket.isConnected();
                        while (true) {
                            try {
                                synchronized (stateLock) {
                                    // Open the output stream on the socket
                                    OutputStream audioOut = socket.getOutputStream();

                                    // Output to the audio socket and remove data from buffer
                                    if (buffer != null && buffer.length > 0) {
                                        if (!socketAvailable) {
                                            LOG.info("Discarding response. Output set to Audio Socket, but the socket is closed or not connected.");
                                        }
                                        else {
                                            int startingBufferLength = buffer.length;
                                            writeCount++;
                                            byteCount += startingBufferLength;
                                            System.out.print('>');
                                            audioOut.write(this.buffer, 0, startingBufferLength);
                                        }
                                        this.buffer = null;
                                    }
                                    audioOut.flush();
                                }

                                Thread.sleep(80);
                            } catch (IOException e) {
                                System.err.println("Error sending audio data to the audio socket: " + e);
                                e.printStackTrace();
                                socketAvailable = false;
                            } catch (InterruptedException e) {
                                LOG.debug(" AudioOutput - Interrupted... Finish audio socket output and stop.");
                                synchronized (stateLock) {
                                    if (buffer != null && buffer.length > 0) {
                                        if (socketAvailable) {
                                            OutputStream audioOut;
                                            try {
                                                audioOut = audioSocket.getSocket().getOutputStream();
                                                audioOut.write(buffer, 0, buffer.length);
                                                audioOut.flush();
                                            } catch (IOException | AudioSocket.SocketNotAvailable e1) {
                                                System.err.println("Error sending audio data to the audio socket: " + e);
                                                e1.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                int average = (writeCount > 0 ? byteCount/writeCount : 0);
                                LOG.info(String.format("\nAudio output. Times: %d Total Bytes: %d Avg: %d", writeCount, byteCount, average));
                                break;
                            }
                        }
                    } catch (AudioSocket.SocketNotAvailable e) {
                        LOG.error("Error sending audio data to the audio socket: " + e);
                        e.printStackTrace();
                    }

                }
            } catch (LineUnavailableException e) {
                LOG.error("Unable to create data line to speaker.");
            } catch (RuntimeException re) {
                re.printStackTrace();
            } finally {
                // Anything need to be done here?
            }
        });
    }

    public void enable() {
        LOG.debug(">>> ENABLE Audio Output...");
        outputDisabled = false;
    }
    /**
     * Stop output and don't allow more output until re-enabled.
     */
    public void stop() {
        LOG.debug(">>> STOP Audio Output...");
        // Stop play back
        synchronized (stateLock) {
            outputDisabled = true; // Do this first!
            clearBuffer();
    
            if (null != speaker) {
                speaker.drain();
                speaker.flush();
                speaker.close();
            }
            if (null != audioSocket) {
                audioSocket.clearInput();
                try {
                    audioSocket.getSocket().getOutputStream().flush();
                } catch (IOException | SocketNotAvailable e) {
                    LOG.warn(e);
                }
            }
        }
    }
}
