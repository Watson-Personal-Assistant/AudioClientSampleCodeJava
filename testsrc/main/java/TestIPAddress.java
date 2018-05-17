/**
 * Copyright 2016-2018 IBM Corporation. All Rights Reserved.
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

import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;

import wa.network.LocalNetworkInterface;

public class TestIPAddress {

	public static void main(String[] args) {
		try {
			List<InetAddress> addresses;
			addresses = LocalNetworkInterface.getAddresses();

	      int i = 0;
	      for (InetAddress address : addresses) {
	    	  i++;
		      boolean isSSHReachable = LocalNetworkInterface.isReachableViaSSH(address, 100);
		      System.out.println(String.format("Address %d: %s  Hostname: '%s'  Is SSH reachable: %b", i, address.getHostAddress(), address.getCanonicalHostName(), isSSHReachable));
	      }
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

}
