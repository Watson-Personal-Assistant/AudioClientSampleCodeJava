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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
//Import log4j classes.
import org.apache.logging.log4j.Logger;

import wa.client.Client;

import org.apache.logging.log4j.LogManager;


/**
 *  Main driver of the program. Create client thread and start.
 *  
 *  This reads the configure.properties file and creates a Properties object.
 *  It then creates a Client object with the Properties object.
 *  
 *  This does not exit unless:
 *  <bl>
 *   <li> It cannot open/read the configure.properties file (Exit=1)
 *   <li> It cannot create and initialize the Client (Exit=1)
 *   <li> There is a 'catastrophic' error from the client (Exit=3)
 *   <li> The process is externally terminated
 *   </bl>
 *   
 */
public class Driver {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(Driver.class);

    /**
     * Open and read configure.properties.  Use the properties to create a Client.
     * Once created, start the client and let it run.
     * 
     * Only a failure to read the properties, create and start a client, or a fatal client error, will cause this method to exit.
     * 
     * @param args - None at this time
     */
    public static void main(String args[]) {
        Thread.currentThread().setName("Driver (main)");
        
        LOG.info("Watson Driver (main) starting...");
        Client client = null;
        Properties properties = readProps();
        if (null == properties) {
            System.exit(1);
        }

        // Create the client
        try {
            client = new Client(properties);
        }
        catch (Throwable t) {
            // Any error from creating the client causes this to exit(2)
            t.printStackTrace();
            System.exit(2);
        }

        // The properties are read, the client is created - start it and wait for a fatal error!
        Thread clientThread = new Thread(client, "Client");
        LOG.info("Starting client...");
        clientThread.start();
        try {
            LOG.info("Waiting on client...");
            while (clientThread.isAlive()) {
                try {
                    clientThread.join();
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted!", e);
                }
            }
            LOG.info("Client is done!");
        } catch (RuntimeException re) {
            LOG.error("Exiting due to thrown RuntimeException", re);
        } catch (Error err) {
            LOG.error("Exiting due to thrown Error",  err);
        }
    }

    /**
     * Reads the configure.properties file
     * 
     * @return Properties or null if any error occurred.
     */
    private static Properties readProps() {
        FileInputStream file = null;
        Properties props = null;

        try {
            Properties p = new Properties();
            file = new FileInputStream("./config/configure.properties");
            p.load(file);
            props = p;
        } catch (Throwable t) {
            // Just return a null properties...
        } finally {
            try {
            	if (null != file) {
            		file.close();
            	}
            } catch (IOException e) { }
        }
        return props;
    }
}