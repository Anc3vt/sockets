package com.ancevt.net.udp;

import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class UdpClientImpl implements UdpClient {

    private final String host;
    private final int port;
    private final int bufferSize;
    private final DatagramChannel datagramChannel;
    private final Map<Integer, SendingMessage> sendingMessages = new ConcurrentHashMap<>();
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();
    private int interval;
    private SocketAddress socketAddress;
    private int sessionId;
    private ByteBuffer byteBufferToSend;
    private Thread sendLoopThread;
    private Thread receiveLoopThread;

    @Getter
    private boolean disposed;

    public UdpClientImpl(String host, int port, int bufferSize, int interval, boolean blocking) {
        this.host = host;
        this.port = port;
        this.bufferSize = bufferSize;
        this.interval = interval;

        try {
            datagramChannel = UdpUtils.bindChannel(null);
            datagramChannel.configureBlocking(blocking);
            socketAddress = new InetSocketAddress(host, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setInterval(int interval) {
        this.interval = interval;
    }

    @Override
    public int getInterval() {
        return interval;
    }

    @Override
    public void send(byte[] bytes) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeByte(Type.PLAIN);
        byteOutput.writeByte(sessionId);
        byteOutput.write(bytes);
        atomicSendBytes(byteOutput.toArray());
    }

    @Override
    public void sendMessage(byte[] bytes) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, sessionId, bufferSize, false);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(sendingMessage.getInitialBytes());
    }

    @Override
    public void sendObject(Object object) {
        int messageId = 1;
        while (sendingMessages.containsKey(messageId)) {
            messageId++;
        }

        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeUtf(short.class, object.getClass().getName());
        byteOutput.writeUtf(int.class, UdpUtils.gson().toJson(object));
        byte[] bytes = byteOutput.toArray();

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, sessionId, bufferSize, true);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(sendingMessage.getInitialBytes());
    }

    private void atomicSendBytes(byte[] bytes) {
        byteBufferToSend = ByteBuffer.wrap(bytes);
        try {
            datagramChannel.send(byteBufferToSend, socketAddress);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void atomicReceiveBytes(byte[] bytes) {
        ByteInput in = ByteInput.newInstance(bytes);

        int type = in.readUnsignedByte();

        switch (type) {
            case Type.SESSION_ID: {
                if (sessionId == 0) {
                    this.sessionId = in.readUnsignedByte();
                    onSessionCreated(sessionId);
                }
                break;
            }
            case Type.PLAIN: {
                int sessionId = in.readUnsignedByte();
                byte[] data = in.readBytes(bytes.length - 2);
                onBytesReceived(data);
                break;
            }
            case Type.START_CHUNKS: {
                int sessionId = in.readUnsignedByte();
                int messageId = in.readUnsignedByte();
                int size = in.readInt();
                boolean dto = in.readBoolean();

                if (!receivingMessages.containsKey(messageId)) {
                    ReceivingMessage receivingMessage = new ReceivingMessage(messageId, size, dto);
                    receivingMessages.put(messageId, receivingMessage);
                    atomicSendBytes(UdpUtils.createAcknowledgeBytes(sessionId, messageId, 0));
                }
                break;
            }
            case Type.CHUNK: {
                int sessionId = in.readUnsignedByte();
                int messageId = in.readUnsignedByte();
                int position = in.readInt();
                byte[] data = in.readBytes(bufferSize);

                ReceivingMessage receivingMessage = receivingMessages.get(messageId);
                if (receivingMessage != null && position == receivingMessage.getPosition()) {
                    receivingMessage.addBytes(data);
                    receivingMessage.next();
                    atomicSendBytes(
                        UdpUtils.createAcknowledgeBytes(sessionId, messageId, receivingMessage.getPosition())
                    );

                    if (receivingMessage.isTotallyReceived()) {


                        if (receivingMessage.isDto()) {
                            ByteInput byteInput = ByteInput.newInstance(receivingMessage.getBytes());
                            String className = byteInput.readUtf(short.class);
                            String json = byteInput.readUtf(int.class);
                            try {
                                Object object = UdpUtils.gson().fromJson(json, Class.forName(className));
                                onObjectReceived(object);
                            } catch (ClassNotFoundException e) {
                                log.warn(e.getMessage(), e);
                            }

                        } else {
                            onMessageReceived(receivingMessage.getBytes());
                        }

                        receivingMessages.remove(messageId);
                        atomicSendBytes(
                            UdpUtils.createMessageReceivedBytes(sessionId, messageId)
                        );
                    }
                }

                break;
            }
            case Type.ACKNOWLEDGE: {
                int sessionId = in.readUnsignedByte();
                int messageId = in.readUnsignedByte();
                int position = in.readInt();

                SendingMessage sendingMessage = sendingMessages.get(messageId);

                if (sendingMessage != null && position == sendingMessage.getPosition()) {
                    sendingMessage.nextBytes();
                }
                break;
            }
            case Type.MESSAGE_RECEIVED: {
                int sessionId = in.readUnsignedByte();
                int messageId = in.readUnsignedByte();
                SendingMessage sendingMessage = sendingMessages.get(messageId);
                if (sendingMessage != null) {
                    sendingMessages.remove(messageId);
                }
                break;
            }
            case Type.DISCONNECTED: {
                if (!disposed) {
                    dispose();
                    onDisconnect();
                }
                break;
            }

        }
    }

    @Override
    public void requestSession() {
        atomicSendBytes(new byte[]{Type.REQUEST_SESSION_ID});
    }

    @Override
    public void requestDisconnect() {
        atomicSendBytes(ByteOutput.newInstance()
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
                        try {
                            if (socketAddress != null) {
                                if (byteBufferToSend != null) {
                                    datagramChannel.send(byteBufferToSend, socketAddress);
                                }

                                for (SendingMessage message : sendingMessages.values()) {
                                    atomicSendBytes(message.getChunkBytes());
                                }

                                ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                                SocketAddress socketAddress = datagramChannel.receive(byteBuffer);
                                if (socketAddress != null) {
                                    atomicReceiveBytes(byteBuffer.array());
                                }

                                Thread.sleep(interval);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } catch (InterruptedException e) {

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
                atomicReceiveBytes(byteBuffer.array());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isReceiveLoopActive() {
        return false;
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
        sessionId = 0;

        disposed = true;
    }

    @Override
    public void onSessionCreated(int sessionId) {

    }

    @Override
    public void onBytesReceived(byte[] bytes) {

    }

    @Override
    public void onMessageReceived(byte[] bytes) {

    }

    @Override
    public void onObjectReceived(Object object) {

    }

    @Override
    public void onDisconnect() {

    }
}
