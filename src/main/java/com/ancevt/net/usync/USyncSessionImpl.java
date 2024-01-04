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
import com.ancevt.commons.debug.TraceUtils;
import com.ancevt.commons.exception.NotImplementedException;
import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
class USyncSessionImpl implements USyncSession {

    private static final int MAX_SESSIONS = 256;
    private static final long DISCONNECT_NOTIFYING_DELAY_SECONDS = 1;
    private static int idCounter = 0;
    @Getter
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();
    private final USyncServerImpl uSyncServer;
    private final DatagramChannel datagramChannel;
    @Getter
    private final int bufferSize;
    @Getter
    private final int id;
    public final Sender sender;
    private final Receiver receiver;
    @Getter
    private SocketAddress socketAddress;
    @Getter
    private boolean disposed;


    USyncSessionImpl(USyncServerImpl uSyncServer, DatagramChannel datagramChannel, SocketAddress socketAddress, int bufferSize, int persistMessageDelay) {
        this.uSyncServer = uSyncServer;
        this.datagramChannel = datagramChannel;
        this.socketAddress = socketAddress;
        this.bufferSize = bufferSize;

        sender = new Sender(datagramChannel, socketAddress, bufferSize, persistMessageDelay);
        sender.setDebugObject(USyncSession.class);

        receiver = new Receiver(sender);

        if (idCounter >= MAX_SESSIONS) {
            idCounter = 0;
        }

        this.id = ++idCounter;
    }

    @Override
    public long getBytesReceived() {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    public long getBytesSent() {
        throw new NotImplementedException("not implemented yet");
    }

    public void receiveBytes(SocketAddress socketAddress, int type, ByteInput in) {
        this.socketAddress = socketAddress;

        TraceUtils.trace_3(Math.random());

        switch (type) {
            case Type.PLAIN: {
                byte[] data = in.readBytes(bufferSize - 2);
                bytesReceived(data);
                break;
            }
            case Type.START_CHUNKS: {
                int messageId = in.readUnsignedByte();
                int size = in.readInt();
                boolean dto = in.readBoolean();
                if (!receivingMessages.containsKey(messageId)) {
                    ReceivingMessage receivingMessage = new ReceivingMessage(messageId, size, dto);
                    receivingMessages.put(messageId, receivingMessage);
                    sender.sendResponseBytes(Utils.createAcknowledgeBytes(id, messageId, 0));
                }
                break;
            }
            case Type.CHUNK: {
                int messageId = in.readUnsignedByte();
                int position = in.readInt();
                byte[] bytes = in.readBytes(bufferSize);

                ReceivingMessage receivingMessage = receivingMessages.get(messageId);
                if (receivingMessage != null && position == receivingMessage.getPosition()) {
                    receivingMessage.addBytes(bytes);
                    receivingMessage.next();
                    sender.sendResponseBytes(
                        Utils.createAcknowledgeBytes(id, messageId, receivingMessage.getPosition())
                    );

                    if (receivingMessage.isTotallyReceived()) {
                        if (receivingMessage.isDto()) {
                            ByteInput byteInput = ByteInput.newInstance(receivingMessage.getBytes());
                            String className = byteInput.readUtf(short.class);
                            String json = byteInput.readUtf(int.class);
                            try {
                                Object object = Utils.gson().fromJson(json, Class.forName(className));
                                uSyncServer.onObjectReceive(this, object);
                            } catch (ClassNotFoundException e) {
                                log.warn(e.getMessage(), e);
                            }

                        } else {
                            uSyncServer.onMessageReceive(this, receivingMessage.getBytes());
                        }

                        receivingMessages.remove(messageId);
                        sender.sendResponseBytes(Utils.createMessageReceivedBytes(id, messageId));
                    }
                }

                break;
            }
            case Type.ACKNOWLEDGE: {
                int messageId = in.readUnsignedByte();
                int position = in.readInt();

                SendingMessage sendingMessage = sender.getSendingMessages().get(messageId);

                if (sendingMessage != null && position == sendingMessage.getPosition()) {
                    sendingMessage.nextBytes();
                }

                break;
            }
            case Type.MESSAGE_RECEIVED: {
                int messageId = in.readUnsignedByte();
                SendingMessage sendingMessage = sender.getSendingMessages().get(messageId);

                if (sendingMessage != null) {
                    sender.getSendingMessages().remove(messageId);
                }
                break;
            }
        }
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

    @Override
    public void disconnect() {
        sender.sendResponseBytes(
            ByteOutput.newInstance()
                .writeByte(Type.DISCONNECTED)
                .toArray()
        );

        Async.runLater(DISCONNECT_NOTIFYING_DELAY_SECONDS, TimeUnit.SECONDS, () -> {
            dispose();
            uSyncServer.getSessions().remove(getId());
            uSyncServer.sessions.remove(getId());
            uSyncServer.sessionsCopy.remove(getId());
            uSyncServer.onSessionDisconnect(this);
        });
    }

    private void bytesReceived(byte[] bytes) {
        uSyncServer.onBytesReceive(this, bytes);
    }

    @Override
    public USyncServer getServer() {
        return uSyncServer;
    }

    public void dispose() {
        disposed = true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("USyncSessionImpl{");
        sb.append("socketAddress=").append(socketAddress);
        sb.append(", id=").append(id);
        sb.append(", disposed=").append(disposed);
        sb.append('}');
        return sb.toString();
    }
}
