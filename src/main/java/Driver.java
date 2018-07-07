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
 *  Uses the main.Driver class to start and monitor the client.
 *  
 *  This provides a (default package) `main` method that can be used to start the client without requiring a package identifier.
 */
public class Driver {

	public static void main(String args[]) {
		main.Driver mainDriver = new main.Driver();
		mainDriver.runClient();
    }
}
