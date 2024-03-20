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
package com.ancevt.net.usync;

import java.net.SocketAddress;

public interface USyncClient {

    int getBufferSize();

    long getBytesReceived();

    long getBytesSent();

    int getSessionId();

    SocketAddress getSocketAddress();

    void addUSyncClientListener(Listener listener);

    void removeUSyncClientListener(Listener listener);

    void removeAllUSyncClientListeners();

    void send(byte[] bytes);

    void sendMessage(byte[] bytes);

    void sendObject(Object object);

    void requestSession();

    void requestDisconnect();

    void setSendLoopActive(boolean b);

    boolean isSendLoopActive();

    void setReceiveLoopActive(boolean b);

    boolean isReceiveLoopActive();

    void setInterval(int interval);

    int getInterval();

    void dispose();

    boolean isDisposed();

    void start();

    void stop();

    interface Listener {
        void uSyncClientSessionStarted(int sessionId);

        void uSyncClientSessionBytesReceived(byte[] bytes);

        void uSyncClientMessageReceived(byte[] bytes);

        void uSyncClientObjectReceived(Object object);

        void uSyncClientNormalDisconnected();
    }

    class ListenerAdapter implements Listener {

        @Override
        public void uSyncClientSessionStarted(int sessionId) {

        }

        @Override
        public void uSyncClientSessionBytesReceived(byte[] bytes) {

        }

        @Override
        public void uSyncClientMessageReceived(byte[] bytes) {

        }

        @Override
        public void uSyncClientObjectReceived(Object object) {

        }

        @Override
        public void uSyncClientNormalDisconnected() {

        }
    }

    static USyncClient newInstance(String host, int port, int bufferSize, int interval, boolean blocking, int persistMessageEach) {
        return new USyncClientImpl(host, port, bufferSize, interval, blocking, persistMessageEach);
    }
}
