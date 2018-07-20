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

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common logging identifiers.
 */
public class CommonLogging {
    
    private static final String BLANK = "";
    private static final char TAB = '\t';
    
    static Logger LOG_PERFORMANCE = LogManager.getLogger("GLOBAL.Performance");

    /** Communication loggers - when sending */
    public static Logger LOG_SERVER_COMM_SEND = LogManager.getLogger("GLOBAL.Server.Communication.Send");
	/** Communication loggers - when receiving */
    public static Logger LOG_SERVER_COMM_RECEIVE = LogManager.getLogger("GLOBAL.Server.Communication.Receive");
    
    static {
        LOG_PERFORMANCE.info("<<STARTED>>");
    }
    
    /**
     * Log a performance message in the correct format given the input parameters.
     * (no validation is done)
     * 
     * The format of each logged line is:
     * <br/>
     * (tab separated fields):
     * <bl>
     *  <li> Identifier </li>
     *  <li> Comment </li>
     *  <li> Perf Numeric Value Name1 </li>
     *  <li> Perf Numeric Value Value1 (numeric) </li>
     *  <li> Perf Numeric Value Name2 </li>
     *  <li> Perf Numeric Value Value2 (numeric) </li>
     *  <li> Perf Numeric Value Name3 </li>
     *  <li> Perf Numeric Value Value3 (numeric) </li>
     *  <li> etc... </li>
     *  <li> Perf Info Value Name 1 </li>
     *  <li> Perf Info Value Value1 </li>
     *  <li> etc... </li>
     * </bl>
     *
     * The Collection.iterator() is used to access the elements. 
     * If the order of the elements in the log file is important a collection based 
     * on a class that guarantees order needs to be used (List, etc.).
     * 
     * @param identifier - Identifier string that will be written to the log after the datetime stamp and 'PERF'
     * @param comment - Comment that will be written to the log after the identifier
     * @param elements - A collection of `PerfNumericNameValue` elements that will be written to the log (tab separated) [can be null]
     * @param infoElements - A collection of `PerfInfoNameValue` elements that will be written to the log (tab separated) [can be null]
     **/
    public static void logPerformanceElements(String identifier, String comment, Collection<PerfNumericNameValue> valueElements, Collection<PerfInfoNameValue> infoElements) {
        if (isPerfomanceLogEnabled()) {
        	String logIdentifier = (StringUtils.isEmpty(identifier) ? BLANK : identifier);
        	String logComment = (StringUtils.isBlank(comment) ? BLANK : comment);
        	
        	// Create the log message
        	StringBuilder sb = new StringBuilder();
        	//  Identifier and Comment
        	sb.append(logIdentifier).append(TAB);
        	sb.append(logComment);
        	//  Performance value elements
        	if (null != valueElements)
        	for (PerfNumericNameValue element : valueElements) {
        	    sb.append(TAB);
        	    String value = (element.getValue() == null ? BLANK : element.getValue().toString()); // TODO: Possibly improve formatting based on type?
        	    sb.append(element.getName()).append(TAB).append(value);
        	}
            if (null != infoElements) {
                for (PerfInfoNameValue element : infoElements) {
                    sb.append(TAB);
                    String value = (element.getValue() == null ? BLANK : element.getValue());
                    sb.append(element.getName()).append(TAB).append(value);
                }
            }
  	
        	// Log the line...
        	LOG_PERFORMANCE.debug(sb.toString());
        }
    }
    
    /**
     * Is the performance logging enabled.
     * 
     * @return - true if enabled
     */
    public static boolean isPerfomanceLogEnabled() {
    	return LOG_PERFORMANCE.isDebugEnabled();
    }
}
