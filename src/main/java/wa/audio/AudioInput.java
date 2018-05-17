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
package wa.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import okio.ByteString;
import wa.client.Client;
import wa.client.ClientHelpers;

public class AudioInput {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(AudioInput.class);

    public enum InputSource {
        MICROPHONE,
        AUDIO_SOCKET
    }
    private InputSource inputSource;

    private boolean micIsOpen = false;

    private Client client = null;

    private AudioSocket audioSocket = null;

    // optional prompt info to pass to STT options
    private JSONObject sttOptions = null;
    
    // Thread to capture audio until cancelled or timed out
    private CaptureThread captureThread = null;


    public AudioInput(String name, Client client, AudioSocket audioSocket) {
        this.client = client;
        this.audioSocket = audioSocket;

        this.inputSource = InputSource.MICROPHONE;
    }

    public AudioInput(Client client, AudioSocket audioSocket) {
        this("Audio Input", client, audioSocket);
    }
    
    private synchronized Client getClient() {
        return this.client;
    }
    private synchronized InputSource getInputSource() {
        return this.inputSource;
    }
    public synchronized void setInputSource(InputSource inputSource) {
        this.inputSource = inputSource;
    }

    private synchronized JSONObject getSttOptions() {
        return this.sttOptions;
    }
    public void setSttOptions(JSONObject sttOptions) {
        this.sttOptions = sttOptions;
    }

    private class CaptureThread extends Thread {

        public CaptureThread() {
            super("Audio Capture");
            this.setDaemon(true);
        }
        
        @Override
        public void run() {
            try {
                AudioInput.this.captureInternal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }            
        }
    }
    
    public synchronized boolean micIsOpen() {
        return this.micIsOpen;
    }

