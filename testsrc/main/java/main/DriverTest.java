package main;
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

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
/* JUnit imports */
import org.junit.Test;

import main.Driver;

/**
 * JUnit test for the Driver (main) class.
 *
 */

public class DriverTest {

    /**
     * Test that the Driver class can read a properties file and create a properties object with the expected values.
     * These tests basically assure that the Driver class can read the correct properties file and process it 
     * (the values returned are controlled by the Properties class)
     */
    @Test
    public void testReadProps() {
		System.out.println("DriverTest.testReadProps()");
    	Driver driver = new Driver();
        // Get the path to the test properties file
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("DriverTest.testReadProps.properties").getFile());
        String testPropsPath = file.getAbsolutePath();
        //System.out.println(testPropsPath);
        Properties props = driver.readProps(testPropsPath);
        assertNotNull("Properties object should not be null", props);
        // Test 'host'
        String value = props.getProperty("host");
        // value s/b "myhost.mydomain.com"
        assertEquals("'host' not as expected", "myhost.mydomain.com", value);
        // Test for blank 'IAMAPIKey' property
        value = props.getProperty("IAMAPIKey");
        assertTrue("'IAMAPIKey' property should be blank", StringUtils.isBlank(value));
    }
}
