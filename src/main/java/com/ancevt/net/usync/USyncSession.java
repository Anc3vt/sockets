package com.ancevt.net.usync;

import java.net.SocketAddress;

public interface USyncSession {

    int getId();

    int getBufferSize();

    long getBytesReceived();

    long getBytesSent();

    void send(byte[] bytes);

    void sendMessage(byte[] bytes);

    void sendObject(Object object);

    void disconnect();

    void dispose();

    boolean isDisposed();
    SocketAddress getSocketAddress();

    USyncServer getServer();
}
