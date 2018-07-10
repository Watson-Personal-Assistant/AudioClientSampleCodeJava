package _junitTests;
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

/**
 * Categories of JUnit tests for slow running tests (tests you might not want to run in an automated acceptance test suite.
 */
public class JUnitTestCategories {
	
	public interface GeneralTest {/* Interface marker for typical (fast-running) tests */}
	public interface LongRunningTest {/* Interface marker for slow-running tests */}	
}
