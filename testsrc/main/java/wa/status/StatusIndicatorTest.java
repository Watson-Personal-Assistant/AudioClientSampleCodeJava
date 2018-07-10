/**
 * Copyright 2018 IBM Corporation. All Rights Reserved.
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
package wa.status;

import static _junitTests.JUnitTestCategories.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * JUnit tests for the Status Indicator.
 * StatusIndicator is an Interface. This tests the classes that implement the interface.
 */
public class StatusIndicatorTest {

	/* These are used by the tests and initialized with an @Before annotation */
	StatusIndicator consoleStatusIndicator = null;
	StatusIndicator ledStatusIndicator = null;
	
	/**
	 * Initialize each type of status indicator for each of the tests.
	 */
	@Before 
	public void initializeEachTest() {
		consoleStatusIndicator = new StatusConsole();
		//ledStatusIndicator = new StatusLED();
	}
	
	/**
	 * Tests `on` and `isOn` methods.
	 */
	@Test
	public void testStatusConsoleOn() {
		System.out.println("StatusIndicatorTest.testStatusOn()");
		boolean isOn = consoleStatusIndicator.isOn();
		assertThat(isOn, is(false));
		consoleStatusIndicator.on();
		isOn = consoleStatusIndicator.isOn();
		assertThat(isOn, is(true));
	}

	/**
	 * Tests `off` and `isOff` methods.
	 */
	@Test
	public void testStatusConsoleOff() {
		System.out.println("StatusIndicatorTest.testStatusOff()");
		boolean isOff = consoleStatusIndicator.isOff();
		assertThat(isOff, is(true));
		// assumes that 'on' is working (tested above)
		consoleStatusIndicator.on();
		isOff = consoleStatusIndicator.isOff();
		assertThat(isOff, is(false));
		// turn it back off
		consoleStatusIndicator.off();
		isOff = consoleStatusIndicator.isOff();
		assertThat(isOff, is(true));
	}

	/**
	 * Testing the `blink` method is a bit trickier...
	 * @throws Exception 
	 */
	@Category(LongRunningTest.class)
	@Test
	public void testStatusConsoleBlink() throws Exception {
		System.out.println("StatusIndicatorTest.testStatusConsoleBlink()");
		boolean isOn = consoleStatusIndicator.isOn();
		assertThat(isOn, is(false));
		// Set to blink at 100ms
		// Wait 50ms and test
		// Wait 100ms and test
		// Wait 100ms and test
		// Timeline: ('+' is on, '-' is off) 
		// ----------------------------------------------------
		// 0    50    100    150    200    250
		// -     +      ?      -      ?      +
		//
		consoleStatusIndicator.blink(100);
		Thread.sleep(50);
		boolean isBlinking = consoleStatusIndicator.isBlinking();
		assertTrue("Expected to be blinking (50ms)", isBlinking);
		isOn = consoleStatusIndicator.isOn();
		assertTrue("Should be on while blinking at this point (50ms)", isOn);
		Thread.sleep(100);
		isBlinking = consoleStatusIndicator.isBlinking();
		assertTrue("Expected to be blinking (150ms)", isBlinking);
		isOn = consoleStatusIndicator.isOn();
		assertFalse("Should be off while blinking at this point (150ms)", isOn);
		Thread.sleep(100);
		isBlinking = consoleStatusIndicator.isBlinking();
		assertTrue("Expected to be blinking (250ms)", isBlinking);
		isOn = consoleStatusIndicator.isOn();
		assertTrue("Should be on while blinking at this point (250ms)", isOn);
		consoleStatusIndicator.off();
		Thread.sleep(100);
		isBlinking = consoleStatusIndicator.isBlinking();
		assertFalse("Expected not to be blinking after call to `off`", isBlinking);
		isOn = consoleStatusIndicator.isOn();
		assertFalse("Should be off after call to `off`", isOn);
	}

}
