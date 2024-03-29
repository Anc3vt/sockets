/**
 * Copyright (C) 2023 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ancevt.net.server;

import com.ancevt.net.connection.IConnection;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface IServer {

    void listen(String host, int port);

    void asyncListen(String host, int port);

    boolean asyncListenAndAwait(String host, int port);

    boolean asyncListenAndAwait(String host, int port, long time, TimeUnit timeUnit);

    boolean isListening();

    void close();

    void addServerListener(ServerListener listener);

    void removeServerListener(ServerListener listener);

    Set<IConnection> getConnections();

    void sendToAll(byte[] bytes);
}
