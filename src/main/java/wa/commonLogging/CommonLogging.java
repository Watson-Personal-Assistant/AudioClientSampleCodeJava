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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common logging identifiers.
 */
public class CommonLogging {
    /** 
     * Performance logging 
     * <br/>
     * Format is (tab separated fields):
     * <bl>
     *  <li> Class name </li>
     *  <li> Method name </li>
     *  <li> Start time (absolute in ms) </li>
     *  <li> End time (absolute in ms) </li>
     *  <li> Total time (absolute end-start) </li>
     *  <li> Comments (anything that could be important to analyzing the log) </li>
     * </bl>
     *  
     * */
    static Logger LOG_PERFORMANCE = LogManager.getLogger("GLOBAL.Performance");

    /** Communication loggers - when sending */
    public static Logger LOG_SERVER_COMM_SEND = LogManager.getLogger("GLOBAL.Server.Communication.Send");
	/** Communication loggers - when receiving */
    public static Logger LOG_SERVER_COMM_RECEIVE = LogManager.getLogger("GLOBAL.Server.Communication.Receive");
    
    /**
     * Create a log message in the correct format given the input parameters.
     * (no validation is done)
     * 
     * @param className
     * @param methodName
     * @param startTime
     * @param endTime
     * @param operation
     * @param comments
     */
    public static void logPerformance(String className, String methodName, long startTime, long endTime, String operation, String comments) {
    	long totalTime = endTime - startTime;
    	String comment = (StringUtils.isBlank(comments) ? "" : comments.trim());
    	String logMsg = String.format("%s\t%s\t%d\t%d\t%d\t%s\t%s", className, methodName, startTime, endTime, totalTime, operation, comment);
    	LOG_PERFORMANCE.debug(logMsg);
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
