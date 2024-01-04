package com.ancevt.net.usync;

import com.ancevt.commons.concurrent.Async;
import com.ancevt.commons.exception.NotImplementedException;
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
import java.util.concurrent.TimeUnit;

@Slf4j
class USyncUSyncSessionImpl implements USyncSession {

    private static final int MAX_SESSIONS = 256;
    private static final long DISCONNECT_NOTIFYING_DELAY_SECONDS = 1;
    private static int idCounter = 0;
    @Getter
    private final Map<Integer, SendingMessage> sendingMessages = new ConcurrentHashMap<>();
    @Getter
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();
    private final USyncServerImpl uSyncServer;
    private final DatagramChannel datagramChannel;
    @Getter
    private SocketAddress socketAddress;
    @Getter
    private final int bufferSize;
    @Getter
    private final int id;
    @Getter
    private ByteBuffer byteBufferToSend;
    @Getter
    private boolean disposed;


    USyncUSyncSessionImpl(USyncServerImpl uSyncServer, DatagramChannel datagramChannel, SocketAddress socketAddress, int bufferSize) {
        this.uSyncServer = uSyncServer;
        this.datagramChannel = datagramChannel;
        this.socketAddress = socketAddress;
        this.bufferSize = bufferSize;

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

    public ByteBuffer atomicSendBytes(SocketAddress socketAddress, byte[] bytes) {
        this.socketAddress = socketAddress;

        byte[] bytesToSend = new byte[bufferSize];
        System.arraycopy(bytes, 0, bytesToSend, 0, bytes.length);
        ByteBuffer byteBufferToSend = ByteBuffer.wrap(bytesToSend);
        try {
            datagramChannel.send(byteBufferToSend, this.socketAddress);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        return byteBufferToSend;
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
                    atomicSendBytes(socketAddress, Utils.createAcknowledgeBytes(id, messageId, 0));
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
                        Utils.createAcknowledgeBytes(id, messageId, receivingMessage.getPosition())
                    );

                    if (receivingMessage.isTotallyReceived()) {
                        if (receivingMessage.isDto()) {
                            ByteInput byteInput = ByteInput.newInstance(receivingMessage.getBytes());
                            String className = byteInput.readUtf(short.class);
                            String json = byteInput.readUtf(int.class);
                            try {

                                System.out.println(json);

                                Object object = Utils.gson().fromJson(json, Class.forName(className));
                                uSyncServer.onObjectReceive(this, object);
                            } catch (ClassNotFoundException e) {
                                log.warn(e.getMessage(), e);
                            }

                        } else {
                            uSyncServer.onMessageReceive(this, receivingMessage.getBytes());
                        }

                        receivingMessages.remove(messageId);
                        atomicSendBytes(
                            socketAddress,
                            Utils.createMessageReceivedBytes(id, messageId)
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

    @Override
    public void send(byte[] bytes) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeByte(Type.PLAIN);
        byteOutput.writeByte(id);
        byteOutput.write(bytes);
        byteBufferToSend = atomicSendBytes(socketAddress, byteOutput.toArray());
    }

    @Override
    public void sendMessage(byte[] bytes) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, id, bufferSize, false);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(socketAddress, sendingMessage.getInitialBytes());
    }

    @Override
    public void sendObject(Object object) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeUtf(short.class, object.getClass().getName());
        byteOutput.writeUtf(int.class, Utils.gson().toJson(object));
        byte[] bytes = byteOutput.toArray();

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, id, bufferSize, true);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(socketAddress, sendingMessage.getInitialBytes());
    }

    @Override
    public void disconnect() {
        send(
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
        //System.out.println("s receive " + Arrays.toString(bytes));
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
        final StringBuilder sb = new StringBuilder("USyncUSyncSessionImpl{");
        sb.append("socketAddress=").append(socketAddress);
        sb.append(", id=").append(id);
        sb.append(", disposed=").append(disposed);
        sb.append('}');
        return sb.toString();
    }
}
