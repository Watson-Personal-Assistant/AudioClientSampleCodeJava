package wa.client;
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
/*
 * Portions Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * See DEPENDENCIES.md for notice.
 */ 

import java.net.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import main.Version;
import wa.audio.AudioInput;

import java.io.*;

public class SocketCommandProcessor extends Thread {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(SocketCommandProcessor.class);

    private Client client;
    private int portNumber;
    private ServerSocket serverSocket;
    private PrintWriter socketOutputWriter;

    static class SocketProtocol {
        private static final int WAITING = 0;
        private static final int ACCEPT_COMMAND = 1;

        public static final String DONE = "DONE";
        public static final String OK = "OK";
        public static final String UNKNOWN_CMD = "?";

        public static final String MIC_CLOSE = "micClose";
        public static final String MIC_OPEN = "micOpen";
        public static final String MIC_TRIGGER_IS_ALLOWED = "micWakeUpAllowed";
        public static final String MIC_TRIGGER_NOT_ALLOWED = "micWakeUpNotAllowed";

        public static final String PLAYBACK_RESUME = "playbackResume";
        public static final String PLAYBACK_STOP = "playbackStop";

        public static final String VOLUME_DECREASE = "volumeDown";
        public static final String VOLUME_INCREASE = "volumeUp";
        public static final String VOLUME_MUTE = "volumeMute";
        public static final String VOLUME_UNMUTE = "volumeUnmute";

        public static final String SERVER_CONNECTED = "serverConnected";
        public static final String SERVER_CONNECTING = "serverConnecting";
        public static final String SERVER_CONNECTION_READY = "serverConnectionReady";
        public static final String SERVER_NOT_CONNECTED = "serverNotConnected";

        public enum Command {
            NONE,
            READ_MICROPHONE,
            READ_AUDIO_SOCKET,
            OUTPUT_TO_SPEAKER,
            OUTPUT_TO_AUDIO_SOCKET,
            FINISHED_PLAYING,
            EXIT
        };
        private Command currentCommand;

        private int state = WAITING;
        private String okWithVersion = OK + " " + Version.getInstance().getVersion();
        
        public Command getCommand() {
            return currentCommand;
        }

        public String processInput(String theInput) {
            String theOutput = null;

            if (state == WAITING) {
                theOutput = okWithVersion;
                currentCommand = Command.NONE;
                state = ACCEPT_COMMAND;
            } else if (state == ACCEPT_COMMAND) {
                LOG.info(String.format("Command Socket received: \"%s\"", theInput));
                if (theInput.equalsIgnoreCase("RM")) {
                    theOutput = okWithVersion;
                    currentCommand = Command.READ_MICROPHONE;
                    state = ACCEPT_COMMAND;
                }
                else if (theInput.equalsIgnoreCase("RAS")) {
                    theOutput = okWithVersion;
                    currentCommand = Command.READ_AUDIO_SOCKET;
                    state = ACCEPT_COMMAND;
                }
                else if (theInput.equalsIgnoreCase("OS")) {
                    theOutput = okWithVersion;
                    currentCommand = Command.OUTPUT_TO_SPEAKER;
                    state = ACCEPT_COMMAND;
                }
                else if (theInput.equalsIgnoreCase("OAS")) {
                    theOutput = okWithVersion;
                    currentCommand = Command.OUTPUT_TO_AUDIO_SOCKET;
                    state = ACCEPT_COMMAND;
                }
                else if (theInput.equalsIgnoreCase("finishedPlaying")) {
                    theOutput = okWithVersion;
                    currentCommand = Command.FINISHED_PLAYING;
                    state = ACCEPT_COMMAND;
                }
                else if (theInput.equalsIgnoreCase("EXIT")) {
                    theOutput = DONE;
                    currentCommand = Command.EXIT;
                    state = WAITING;
                } else {
                    theOutput = UNKNOWN_CMD;
                    currentCommand = Command.NONE;
                    state = ACCEPT_COMMAND;
                }
            }
            return theOutput;
        }
    }

