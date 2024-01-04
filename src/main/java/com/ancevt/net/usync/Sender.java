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

import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
class Sender {

    @Getter
    private final Map<Integer, SendingMessage> sendingMessages = new ConcurrentHashMap<>();
    private final DatagramChannel datagramChannel;
    private final SocketAddress socketAddress;
    private final int bufferSize;
    private final int persistMessageDelay;

    @Setter
    @Getter
    private int sessionId;
    @Getter
    private ByteBuffer plainByteBufferToSend;

    private final Map<ByteBuffer, Integer> byteBuffersToSend = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private Object debugObject;

    private int tick;

    public Sender(DatagramChannel datagramChannel, SocketAddress socketAddress, int bufferSize, int persistMessageDelay) {
        this.datagramChannel = datagramChannel;
        this.socketAddress = socketAddress;
        this.bufferSize = bufferSize;
        this.persistMessageDelay = persistMessageDelay;
    }

    public void send(byte[] bytes) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeByte(Type.PLAIN);
        byteOutput.writeByte(sessionId);
        byteOutput.write(bytes);
        plainByteBufferToSend = atomicSendBytes(byteOutput.toArray());
    }

    public void sendMessage(byte[] bytes) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, sessionId, bufferSize, false);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(sendingMessage.getInitialBytes());
    }

    public void sendObject(Object object) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeUtf(short.class, object.getClass().getName());
        byteOutput.writeUtf(int.class, Utils.gson().toJson(object));
        byte[] bytes = byteOutput.toArray();

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, sessionId, bufferSize, true);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(sendingMessage.getInitialBytes());
    }

    public ByteBuffer atomicSendBytes(byte[] bytes) {

        byte[] bytesToSend = new byte[bufferSize];
        System.arraycopy(bytes, 0, bytesToSend, 0, bytes.length);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytesToSend);

        try {
            datagramChannel.send(byteBuffer, socketAddress);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }

        return byteBuffer;
    }

    public ByteBuffer sendResponseBytes(byte[] bytes, int remain) {
        ByteBuffer byteBuffer = atomicSendBytes(bytes);
        byteBuffersToSend.put(byteBuffer, remain);
        return byteBuffer;
    }

    public ByteBuffer sendResponseBytes(byte[] bytes) {
        return sendResponseBytes(bytes, 10);
    }

    public void sendLoopIteration() {
        try {
            if (socketAddress != null) {
                if (plainByteBufferToSend != null) {
                    datagramChannel.send(plainByteBufferToSend, socketAddress);
                }

                tick++;
                if (tick >= persistMessageDelay) {
                    for (SendingMessage message : sendingMessages.values()) {
                        byte[] src = message.getChunkBytes();
                        byte[] bytesToSend = new byte[bufferSize];
                        System.arraycopy(src, 0, bytesToSend, 0, src.length);
                        ByteBuffer bts = ByteBuffer.wrap(bytesToSend);
                        datagramChannel.send(bts, socketAddress);
                    }
                    tick = 0;

                    Set<ByteBuffer> toRemove = new HashSet<>();

                    byteBuffersToSend.forEach((b, remain) -> {
                        try {
                            datagramChannel.send(b, socketAddress);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        byteBuffersToSend.put(b, remain--);

                        if (remain == 0) {
                            toRemove.add(b);
                        }
                    });

                    toRemove.forEach(b -> byteBuffersToSend.remove(b));
                }
            }
        } catch (IOException e) {
            log.debug(e.getMessage(), e);
        }
    }
}
