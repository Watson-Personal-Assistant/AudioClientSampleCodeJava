package wa.status;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

public class StatusConsole implements StatusIndicator {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(StatusConsole.class);

    private volatile boolean statusOn = false;
    private volatile boolean blink = false;
    private Thread blinkThread = null;

    public StatusConsole() {
    }

    private void cancelBlink() {
        blink = false;
        statusOn = false;
        if (null != blinkThread) {
            blinkThread.interrupt();
            blinkThread = null;
        }
    }
    
    private Thread createBlinkThread(long onOffTime) {
        Thread t = new Thread(() -> {
          try {
            while (blink) {
              char statusChar = (toggle() ? 'O' : 'o');
              System.out.print(statusChar);
              Thread.sleep(onOffTime);
            }
          } catch (InterruptedException ignored) {
          } finally {
              cancelBlink();
          }
        });
        t.setName("Status-blink-thread");
        return t;
      }

    private boolean toggle() {
        statusOn = !statusOn;
        return statusOn;
    }

    /**
     * Turn on the indicator.
     */
    @Override
    public void on() {
        cancelBlink();
        this.statusOn = true;
        LOG.info("\nStatus indicator: on");
    }

    /**
     * Turn off the indicator.  This also cancels blinking.
     */
    @Override
    public void off() {
        cancelBlink();
        this.statusOn = false;
        LOG.info("\nStatus indicator: off");
    }

    /**
     * Blink the indicator at the given rate.
     */
    @Override
    public void blink(long onOffTime) {
        LOG.info("Status indicator: blink on/off: " + onOffTime);
        cancelBlink();
        blinkThread = createBlinkThread(onOffTime);
        blinkThread.start();
    }
}