    /**
     * Creates a thread to capture audio input from the microphone or the audio socket
     * and captures audio until micClose is called or a maximum amount of audio or 
     * blank audio is received.
     */
    public synchronized void capture() {
         if (null != captureThread) {
             captureThread.interrupt();
             try {
                captureThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
         }
         captureThread = new CaptureThread();
         captureThread.start();
    }
    
    private void captureInternal() throws InterruptedException {
        boolean writingToServer = false;
        byte[] audioDataBuffer;
        int bufferSize;
        AudioInputDevice audioInputDevice = null;
        
        try {
            switch (getInputSource()) {
            case MICROPHONE:
                audioInputDevice = new MicrophoneInputDevice();
                LOG.debug("Audio source is microphone");
                break;
            case AUDIO_SOCKET:
                audioInputDevice = new AudioSocketInputDevice(audioSocket);
                LOG.debug("Audio source is socket");
                break;
            default:
                audioInputDevice = new MicrophoneInputDevice();
                LOG.debug("Audio source defaulting to microphone");
                break;
            }
    
            this.micOpen();

            int captureWindowSize = AudioConstants.RecordWindowSizeInSeconds * audioInputDevice.getDataRate();
            bufferSize = audioInputDevice.getBufferSize();
            audioDataBuffer = new byte[bufferSize];
            
            this.client.writeToServer(ClientHelpers.getClientStartAudioAction(getSttOptions()).toString());
            writingToServer = true;
            this.setSttOptions(null);
            this.client.getIndicator().on();
            this.client.clearServerWriteLogging(); // Clears out the log data so we can get a correct summary at the end
            
            // 10 seconds max or until whenever speaker starts to stream. The while
            // loop will break whenever the pause() method is called, or in the case
            // of a disconnect
            int bytesRead = 0;
            int bytesReturned;
            long now = System.currentTimeMillis();
            long start = now;
            int consecutiveZeroBytesRead = 0;
            while (micIsOpen() && this.client.isServerConnectionReady() && bytesRead < captureWindowSize && consecutiveZeroBytesRead < 200 && now < (start + ((AudioConstants.RecordWindowSizeInSeconds + 2) * 1000))) {
                bytesReturned = audioInputDevice.read(audioDataBuffer, 0, bufferSize, 300);
                if (bytesReturned > 0) {
                    consecutiveZeroBytesRead = 0;
                    bytesRead += bytesReturned;
                    this.client.writeToServer(ClientHelpers.getClientAudioDataAction(ByteString.of(audioDataBuffer, 0, bytesReturned)).toString());
                }
                else {
                    consecutiveZeroBytesRead++;
                    System.out.print('~');
                    Thread.sleep(100);
                }
                now = System.currentTimeMillis();
            }
            if (bytesRead == 0) {
                LOG.error("No audio input received");
            }
        } catch (AudioInput.AudioInputDevice.SourceNotAvailableException e) {
            LOG.error("Source not available: " + e);
            e.printStackTrace();
        } finally {
            client.getIndicator().off();
            client.logFinalServerWriteStatus();            
            if (this.client.isServerConnectionReady() && writingToServer) {
                this.client.writeToServer(ClientHelpers.getClientEndAudioAction().toString());
            }
            if (null != audioInputDevice) {
                audioInputDevice.drainInput();
                audioInputDevice.release();
                audioInputDevice = null;
            }
            
            client.wakeupTriggerIsAllowed();

            // TODO is this still needed?
            // Enable a client 'trigger wake up safety' in case we never get a response
            client.scheduleWakeupTriggerEnable();
        }
    }

    public synchronized void micClose() {
        LOG.info("micClose: Microphone close.");
        this.micIsOpen = false;
        client.onMicClose();
    }
    
    public synchronized void micOpen() throws InterruptedException {
        LOG.info("micOpen: Microphone open.");
        this.micIsOpen = true;
        client.onMicOpen();
    }
    
    
    private interface AudioInputDevice {
        
        @SuppressWarnings("unused")
        class SourceNotAvailableException extends Exception {
            private static final long serialVersionUID = 1L;

            public SourceNotAvailableException() {
                super();
            }

            public SourceNotAvailableException(String message, Throwable cause) {
                super(message, cause);
            }

            public SourceNotAvailableException(String message) {
                super(message);
            }

            public SourceNotAvailableException(Throwable cause) {
                super(cause);
            }
        }
        
        int getBufferSize() throws SourceNotAvailableException;
        
        int getDataRate() throws SourceNotAvailableException;
        
        int read(byte[] buffer, int offset, int len, int timeout) throws SourceNotAvailableException;
        
        void drainInput();
        
        void release();
    }
    
    private static class AudioSocketInputDevice implements AudioInputDevice {

        private AudioSocket audioSocket;
        
        AudioSocketInputDevice(AudioSocket audioSocket) throws SourceNotAvailableException {
            if (!audioSocket.hasConnection()) {
                throw new SourceNotAvailableException("No connected source");
            }
            this.audioSocket = audioSocket;
            
        }
        
        @Override
        public int getBufferSize() {
            return 16000;   // ZZZ - Hardcoded to match microphone for now
        }

        @Override
        public int getDataRate() {
            return 32000;   // ZZZ - Hardcoded to match microphone for now
        }

        @Override
        public int read(byte[] buffer, int offset, int len, int timeout) throws SourceNotAvailableException {
            int bytesRead = 0;
            try {
                bytesRead = audioSocket.read(buffer, offset, len, timeout);
            } catch (AudioSocket.SocketNotAvailable e) {
                throw new SourceNotAvailableException();
            }
            if (bytesRead > 0) {
                System.out.print('<');
            }
            return bytesRead;
        }
        
        @Override
        public void drainInput() {
            audioSocket.clearInput();
        }

        @Override
        public void release() {
            // TODO Auto-generated method stub
        }
        
    }
    
    private static class MicrophoneInputDevice implements AudioInputDevice {

        @SuppressWarnings("unused")
        class MicrophoneStateException extends Exception {
            private static final long serialVersionUID = 1L;

            public MicrophoneStateException() {
                super();
            }

            public MicrophoneStateException(String message, Throwable cause) {
                super(message, cause);
            }

            public MicrophoneStateException(String message) {
                super(message);
            }

            public MicrophoneStateException(Throwable cause) {
                super(cause);
            }
        }

        private TargetDataLine microphone;
        private AudioFormat micFormat = new AudioFormat(AudioConstants.RecordSampleRate, AudioConstants.RecordSampleSizeInBits,
                AudioConstants.RecordChannels, AudioConstants.isSigned, AudioConstants.isBigEndian);


        MicrophoneInputDevice() throws SourceNotAvailableException {
            // Microphone format is 1 channel, 2 bytes, 16000 samples/sec, little endian
            try {
                this.microphone = AudioSystem.getTargetDataLine(micFormat);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, micFormat);
                this.microphone = (TargetDataLine) AudioSystem.getLine(info);
                this.microphone.open(micFormat);
                if (!this.microphone.isOpen()) {
                    throw new SourceNotAvailableException(new MicrophoneStateException("Microphone is not open for capture"));
                }
            } catch (LineUnavailableException e) {
                throw new SourceNotAvailableException(e);
            }
            this.microphone.start();
        }
        
        @Override
        public int getBufferSize() {
            return microphone.getBufferSize();
        }
        
        @Override
        public int getDataRate() {
            return micFormat.getChannels() * (int) micFormat.getSampleRate() * (micFormat.getSampleSizeInBits() / 8);
        }

        @Override
        public int read(byte[] buffer, int offset, int len, int timeout) {
            int bytesRead = microphone.read(buffer, offset, len);
            if (bytesRead > 0) {
                System.out.print('%');
            }
            return bytesRead;
        }

        @Override
         public void drainInput() {
            // Nothing to do
        }
 
        @Override
        public void release() {
            if (null != this.microphone) {
                this.microphone.close();
                this.microphone = null;
            }
        }
        
    }
}
