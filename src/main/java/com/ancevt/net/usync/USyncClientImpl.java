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

import com.ancevt.commons.debug.TraceUtils;
import com.ancevt.commons.exception.NotImplementedException;
import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
class USyncClientImpl implements USyncClient {

    @Getter
    private final int bufferSize;
    private final DatagramChannel datagramChannel;
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();
    private final Sender sender;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final SocketAddress socketAddress;
    @Getter
    @Setter
    private int interval;
    @Getter
    private int sessionId;
    private Thread sendLoopThread;
    private Thread receiveLoopThread;
    @Getter
    private boolean disposed;

    public USyncClientImpl(String host, int port, int bufferSize, int interval, boolean blocking, int persistMessageEach) {
        this.bufferSize = bufferSize;
        this.interval = interval;

        try {
            datagramChannel = Utils.bindChannel(null);
            datagramChannel.configureBlocking(blocking);
            socketAddress = new InetSocketAddress(host, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sender = new Sender(datagramChannel, socketAddress, bufferSize, persistMessageEach);
        sender.setDebugObject(USyncClient.class);
    }

    @Override
    public void addUSyncClientListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUSyncClientListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void removeAllUSyncClientListeners() {
        listeners.clear();
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
    public long getBytesReceived() {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    public long getBytesSent() {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public void send(byte[] bytes) {
        sender.send(bytes);
    }

    @Override
    public void sendMessage(byte[] bytes) {
        sender.sendMessage(bytes);
    }

    @Override
    public void sendObject(Object object) {
        sender.sendObject(object);
    }

    private void atomicReceiveBytes(byte[] bytes) {
        TraceUtils.trace_6(Math.random());

        ByteInput in = ByteInput.newInstance(bytes);

        int type = in.readUnsignedByte();

        switch (type) {
            case Type.SESSION_ID: {
                if (sessionId == 0) {
                    this.sessionId = in.readUnsignedByte();
                    sender.setSessionId(sessionId);
                    onSessionStart(sessionId);
                }
                break;
            }
            case Type.PLAIN: {
                in.skipBytes(1);
                byte[] data = in.readBytes(bytes.length - 2);
                onBytesReceive(data);
                break;
            }
            case Type.START_CHUNKS: {
                in.skipBytes(1);
                int messageId = in.readUnsignedByte();
                int size = in.readInt();
                boolean dto = in.readBoolean();

                if (!receivingMessages.containsKey(messageId)) {
                    ReceivingMessage receivingMessage = new ReceivingMessage(messageId, size, dto);
                    receivingMessages.put(messageId, receivingMessage);
                    sender.sendResponseBytes(Utils.createAcknowledgeBytes(this.sessionId, messageId, 0));
                }
                break;
            }
            case Type.CHUNK: {
                in.skipBytes(1);
                int messageId = in.readUnsignedByte();
                int position = in.readInt();
                byte[] data = in.readBytes(bufferSize);

                ReceivingMessage receivingMessage = receivingMessages.get(messageId);
                if (receivingMessage != null && position == receivingMessage.getPosition()) {
                    receivingMessage.addBytes(data);
                    receivingMessage.next();


                    if (receivingMessage.isTotallyReceived()) {
                        if (receivingMessage.isDto()) {
                            ByteInput byteInput = ByteInput.newInstance(receivingMessage.getBytes());
                            String className = byteInput.readUtf(short.class);
                            String json = byteInput.readUtf(int.class);
                            try {
                                Object object = Utils.gson().fromJson(json, Class.forName(className));
                                onObjectReceive(object);
                            } catch (ClassNotFoundException e) {
                                log.warn(e.getMessage(), e);
                            }

                        } else {
                            onMessageReceive(receivingMessage.getBytes());
                        }

                        receivingMessages.remove(messageId);

                        byte[] messageReceivedMessageBytes = Utils.createMessageReceivedBytes(
                            this.sessionId,
                            messageId
                        );

                        sender.sendResponseBytes(messageReceivedMessageBytes);
                    } else {
                        sender.sendResponseBytes(
                            Utils.createAcknowledgeBytes(this.sessionId, messageId, receivingMessage.getPosition())
                        );
                    }
                }

                break;
            }
            case Type.ACKNOWLEDGE: {
                in.skipBytes(1);
                int messageId = in.readUnsignedByte();
                int position = in.readInt();

                SendingMessage sendingMessage = sender.getSendingMessages().get(messageId);

                if (sendingMessage != null && position == sendingMessage.getPosition()) {
                    sendingMessage.nextBytes();
                }
                break;
            }
            case Type.MESSAGE_RECEIVED: {
                in.skipBytes(1);
                int messageId = in.readUnsignedByte();
                SendingMessage sendingMessage = sender.getSendingMessages().get(messageId);
                if (sendingMessage != null) {
                    sender.getSendingMessages().remove(messageId);
                }
                break;
            }
            case Type.DISCONNECTED: {
                if (!disposed) {
                    dispose();
                    onNormalDisconnect();
                }
                break;
            }

        }
    }

    @Override
    public void requestSession() {
        sender.sendResponseBytes(new byte[]{Type.REQUEST_SESSION_ID});
    }

    @Override
    public void requestDisconnect() {
        sender.sendResponseBytes(
            ByteOutput.newInstance()
                .writeByte(Type.REQUEST_DISCONNECT)
                .writeByte(sessionId)
                .toArray()
        );
    }

    @Override
    public void setSendLoopActive(boolean b) {
        if ((b && sendLoopThread != null) || (!b && sendLoopThread == null)) return; // ignore if same value set

        if (b) {
            sendLoopThread = new Thread(
                () -> {
                    while (sendLoopThread != null) {
                        sender.sendLoopIteration();
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        }
                    }
                },
                "sendLoopThread" + hashCode()
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
                            log.debug(e.getMessage(), e);
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

    public void receive() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        try {
            SocketAddress sourceSocketAddress = datagramChannel.receive(byteBuffer);
            if (sourceSocketAddress != null) {
                byteBuffer.flip();
                byte[] bytes = new byte[byteBuffer.remaining()];
                if (bytes.length == 0) return;
                byteBuffer.get(bytes);
                atomicReceiveBytes(bytes);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean isReceiveLoopActive() {
        return false;
    }

    @Override
    public void dispose() {
        removeAllUSyncClientListeners();
        stop();

        try {
            datagramChannel.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        sessionId = 0;
        disposed = true;
    }

    public void onSessionStart(int sessionId) {
        listeners.forEach(l -> l.uSyncClientSessionStarted(sessionId));
    }

    public void onBytesReceive(byte[] bytes) {
        listeners.forEach(l -> l.uSyncClientSessionBytesReceived(bytes));
    }

    public void onMessageReceive(byte[] bytes) {
        listeners.forEach(l -> l.uSyncClientMessageReceived(bytes));
    }

    public void onObjectReceive(Object object) {
        listeners.forEach(l -> l.uSyncClientObjectReceived(object));
    }

    public void onNormalDisconnect() {
        listeners.forEach(Listener::uSyncClientNormalDisconnected);
    }
}
