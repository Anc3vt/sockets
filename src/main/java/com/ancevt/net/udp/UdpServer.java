package com.ancevt.net.udp;

public interface UdpServer {

    void receive();

    void setSendLoopActive(boolean b);

    boolean isSendLoopActive();

    void setReceiveLoopActive(boolean b);

    boolean isReceiveLoopActive();

    void setInterval(int interval);

    int getInterval();

    void dispose();

    void onBytesReceived(Session session, byte[] bytes);

    void onMessageReceived(Session session, byte[] data);

    void onObjectReceived(Session session, Object object);

    void onSessionDisconnect(Session session);
}
