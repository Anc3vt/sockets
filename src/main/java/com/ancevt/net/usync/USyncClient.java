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

    static USyncClient newInstance(String host, int port, int bufferSize, int interval, boolean blocking) {
        return new USyncClientImpl(host, port, bufferSize, interval, blocking);
    }
}
