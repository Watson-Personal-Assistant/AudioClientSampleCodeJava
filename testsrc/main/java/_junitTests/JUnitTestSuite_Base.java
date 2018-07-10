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

package _junitTests;

import org.junit.runners.Suite;

import main.DriverTest;
import wa.audio.LocalAudioTest;
import wa.status.StatusIndicatorTest;

/**
 * JUnit test suite base class to define the annotation that defines the test classes to consider.
 * Subclasses can include/exclude tests from the classes based on categories.
 * 
 * The @Suite.SuiteClasses defines the test classes that will be considered to be run.
 * 
 * The preferred format of the class name for JUnit tests is: <<ClassName>>Test
 * The preferred format of manually run tests (using a `main` method) is: Test<<ClassName>>
 */
@Suite.SuiteClasses({
	DriverTest.class,
	LocalAudioTest.class,
	StatusIndicatorTest.class,
})
public abstract class JUnitTestSuite_Base {
 /* Empty class - used as placeholder for the test annotations */
}
