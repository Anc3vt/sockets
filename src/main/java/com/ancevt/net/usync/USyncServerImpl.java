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

import com.ancevt.commons.concurrent.Async;
import com.ancevt.commons.exception.NotImplementedException;
import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;


@Slf4j
class USyncServerImpl implements USyncServer {

    private final int bufferSize;
    private final DatagramChannel datagramChannel;
    final Map<Integer, USyncSession> sessions = new ConcurrentHashMap<>();

    final Map<Integer, USyncSessionImpl> sessionsCopy = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private int interval;
    private final int persistMessageDelay;
    private Thread sendLoopThread;
    private Thread receiveLoopThread;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public USyncServerImpl(String bindHost, int bindPort, int bufferSize, int interval, boolean blocking, int persistMessageDelay) {
        this.bufferSize = bufferSize;
        this.interval = interval;
        this.persistMessageDelay = persistMessageDelay;

        try {
            datagramChannel = Utils.bindChannel(bindHost == null ? null : new InetSocketAddress(bindHost, bindPort));
            datagramChannel.configureBlocking(blocking);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addUSyncServerListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUSyncServerListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void removeAllUSyncServerListeners() {
        listeners.clear();
    }

    @Override
    public Map<Integer, USyncSession> getSessions() {
        return sessions;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public long getBytesReceived() {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    public long getBytesSent() {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    public void disconnectAll() {
        sessions.forEach((id, s) -> s.disconnect());
    }

    @Override
    public void start() {
        setReceiveLoopActive(true);
        setSendLoopActive(true);
    }

    @Override
    public void stop() {
        setReceiveLoopActive(false);
        setSendLoopActive(false);
    }

    @Override
    public void receive() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        try {
            SocketAddress sourceSocketAddress = datagramChannel.receive(byteBuffer);
            if (sourceSocketAddress != null) {
                byteBuffer.flip();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                if(bytes.length == 0) return;
                atomicReceiveBytes(sourceSocketAddress, bytes);
                //System.out.println("rec " + Arrays.toString(bytes));
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


                        for (USyncSessionImpl s : sessionsCopy.values()) {
                            SocketAddress socketAddress = s.getSocketAddress();
                            if (socketAddress != null) {
                                try {
                                    ByteBuffer byteBufferToSend = s.sender.getPlainByteBufferToSend();

                                    if (byteBufferToSend != null) {
                                        datagramChannel.send(byteBufferToSend, socketAddress);
                                        //System.out.println("send " + Arrays.toString(byteBufferToSend.array()));
                                    }

                                    Collection<SendingMessage> sendingMessages = s.sender.getSendingMessages().values();

                                    for (SendingMessage message : sendingMessages) {
                                        byte[] src = message.getChunkBytes();
                                        byte[] bytesToSend = new byte[bufferSize];
                                        System.arraycopy(src, 0, bytesToSend, 0, src.length);
                                        ByteBuffer bbts = ByteBuffer.wrap(bytesToSend);
                                        datagramChannel.send(bbts, socketAddress);
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
        removeAllUSyncServerListeners();
        stop();
        try {
            datagramChannel.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    private void atomicReceiveBytes(SocketAddress sourceSocketAddress, byte[] bytes) {
        ByteInput in = ByteInput.newInstance(bytes);

        int type = in.readUnsignedByte();

        switch (type) {
            case Type.REQUEST_SESSION_ID: {
                if (!isSourceSocketAddressAlreadyExists(sourceSocketAddress)) {
                    USyncSessionImpl session = new USyncSessionImpl(this, datagramChannel, sourceSocketAddress, bufferSize, persistMessageDelay);
                    sessions.put(session.getId(), session);
                    sessionsCopy.put(session.getId(), session);
                    session.sender.atomicSendBytes(
                        ByteOutput.newInstance(2)
                            .writeByte(Type.SESSION_ID)
                            .writeByte(session.getId())
                            .toArray()
                    );
                    onSessionStart(session);
                }
                break;
            }
            case Type.REQUEST_DISCONNECT: {
                int sessionId = in.readUnsignedByte();
                USyncSession session = sessions.get(sessionId);
                if (session != null) {
                    session.disconnect();
                }
                break;
            }

            default: {
                int sessionId = in.readUnsignedByte();
                USyncSessionImpl session = (USyncSessionImpl) sessions.get(sessionId);
                if (session != null) {
                    session.receiveBytes(sourceSocketAddress, type, in);
                }
                break;
            }
        }
    }

    void disconnectSession(USyncSession session) {
        Async.runLater(1, TimeUnit.SECONDS, () -> {
            session.dispose();
            sessions.remove(session.getId());
            sessionsCopy.remove(session.getId());
            onSessionDisconnect(session);
        });
    }

    private boolean isSourceSocketAddressAlreadyExists(SocketAddress socketAddress) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        for (USyncSession s : sessions.values()) {
            InetSocketAddress sessionInetSocketAddress = (InetSocketAddress) s.getSocketAddress();
            if (Objects.equals(inetSocketAddress.getHostName(), sessionInetSocketAddress.getHostName()) &&
                inetSocketAddress.getPort() == sessionInetSocketAddress.getPort()) return true;
        }

        return false;
    }

    private void onSessionStart(USyncSession session) {
        listeners.forEach(l -> l.uSyncServerSessionStart(session));
    }

    public void onBytesReceive(USyncSession session, byte[] bytes) {
        listeners.forEach(l -> l.uSyncServerBytesReceived(session, bytes));
    }

    void onMessageReceive(USyncSession session, byte[] bytes) {
        listeners.forEach(l -> l.uSyncServerMessageReceived(session, bytes));
    }

    void onObjectReceive(USyncSession session, Object object) {
        listeners.forEach(l -> l.uSyncServerObjectReceived(session, object));
    }

    void onSessionDisconnect(USyncSession session) {
        listeners.forEach(l -> l.uSyncServerSessionDisconnect(session));
    }

    static USyncServer server;
    static USyncClient client;

    @SneakyThrows
    public static void main(String[] args) {
        int bufferSize = 64;
        int interval = 250;
        int persistMessageDelay = 10;

        server = new USyncServerImpl("0.0.0.0", 8888, bufferSize, interval, true, persistMessageDelay);
        server.addUSyncServerListener(new Listener() {
            @Override
            public void uSyncServerSessionStart(USyncSession session) {
                System.out.println("SERVER SESSION START: " + session);

            }

            @Override
            public void uSyncServerBytesReceived(USyncSession session, byte[] bytes) {
                System.out.println("SERVER RECEIVED bytes:: " + Arrays.toString(bytes));
            }

            @Override
            public void uSyncServerMessageReceived(USyncSession session, byte[] bytes) {
                System.out.println("SERVER onMessageReceived " + new String(bytes, StandardCharsets.UTF_8));

            }

            @Override
            public void uSyncServerObjectReceived(USyncSession session, Object object) {
                System.out.println("SERVER RECEIVED " + object.toString());

                session.sendObject(
                    MyDto.builder()
                        .id(149)
                        .name("request")
                        .build()
                );
            }

            @Override
            public void uSyncServerSessionDisconnect(USyncSession session) {
                System.out.println("SERVER DISCONNECT " + session);

            }
        });


        server.setSendLoopActive(true);
        server.setReceiveLoopActive(true);

        System.out.println("start");

        client = new USyncClientImpl("localhost", 8888, bufferSize, interval, false, persistMessageDelay) {
            @Override
            public void onSessionStart(int sessionId) {
                System.out.println("session created");
                send(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
                sendMessage("1Hello world 2Hello world 3Hello world4".getBytes(StandardCharsets.UTF_8));

                sendObject(
                    MyDto.builder()
                        .id(148)
                        .name("request")
                        .build()
                );
            }

            @Override
            public void onBytesReceive(byte[] bytes) {
                System.out.println("CLIENT RECEIVED bytes: " + Arrays.toString(bytes));
            }

            @Override
            public void onObjectReceive(Object object) {
                System.out.println("CLIENT RECEIVED " + object.toString());

                MyDto responseDto = (MyDto) object;
                if (responseDto.id == 149) {
                    requestDisconnect();
                }
            }

            @Override
            public void onNormalDisconnect() {
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
