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

import java.util.Map;

public interface USyncServer {

    int getBufferSize();

    long getBytesReceived();

    long getBytesSent();

    Map<Integer, USyncSession> getSessions();

    void addUSyncServerListener(Listener listener);

    void removeUSyncServerListener(Listener listener);

    void removeAllUSyncServerListeners();

    void receive();

    void setSendLoopActive(boolean b);

    boolean isSendLoopActive();

    void setReceiveLoopActive(boolean b);

    boolean isReceiveLoopActive();

    void setInterval(int interval);

    int getInterval();

    void disconnectAll();

    void start();

    void stop();

    void dispose();

    interface Listener {

        void uSyncServerSessionStart(USyncSession session);

        void uSyncServerBytesReceived(USyncSession session, byte[] bytes);

        void uSyncServerMessageReceived(USyncSession session, byte[] bytes);

        void uSyncServerObjectReceived(USyncSession session, Object object);

        void uSyncServerSessionDisconnect(USyncSession session);

    }

    class ListenerAdapter implements Listener {
        @Override
        public void uSyncServerSessionStart(USyncSession session) {

        }

        @Override
        public void uSyncServerBytesReceived(USyncSession session, byte[] bytes) {

        }

        @Override
        public void uSyncServerMessageReceived(USyncSession session, byte[] bytes) {

        }

        @Override
        public void uSyncServerObjectReceived(USyncSession session, Object object) {

        }

        @Override
        public void uSyncServerSessionDisconnect(USyncSession session) {

        }
    }

    static USyncServer newInstance(String bindHost, int listeningPort, int bufferSize, int interval, boolean blocking, int persistMessageDelay) {
        return new USyncServerImpl(bindHost, listeningPort, bufferSize, interval, blocking, persistMessageDelay);
    }
}