    public SocketCommandProcessor(String name, Client client, int portNumber) {
        super(name);
        this.setDaemon(true);
        this.client = client;
        this.portNumber = portNumber;
        try {
            serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName(null));
        } catch (IOException e) {
            LOG.error("Error trying to create command socket on port " + this.portNumber, e);
        }
    }

    public SocketCommandProcessor(Client client, int portNumber) {
        this("Command Socket", client, portNumber);
    }

    public void outputToSocket(String text) {
        if (null != socketOutputWriter) {
            socketOutputWriter.println(text);
        }
    }

    public void sendServerConnected() {
        outputToSocket(SocketProtocol.SERVER_CONNECTED);
    }

    public void sendServerConnecting() {
        outputToSocket(SocketProtocol.SERVER_CONNECTING);
    }

    public void sendServerConnectionReady() {
        outputToSocket(SocketProtocol.SERVER_CONNECTION_READY);
    }

    public void sendServerNotConnected() {
        outputToSocket(SocketProtocol.SERVER_NOT_CONNECTED);
    }

    public void sendMicrophoneClose() {
        outputToSocket(SocketProtocol.MIC_CLOSE);
    }

    public void sendMicrophoneOpen() {
        outputToSocket(SocketProtocol.MIC_OPEN);
    }

    public void sendWakeupTriggerIsAllowed(boolean allowed) {
        if (allowed) {
            outputToSocket(SocketProtocol.MIC_TRIGGER_IS_ALLOWED);
        }
        else {
            outputToSocket(SocketProtocol.MIC_TRIGGER_NOT_ALLOWED);
        }
    }

    public void sendPlaybackResume() {
        outputToSocket(SocketProtocol.PLAYBACK_RESUME);
    }

    public void sendPlaybackStop() {
        outputToSocket(SocketProtocol.PLAYBACK_STOP);
    }

    public void sendVolumeDecrease() {
        outputToSocket(SocketProtocol.VOLUME_DECREASE);
    }

    public void sendVolumeIncrease() {
        outputToSocket(SocketProtocol.VOLUME_INCREASE);
    }

    public void sendVolumeMute() {
        outputToSocket(SocketProtocol.VOLUME_MUTE);
    }

    public void sendVolumeUnmute() {
        outputToSocket(SocketProtocol.VOLUME_UNMUTE);
    }

    @Override
    public void run() {
        while (null != serverSocket) {
            this.socketOutputWriter = null;
            LOG.info("Command socket listening on port: " + portNumber);
            try ( 
                    Socket clientSocket = serverSocket.accept();
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    ){

                LOG.info("Command socket accepted connection");
                this.socketOutputWriter = out;

                String inputLine, outputLine;

                // Initiate conversation with client
                SocketProtocol protocol = new SocketProtocol();
                outputLine = protocol.processInput(null);
                outputToSocket(outputLine);

                // Send current status
                client.sendWakeupTriggerAllowedStatus();
                sleep(800); // Small delay to help controllers
                client.sendConnectionStatus();

                boolean readFromController = true;

                while (readFromController && (inputLine = in.readLine()) != null) {
                    boolean accepted = false;
                    outputLine = protocol.processInput(inputLine);
                    SocketProtocol.Command command = protocol.getCommand();
                    switch (command) {
                    case EXIT:
                        LOG.debug("External controller exited");
                        readFromController = false;
                        break;
                    case READ_MICROPHONE:
                        accepted = client.onWakeupTriggerReceived(AudioInput.InputSource.MICROPHONE);
                        break;
                    case READ_AUDIO_SOCKET:
                        accepted = client.onWakeupTriggerReceived(AudioInput.InputSource.AUDIO_SOCKET);
                        break;
                    case OUTPUT_TO_SPEAKER:
                        LOG.debug("Set output to the speaker");
                        accepted = client.setOutputToSpeaker();
                        break;
                    case OUTPUT_TO_AUDIO_SOCKET:
                        LOG.debug("Set output to the audio socket");
                        accepted = client.setOutputToAudioSocket();
                        break;
                    case FINISHED_PLAYING:
                        // The controller has finished playing the audio response - allow the client to continue.
                        LOG.debug("Controller finished playing response...");
                        accepted = client.finishedPlayingOutput();
                        break;
                    case NONE:
                        break;
                    }
                    if (!accepted) {
                        LOG.info(String.format("Command requested cannot currently be controlled: CommandType: %s  Command: '%s'", command.toString(), inputLine));
                    }
                    out.println(outputLine);
                    out.flush();
                }

                // Clean up existing socket and accept a new connection
                clientSocket.close();
                // Tell the client to close the audio socket and accept a new connection
                client.closeAndAcceptAudioConnection();

            } catch (IOException e) {
                LOG.error("Error trying to connect or listen to command socket on port " + portNumber, e);
            } catch (InterruptedException e) {
                LOG.warn("Command Socket interrupted...");
                Thread.currentThread().interrupt();
            }
        }
    }

}
