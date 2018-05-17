package wa.network;
/**
 * Copyright 2016-2017 IBM Corporation. All Rights Reserved.
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

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LocalNetworkInterface {
  public static List<InetAddress> getAddresses() throws SocketException {
    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

    List<InetAddress> results = new ArrayList<>();

    while (interfaces.hasMoreElements()) {
      NetworkInterface netInterface = interfaces.nextElement();
      if (netInterface.isLoopback() || netInterface.isVirtual())
        continue;
      Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (address instanceof Inet6Address || address.isLoopbackAddress())
          continue;
        results.add(address);
      }
    }

    return results;
  }

  public static boolean isReachableViaSSH(InetAddress address, int timeout) {
    Socket socket = null;
    try {
      socket = new Socket();
      socket.connect(new InetSocketAddress(address, 22), timeout);
      return true;
    } catch (ConnectException | SocketTimeoutException e) {
      // Do nothing
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException e) {
          // Do nothing
        }
      }
    }
    return false;
  }
}
