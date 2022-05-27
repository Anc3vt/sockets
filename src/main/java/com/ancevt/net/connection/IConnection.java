/*
 *     Copyright 2015-2022 Ancevt (me@ancevt.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "LICENSE");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ancevt.net.connection;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface IConnection {

    void connect(String host, int port);

    void asyncConnect(String host, int port);

    boolean asyncConnectAndAwait(String host, int port);

    boolean asyncConnectAndAwait(String host, int port, long time, TimeUnit timeUnit);

    int getId();

    String getHost();

    int getPort();

    String getRemoteAddress();

    int getRemotePort();

    void readLoop();

    boolean isOpen();

    void close();

    default void closeIfOpen() {
        if(isOpen()) close();
    }

    void addConnectionListener(ConnectionListener listener);

    void removeConnectionListener(ConnectionListener listener);

    void send(byte[] bytes);

    long bytesSent();

    long bytesLoaded();

    static String getIpFromAddress(@NotNull String address) {
        if (address.contains("/")) {
            address = address.replaceAll("^.*/", "");
        }

        if(address.contains(":")) {
            String[] split = address.split(":");
            return split[0];
        } else {
            return address;
        }

    }
}
