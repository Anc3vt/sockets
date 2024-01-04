package com.ancevt.net.udp;

import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class Session {

    private static final int MAX_SESSIONS = 256;
    private static int idCounter = 0;
    @Getter
    private final Map<Integer, SendingMessage> sendingMessages = new ConcurrentHashMap<>();
    @Getter
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();
    private final UdpServer udpServer;
    private final DatagramChannel datagramChannel;
    @Getter
    private SocketAddress socketAddress;
    private final int bufferSize;
    @Getter
    private final int id;
    @Getter
    private ByteBuffer byteBufferToSend;
    @Getter
    private boolean disposed;

    Session(UdpServer udpServer, DatagramChannel datagramChannel, SocketAddress socketAddress, int bufferSize) {
        this.udpServer = udpServer;
        this.datagramChannel = datagramChannel;
        this.socketAddress = socketAddress;
        this.bufferSize = bufferSize;

        if (idCounter >= MAX_SESSIONS) {
            idCounter = 0;
        }

        this.id = ++idCounter;
    }

    public void atomicSendBytes(SocketAddress socketAddress, byte[] bytes) {
        this.socketAddress = socketAddress;

        byteBufferToSend = ByteBuffer.wrap(bytes);
        try {
            datagramChannel.send(byteBufferToSend, this.socketAddress);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public void receiveBytes(SocketAddress socketAddress, int type, ByteInput in) {
        this.socketAddress = socketAddress;

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
                    atomicSendBytes(socketAddress, UdpUtils.createAcknowledgeBytes(id, messageId, 0));
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
                    atomicSendBytes(
                        socketAddress,
                        UdpUtils.createAcknowledgeBytes(id, messageId, receivingMessage.getPosition())
                    );

                    if (receivingMessage.isTotallyReceived()) {
                        if (receivingMessage.isDto()) {
                            ByteInput byteInput = ByteInput.newInstance(receivingMessage.getBytes());
                            String className = byteInput.readUtf(short.class);
                            String json = byteInput.readUtf(int.class);
                            try {
                                Object object = UdpUtils.gson().fromJson(json, Class.forName(className));
                                udpServer.onObjectReceived(this, object);
                            } catch (ClassNotFoundException e) {
                                log.warn(e.getMessage(), e);
                            }

                        } else {
                            udpServer.onMessageReceived(this, receivingMessage.getBytes());
                        }

                        receivingMessages.remove(messageId);
                        atomicSendBytes(
                            socketAddress,
                            UdpUtils.createMessageReceivedBytes(id, messageId)
                        );
                    }
                }

                break;
            }
            case Type.ACKNOWLEDGE: {
                int messageId = in.readUnsignedByte();
                int position = in.readInt();

                SendingMessage sendingMessage = sendingMessages.get(messageId);

                if (sendingMessage != null && position == sendingMessage.getPosition()) {
                    sendingMessage.nextBytes();
                }
                break;
            }
            case Type.MESSAGE_RECEIVED: {
                int messageId = in.readUnsignedByte();
                SendingMessage sendingMessage = sendingMessages.get(messageId);
                if (sendingMessage != null) {
                    sendingMessages.remove(messageId);
                }
                break;
            }
        }
    }

    public void send(byte[] bytes) {
        atomicSendBytes(socketAddress, bytes);
    }

    public void sendMessage(byte[] bytes) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, id, bufferSize, false);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(socketAddress, sendingMessage.getInitialBytes());
    }

    public void sendObject(Object object) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeUtf(short.class, object.getClass().getName());
        byteOutput.writeUtf(int.class, UdpUtils.gson().toJson(object));
        byte[] bytes = byteOutput.toArray();

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, id, bufferSize, true);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(socketAddress, sendingMessage.getInitialBytes());
    }

    private void bytesReceived(byte[] data) {
        udpServer.onBytesReceived(this, data);
    }


    public void dispose() {
        disposed = true;
    }

}
