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
/*
 * Portions Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * See DEPENDENCIES.md for notice.
 */ 

import java.net.*;
import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

public class AudioSocket extends Thread {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(AudioSocket.class);

    public static class SocketNotAvailable extends Exception {
        private static final long serialVersionUID = 1L;
    }


    private int portNumber;
    private ServerSocket serverSocket;
    private Socket audioSocket = null;
    private CountDownLatch audioSocketDone;

    public AudioSocket(String name, int portNumber) {
        super(name);
        this.portNumber = portNumber;
        audioSocketDone = new CountDownLatch(1);
        try {
            serverSocket = new ServerSocket(portNumber, 0, InetAddress.getByName(null));
        } catch (IOException e) {
            LOG.error("Error trying to create audio socket on port " + this.portNumber, e);
        }
    }

    public AudioSocket(int portNumber) {
        this("Audio Socket", portNumber);
    }
    
    @Override
    public void run() {
        while (null != serverSocket) {
            LOG.info("Audio socket listening on port: " + portNumber);
            try ( 
                    Socket socket = serverSocket.accept();
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();
                    ) {
                if (null != audioSocket) {
                    audioSocket.close();
                }
                LOG.info("Audio socket accepted connection");
                audioSocket = socket;
                audioSocketDone = new CountDownLatch(1);
                audioSocketDone.await();

                // Clean up existing socket and accept a new connection
                if (null != audioSocket) {
                    audioSocket.close();
                    audioSocket = null;
                }

            } catch (IOException e) {
                LOG.error("Error trying to connect or listen to audio socket on port " + portNumber, e);
            } catch (InterruptedException e) {
                try {
                    audioSocket.close();
                } catch (IOException ignore) {
                }
                audioSocket = null;
            }
        }
    }
    
    public Socket getSocket() throws SocketNotAvailable {
        if (!hasConnection()) {
            throw new SocketNotAvailable();
        }
        return this.audioSocket;
    }
    
    public boolean hasConnection() {
        if (null == audioSocket || audioSocket.isClosed() || !audioSocket.isConnected()) {
            return false;
        }
        return true;
    }
    
    public void closeCurrentSocket() {
        while (audioSocketDone.getCount() > 0) {
            audioSocketDone.countDown();
        }
    }
    
    public int read(byte[] buffer, int offset, int len, int timeout) throws SocketNotAvailable {
        Socket input = getSocket();
        try {
            input.setSoTimeout(timeout);
            InputStream in = input.getInputStream();
            
            int bytesRead = 0;
            int available = in.available();
            bytesRead = in.read(buffer, offset, len);
            if (bytesRead < 1) {
                return 0;
            }
            return bytesRead;
        } catch (SocketTimeoutException toe) {
            // No data read in the timeout period
            return 0;
        } catch (IOException e) {
            System.err.println("Error reading from Audio Socket: " + e);
            e.printStackTrace();
            closeCurrentSocket();
        }
        return 0;
    }
    
    public void clearInput() {
        LOG.debug("Clearing audio socket input");
        try {
            Socket input = getSocket();
            InputStream in = input.getInputStream();
            int bufLen = input.getReceiveBufferSize();
            byte[] buffer = new byte[bufLen];
            int bytesRead = 0;
            while (in.available() > 0 && (bytesRead = in.read(buffer, 0, bufLen)) > 0) {
                System.out.print('#');
            }
            if (bytesRead > 0) {
                LOG.debug(" cleared");
            }
        } catch (IOException e) {
        } catch (SocketNotAvailable e) {
        }
    }
}