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

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;

/**
 * JUnit test suite for running all of the 'General' (non-LongRunning) JUnit tests.
 * 
 * The @Suite.SuiteClasses defines the test classes that will be run.
 * JUnit test classes that contain 'General' (typical/normal) tests should 
 * be added to this annotation block.
 * 
 * The preferred format of the class name for JUnit tests is: <<ClassName>>Test
 * The preferred format of manually run tests (using a `main` method) is: Test<<ClassName>>
 */
@RunWith(Categories.class)
public class JUnitTestSuite_All extends JUnitTestSuite_Base {
	// the class remains empty, it is used only as a holder for the class annotations above
}
