package com.ancevt.net.udp;

public interface UdpClient {

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

    void onSessionCreated(int sessionId);

    void onBytesReceived(byte[] bytes);

    void onMessageReceived(byte[] bytes);

    void onObjectReceived(Object object);

    void onDisconnect();
}
