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

package wa.commonLogging;

/**
 * Holder of a Name-Value for the performance log.
 * 
 * This object is immutable.
 */
public class PerfNumericNameValue {

    private String name;
    private Number value;
    
    /**
     * Constructor takes a name and a value.
     * 
     * @param name - String name of the performance value
     * @param value - Number value
     */
    public PerfNumericNameValue(String name, Number value) {
        this.name = name;
        this.value = value;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the value
     */
    public Number getValue() {
        return value;
    }
}
