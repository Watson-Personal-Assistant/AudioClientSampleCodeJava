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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import wa.audio.AudioInput;
import wa.client.Client;

/** 
 * Uses the Raspberry Pi GPIO pin to trigger input.
 * 
 * The GPIO pins used are identified by the `WiringPi/Pi4J` convention.
 * It is important to understand that there are different conventions for numbering.
 * 
 */
public class ListenGpio extends Thread {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(ListenGpio.class);

    private Client client;

    private GpioController gpio;

    private GpioPinDigitalInput myButton;

    public ListenGpio(Client client) {
        super("GPIO Trigger");
        this.client = client;
    }

    @Override
    public void run() {
        try {
            Pin inputPin = RaspiPin.GPIO_26;
            LOG.info("Setup GPIO, Pin " + inputPin.getName());
            gpio = GpioFactory.getInstance();
            myButton = gpio.provisionDigitalInputPin(inputPin, PinPullResistance.PULL_UP);
            myButton.setShutdownOptions(true);
            myButton.addListener((GpioPinListenerDigital) (GpioPinDigitalStateChangeEvent event) -> {
                // display pin state on console
                PinState buttonState = event.getState();

                if (buttonState.isHigh()) {
                    LOG.info("Button pressed - wake up");
                    try {
                        boolean accepted = client.onWakeupTriggerReceived(AudioInput.InputSource.MICROPHONE);
                        if (!accepted) {
                            LOG.info("GPIO trigger not accepted");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            while (true) {
                Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            LOG.error("Error in GPIO Trigger: " + ex,  ex);
        } finally {
            if (myButton != null) {
                myButton.removeAllListeners();
            }
            if (gpio != null) {
                gpio.unprovisionPin(myButton);
            }
        }
    }
}
