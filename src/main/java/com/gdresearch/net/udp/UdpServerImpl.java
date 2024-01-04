package com.gdresearch.net.udp;

import com.ancevt.commons.concurrent.Async;
import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.ToString;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class UdpServerImpl implements UdpServer {

    private final int bufferSize;
    private final DatagramChannel datagramChannel;
    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private int interval;
    private Thread sendLoopThread;
    private Thread receiveLoopThread;

    public UdpServerImpl(String bindHost, int bindPort, int bufferSize, int interval, boolean blocking) {
        this.bufferSize = bufferSize;
        this.interval = interval;

        try {
            datagramChannel = UdpUtils.bindChannel(bindHost == null ? null : new InetSocketAddress(bindHost, bindPort));
            datagramChannel.configureBlocking(blocking);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getInterval() {
        return interval;
    }

    @Override
    public void setInterval(int interval) {
        this.interval = interval;
    }

    @Override
    public void receive() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        try {
            SocketAddress sourceSocketAddress = datagramChannel.receive(byteBuffer);
            if (sourceSocketAddress != null) {
                actualAtomicReceiveBytes(sourceSocketAddress, byteBuffer.array());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setSendLoopActive(boolean b) {
        if ((b && sendLoopThread != null) || (!b && sendLoopThread == null)) return; // ignore if same value set

        if (b) {
            sendLoopThread = new Thread(
                () -> {
                    while (sendLoopThread != null) {


                        for (Session s : sessions.values()) {
                            SocketAddress socketAddress = s.getSocketAddress();
                            if (socketAddress != null) {
                                try {
                                    ByteBuffer byteBufferToSend = s.getByteBufferToSend();

                                    if (byteBufferToSend != null) {
                                        datagramChannel.send(byteBufferToSend, socketAddress);
                                    }

                                    Collection<SendingMessage> sendingMessages = s.getSendingMessages().values();

                                    for (SendingMessage message : sendingMessages) {
                                        s.atomicSendBytes(s.getSocketAddress(), message.getChunkBytes());
                                    }

                                    Thread.sleep(interval);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                } catch (InterruptedException e) {

                                }
                            }
                        }


                    }
                },
                "udpServerSendLoopThread" + hashCode()
            );
            sendLoopThread.start();
        } else {
            if (sendLoopThread.isAlive()) {
                sendLoopThread.interrupt();
            }
            sendLoopThread = null;
        }
    }

    @Override
    public boolean isSendLoopActive() {
        return sendLoopThread != null;
    }

    @Override
    public void setReceiveLoopActive(boolean b) {
        if ((b && receiveLoopThread != null) || (!b && receiveLoopThread == null)) return; // ignore if same value set

        if (b) {
            receiveLoopThread = new Thread(
                () -> {
                    while (receiveLoopThread != null) {
                        receive();

                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                        }
                    }
                }
                , "udpServerReceiveLoopThread"
            );
            receiveLoopThread.start();
        } else {
            if (receiveLoopThread.isAlive()) {
                receiveLoopThread.interrupt();
            }
            receiveLoopThread = null;
        }
    }

    @Override
    public boolean isReceiveLoopActive() {
        return receiveLoopThread != null;
    }

    @Override
    public void dispose() {
        if (sendLoopThread != null && sendLoopThread.isAlive()) {
            sendLoopThread.interrupt();
        }
        sendLoopThread = null;

        if (receiveLoopThread != null && receiveLoopThread.isAlive()) {
            receiveLoopThread.interrupt();
        }
        receiveLoopThread = null;

        try {
            datagramChannel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void actualAtomicReceiveBytes(SocketAddress sourceSocketAddress, byte[] bytes) {
        ByteInput in = ByteInput.newInstance(bytes);

        int type = in.readUnsignedByte();

        switch (type) {
            case Type.REQUEST_SESSION_ID: {
                if (!isSourceSocketAddressAlreadyExists(sourceSocketAddress)) {
                    Session session = new Session(this, datagramChannel, sourceSocketAddress, bufferSize);
                    sessions.put(session.getId(), session);
                    session.atomicSendBytes(
                        sourceSocketAddress,
                        ByteOutput.newInstance(2)
                            .writeByte(Type.SESSION_ID)
                            .writeByte(session.getId())
                            .toArray()
                    );
                }
                break;
            }
            case Type.REQUEST_DISCONNECT: {
                int sessionId = in.readUnsignedByte();
                Session session = sessions.get(sessionId);
                if (session != null) {
                    session.send(
                        ByteOutput.newInstance()
                            .writeByte(Type.DISCONNECTED)
                            .toArray()
                    );

                    Async.runLater(1, TimeUnit.SECONDS, () -> {
                        session.dispose();
                        sessions.remove(sessionId);
                        onSessionDisconnect(session);
                    });
                }
                break;
            }
            case Type.ACKNOWLEDGE:
            case Type.CHUNK:
            case Type.START_CHUNKS:
            case Type.PLAIN: {
                int sessionId = in.readUnsignedByte();
                Session session = sessions.get(sessionId);
                if (session != null) {
                    session.receiveBytes(sourceSocketAddress, type, in);
                }
                break;
            }
        }
    }

    private boolean isSourceSocketAddressAlreadyExists(SocketAddress socketAddress) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        for (Session s : sessions.values()) {
            InetSocketAddress sessionInetSocketAddress = (InetSocketAddress) s.getSocketAddress();
            if (Objects.equals(inetSocketAddress.getHostName(), sessionInetSocketAddress.getHostName()) &&
                inetSocketAddress.getPort() == sessionInetSocketAddress.getPort()) return true;
        }

        return false;
    }

    @Override
    public void onBytesReceived(Session session, byte[] bytes) {

    }

    @Override
    public void onMessageReceived(Session session, byte[] data) {

    }

    @Override
    public void onObjectReceived(Session session, Object object) {

    }

    @Override
    public void onSessionDisconnect(Session session) {

    }

    static UdpServer server;
    static UdpClient client;

    @SneakyThrows
    public static void main(String[] args) {
        int bufferSize = 128;
        int interval = 5;

        server = new UdpServerImpl("0.0.0.0", 7777, bufferSize, interval, true) {
            @Override
            public void onBytesReceived(Session session, byte[] bytes) {
                System.out.println("SERVER RECEIVED: " + Arrays.toString(bytes));
            }

            @Override
            public void onMessageReceived(Session session, byte[] data) {
                System.out.println("SERVER onMessageReceived " + new String(data, StandardCharsets.UTF_8));
            }

            @Override
            public void onObjectReceived(Session session, Object object) {
                System.out.println("SERVER RECEIVED " + object.toString());

                session.sendObject(
                    MyDto.builder()
                        .id(149)
                        .name("response")
                        .build()
                );
            }

            @Override
            public void onSessionDisconnect(Session session) {
                System.out.println("SERVER DISCONNECT " + session);
            }
        };
        server.setSendLoopActive(true);
        server.setReceiveLoopActive(true);

        System.out.println("start");

        client = new UdpClientImpl("localhost", 7777, bufferSize, interval, false) {
            @Override
            public void onSessionCreated(int sessionId) {
                System.out.println("session created");
                //send(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
                sendMessage("1Hello world 2Hello world 3Hello world4".getBytes(StandardCharsets.UTF_8));

                sendObject(
                    MyDto.builder()
                        .id(148)
                        .name("request")
                        .build()
                );
            }

            @Override
            public void onBytesReceived(byte[] bytes) {
                System.out.println("CLIENT RECEIVED: " + Arrays.toString(bytes));
            }

            @Override
            public void onObjectReceived(Object object) {
                System.out.println("CLIENT RECEIVED " + object.toString());

                MyDto responseDto = (MyDto) object;
                if (responseDto.id == 149) {
                    requestDisconnect();
                }
            }

            @Override
            public void onDisconnect() {
                System.out.println("CLIENT DISCONNECT");
                Async.runLater(2, TimeUnit.SECONDS, () -> System.exit(0));
            }
        };
        client.setSendLoopActive(true);
        client.setReceiveLoopActive(true);
        client.requestSession();
    }

    @ToString
    @Builder
    @AllArgsConstructor
    private static class MyDto {
        private int id;
        private String name;
    }

}
/*

                |0 |1 |2 |3 |4 |5 |6 |7 |8 |9 |10|
          reqid  0
          sessid 1  SI
          plain  2  SI D..
          chunks 3  SI ID PAYLOADSIZE
          chunk  4  SI ID __POSITION_ D..
          ackge  5  SI ID __POSITION_
          compld 6  SI ID
          reqdsc 7  SI
          discd  8

 */