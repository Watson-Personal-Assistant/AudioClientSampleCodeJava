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
package main;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provide version information about the client.
 */
public class Version {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(Version.class);

	/** Single instance */
	private static Version instance;
	
	/** The version of the client */
	private String version = null;
	
	/**
	 * Get singleton instance.
	 */
	public static Version getInstance() {
		return instance;
	}
	
	/**
	 * Get the version value.
	 */
	public String getVersion() {
		return version;
	}
	
	static {
		// Create our singleton instance.
		instance = new Version();
	}
	
	private Version() {
		initVersion();
	}
	
	/**
	 * Try to read the version from the JAR manifest. 
	 * If not being run from a JAR then get the version 
	 * from the manifest included in the source.
	 */
	private void initVersion() {
		try {
		    Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
				while (resources.hasMoreElements()) {
					InputStream is = null;
				    try {
				      Manifest manifest = new Manifest(resources.nextElement().openStream());
				      // Check that this is our manifest and get the next one if not.
				      Attributes mainAttributes = manifest.getMainAttributes();
				      // Check the main class for "Driver" and the implementation title
				      String mainClass = mainAttributes.getValue("Main-Class");
				      String implTitle = mainAttributes.getValue("Implementation-Title");
				      if (!"Driver".equals(mainClass) || !"was-audio-client".equalsIgnoreCase(implTitle)) {
				    	  // not a manifest for our implementation - keep looking
				    	  continue;
				      }
				      // Save the version.
				      String implVersion = mainAttributes.getValue("Implementation-Version");
				      this.version = implVersion;
				      break;
				    } catch (IOException e) {
				      LOG.error("Error reading the Manifest from stream to retrieve version information: " + e, e);
				    } finally {
				    	if (null != is) {
				    		is.close();
				    		is = null;
				    	}
				    }
				}
	    } catch (IOException e) {
		      LOG.error("Unable to load META-INF/MANIFEST.MF from class resources: " + e, e);
	    }
		// If we weren't able to find one it is most likely that it is being run from the IDE, 
		// not from a JAR.
		if (null == version) {
			LOG.warn("Could not read version from MANIFEST.MF for our 'Driver' class. Setting default development version");
			version = "1.0.DevIDE";
		}
	}
}
