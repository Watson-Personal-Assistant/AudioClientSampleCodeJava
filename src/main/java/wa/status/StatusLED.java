package wa.status;
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

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

/** 
 * Uses the Raspberry Pi GPIO pin to display status.
 * 
 * The GPIO pins used are identified by the `WiringPi/Pi4J` convention.
 * It is important to understand that there are different conventions for numbering.
 * 
 */
public class StatusLED implements StatusIndicator {

    private boolean blinking = false;
    private Thread blinkThread = null;
    private static StatusLED led;
    private static boolean onState = false;
    private static final boolean dualLED = false;

    private final GpioController gpio = GpioFactory.getInstance();
    private final GpioPinDigitalOutput blueLedControl = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00);  // RPi physical GPIO Pin 11
    private final GpioPinDigitalOutput greenLedControl = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02); // RPi physical GPIO Pin 13
    private final GpioPinDigitalOutput redLedControl = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03);   // RPi physical GPIO Pin 15

    public StatusLED() {
        blueLedControl.setShutdownOptions(true, PinState.LOW);
        blueLedControl.low();
        if (dualLED) {
            redLedControl.setShutdownOptions(true, PinState.LOW);
            redLedControl.low();
        }
        onState = false;
    }

    public static StatusLED getInstance() {
        if (led == null) {
            led = new StatusLED();
        }
        return led;
    }

    private void cancelBlink() {
        blinking = false;
        if (null != blinkThread) {
            blinkThread.interrupt();
            blinkThread = null;
        }
        if (blueLedControl.isHigh()) {
            blueLedControl.low();
        }
        if (dualLED) {
            redLedControl.low();
        }
        onState = false;
    }

    private boolean toggle() {
        onState = !onState;
        if (onState) {
            blueLedControl.high();
            if (dualLED) {
                redLedControl.high();
            }
        }
        else {
            blueLedControl.low();
            if (dualLED) {
                redLedControl.low();
            }
        }
        return onState;
    }

    
	/* (non-Javadoc)
	 * @see wa.status.StatusIndicator#on()
	 */
    @Override
    synchronized public void on() {
        cancelBlink();
        blueLedControl.high();
        if (dualLED) {
            redLedControl.high();
        }
        onState = true;
    }

	@Override
	public boolean isOn() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see wa.status.StatusIndicator#off()
	 */
    @Override
    synchronized public void off() {
        cancelBlink();
    }

	/* (non-Javadoc)
	 * @see wa.status.StatusIndicator#isOff()
	 */
	@Override
	public boolean isOff() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see wa.status.StatusIndicator#blink(long)
	 */
    @Override
    public void blink(long onOffTime) {
        blinkThread(onOffTime).start();
    }

	/* (non-Javadoc)
	 * @see wa.status.StatusIndicator#isBlinking()
	 */
	@Override
	public boolean isBlinking() {
		// TODO Auto-generated method stub
		return false;
	}


    private Thread blinkThread(long onOffTime) {
        return new Thread(() -> {
            try {
                while (blinking) {
                    toggle();
                    Thread.sleep(onOffTime);
                }
            } catch (InterruptedException ignored) {
            } finally {
                off();
            }
        });
    }

    /**
     * Local testing main method.
     * This turns the LED on for 1 second, then blinks the at a 1/2 second rate for 1.5 seconds, then turns it off. 
     */
    public static void main(String[] args) throws InterruptedException {
        StatusLED led = new StatusLED();

        led.on();
        Thread.sleep(1000);
        led.blink(500);
        Thread.sleep(1500);
        led.off();
    }
}
