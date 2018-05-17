package wa.trigger;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import wa.audio.AudioInput;
import wa.client.Client;

public class ListenKey extends Thread {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(ListenKey.class);

  private Client client;

  public ListenKey(Client client) {
      super("ListenKey Trigger");
    this.client = client;
  }

  @Override
  public void run() {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    try {
      LOG.info("Use ENTER key to wake up");
      while (in.readLine() != null) {
        if (Thread.interrupted()) break;
        LOG.info("ENTER pressed");
        boolean accepted = client.onWakeupTriggerReceived(AudioInput.InputSource.MICROPHONE);
        if (!accepted) {
            LOG.info("Keyboard trigger not accepted");
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
  }
}
