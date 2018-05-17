import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import wa.audio.AudioConstants;

/**
 * Copyright 2017 IBM Corporation. All Rights Reserved.
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


public class TestAudioInputSocket {

    private static AudioFormat format = new AudioFormat(AudioConstants.PlaybackSampleRate,
            AudioConstants.PlaybackSampleSizeInBits, AudioConstants.PlaybackChannels, AudioConstants.isSigned,
            AudioConstants.isBigEndian);
    private static long frameSize = (AudioConstants.PlaybackChannels * AudioConstants.PlaybackSampleSizeInBits) / 8;
    
    private static Socket audioSocket = null;

    private static Thread audioSocketReadThread() {
        return new Thread(() -> {
            InputStream in = null;
            int BUFFER_CHUNK_SIZE = 16384;
            byte[] buffer = new byte[BUFFER_CHUNK_SIZE];
            try {
                in = audioSocket.getInputStream();
                int bytesRead = -1;
                int bufferOffset = 0;
                long timeBetweenReceive = 0;
                while (true) {
                    if (in.available() > 0) {
                        bytesRead = in.read(buffer, bufferOffset, buffer.length - bufferOffset);
                        System.out.println(String.format("Read %d bytes from audio socket...", bytesRead));
                        timeBetweenReceive = 0;
                        bufferOffset += bytesRead;
                        int bufferSize = buffer.length;
                        if (bufferSize - bufferOffset < (BUFFER_CHUNK_SIZE / 2)) {
                            // Extend the buffer
                            byte[] newBuffer = new byte[bufferSize + BUFFER_CHUNK_SIZE];
                            System.arraycopy(buffer, 0, newBuffer, 0, bufferSize);
                            buffer = newBuffer;
                            System.out.println(String.format("Extended buffer to %d. Offset is %d...", buffer.length, bufferOffset));
                        }
                    }

                    Thread.sleep(100);
                    if (bufferOffset > 0) {
                        timeBetweenReceive += 100;
                        if (timeBetweenReceive > 5000) {
                            System.out.println(String.format("Total audio data %d bytes.", bufferOffset));
                            // Write the buffer out to a file
                            File outFile = new File("audioResponse.wav");
                            if (outFile.exists()) {
                                outFile.delete();
                            }
                            outFile.createNewFile();
                            ByteArrayInputStream dataInputStream = new ByteArrayInputStream(buffer);
                            long frames = bufferOffset / frameSize;
                            AudioInputStream audioData = new AudioInputStream(dataInputStream, format, frames);
                            AudioSystem.write(audioData, AudioFileFormat.Type.WAVE, outFile);
                            // Close the current buffer
                            buffer = new byte[BUFFER_CHUNK_SIZE];
                            bufferOffset = 0;
                        }
                    }
                }
            } catch(InterruptedException ie) {
            } catch (RuntimeException | IOException re) {
                re.printStackTrace();
            } finally {
                if (null != in) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            usageError();
        }

        int cmdPort = 0;
        int audioPort = 0;
        String hostName;
        String audioFilePathString;

        hostName = args[0];
        audioFilePathString = args[3];

        // Try to convert port# strings to integers
        try {
            cmdPort = Integer.parseInt(args[1]);
            audioPort = Integer.parseInt(args[2]);
        }
        catch (NumberFormatException e) {
            System.err.println("Port values must be numbers");
            usageError();
        }

        // Try to find the audio File
        File audioFile = new File(audioFilePathString);
        if (!audioFile.exists()) {
            System.err.println("Audio file not found.");
            usageError();
        }

        // Play the audio for them
        // playAudio(audioFile);

        // Try to open the command and audio socket connections
        Socket cmdSocket = null;
        int port = cmdPort;
        String socketName = "Command";
        try {
            cmdSocket = new Socket(hostName, port);
            port = audioPort;
            socketName = "Audio";
            audioSocket = new Socket(hostName, port);
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Problem with connection to " + socketName + " Socket at " +
                    hostName + ":" + port);
            System.exit(1);
        }
        try (
                PrintWriter out = new PrintWriter(cmdSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
                ) {
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            String fromServer;
            String fromUser;

            printHelp();

            Thread audioReadThread = audioSocketReadThread();
            audioReadThread.start();

            boolean noServerReplyNeeded = false;
            while (((fromServer = in.readLine()) != null) || noServerReplyNeeded) {
                if (null != fromServer) {
                    System.out.println("Server: " + fromServer);
                    if (fromServer.equals("DONE"))
                        break;
                }
                noServerReplyNeeded = false;

                fromUser = stdIn.readLine();
                if (fromUser != null) {
                    System.out.println("Client: " + fromUser);
                    if ("H".equalsIgnoreCase(fromUser)) {
                        printHelp();
                        noServerReplyNeeded = true;
                    }
                    else if ("S".equals(fromUser)) {
                        sendAudio(out, audioSocket, audioFile);
                    }
                    else {
                        out.println(fromUser);
                    }
                }
            }
            audioReadThread.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-3);
        }
    }

    private static void printHelp() {
        System.out.println("===== Help =====");
        System.out.println("H  - This help message");
        System.out.println("S  - Send the 'read audio from socket' command to the audio client and send the audio file");
        System.out.println("xx - Send the 'xx' command to the audio client");
        System.out.println("================");
    }

    /**
     * Send the audio in the audio file to the audio socket and send the command to the client to read from the socket.
     * 
     * @param out Client command output writer
     * @param audioSocket Client audio socket (already connected and open)
     * @param audioFile The audio file to send
     */
    private static void sendAudio(PrintWriter out, Socket audioSocket, File audioFile) {
        System.out.println("Sending audio file: " + audioFile.getName());
        playAudio(audioFile);

        // Try to open the audio file as audio input
        AudioInputStream audioStream = null;
        try {
            audioStream = AudioSystem.getAudioInputStream(audioFile);
        } catch (UnsupportedAudioFileException | IOException e1) {
            System.err.println("Audio file format not supported (or not an audio file): " + audioFile.getPath());
            System.exit(-4);
        }

        // Now stream the audio data to the socket...
        try {
            // Now that the file is open, open up the output stream on the socket
            OutputStream audioOut = audioSocket.getOutputStream();

            // Now send the command to read from the Audio Socket
            out.println("RAS");

            int BUFFER_SIZE = 4096;
            byte[] bytesBuffer = new byte[BUFFER_SIZE];
            int bytesRead = -1;
            while ((bytesRead = audioStream.read(bytesBuffer)) != -1) {
                audioOut.write(bytesBuffer, 0, bytesRead);
            }
            audioOut.flush();
        } catch (IOException e) {
            System.err.println("Error sending audio data to the audio socket: " + e);
            e.printStackTrace();
        }
    }

    private static void playAudio(File audioFile) {
        // Try to open the audio file as audio input
        AudioInputStream audioStream = null;
        try {
            audioStream = AudioSystem.getAudioInputStream(audioFile);
        } catch (UnsupportedAudioFileException | IOException e1) {
            System.err.println("Audio file format not supported (or not an audio file): " + audioFile.getPath());
            usageError();
        }
        AudioFormat format = audioStream.getFormat();

        // Play the audio file so they know what they are getting...
        System.out.println("Here is your audio...");
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();
            // Play the audio
            int BUFFER_SIZE = 4096;
            byte[] bytesBuffer = new byte[BUFFER_SIZE];
            int bytesRead = -1;
            while ((bytesRead = audioStream.read(bytesBuffer)) != -1) {
                audioLine.write(bytesBuffer, 0, bytesRead);
            }
            audioLine.drain();
            audioLine.close();
            audioStream.close();
        } catch (LineUnavailableException e1) {
            System.err.println("An audio line capable of playing this audio is not available." + e1);
            System.exit(-2);
        } catch (IOException e) {
            System.err.println("Problem reading or playing audio file" + e);
            System.exit(-3);
        }

    }

    private static void usage() {
        System.out.println("Usage: <host-name/IP> <command-port> <audio-port> <.wav file path>");
    }

    private static void usageError() {
        usage();
        System.exit(-1);
    }
}
