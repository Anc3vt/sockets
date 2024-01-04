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

    static USyncServer newInstance(String bindHost, int listeningPort, int bufferSize, int interval, boolean blocking) {
        return new USyncServerImpl(bindHost, listeningPort, bufferSize, interval, blocking);
    }
}
