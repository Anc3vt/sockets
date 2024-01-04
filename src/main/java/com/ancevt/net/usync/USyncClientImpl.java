package com.ancevt.net.usync;

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

    private final String host;
    private final int port;

    @Getter
    private final int bufferSize;
    private final DatagramChannel datagramChannel;
    private final Map<Integer, SendingMessage> sendingMessages = new ConcurrentHashMap<>();
    private final Map<Integer, ReceivingMessage> receivingMessages = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private int interval;
    private SocketAddress socketAddress;
    private int sessionId;
    private ByteBuffer byteBufferToSend;
    private Thread sendLoopThread;
    private Thread receiveLoopThread;

    @Getter
    private boolean disposed;

    private final List<Integer> datagramReceiveIds = new CopyOnWriteArrayList<>();

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public USyncClientImpl(String host, int port, int bufferSize, int interval, boolean blocking) {
        this.host = host;
        this.port = port;
        this.bufferSize = bufferSize;
        this.interval = interval;

        try {
            datagramChannel = Utils.bindChannel(null);
            datagramChannel.configureBlocking(blocking);
            socketAddress = new InetSocketAddress(host, port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    public int getSessionId() {
        return sessionId;
    }

    @Override
    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public void send(byte[] bytes) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeByte(Type.PLAIN);
        byteOutput.writeByte(sessionId);
        byteOutput.write(bytes);
        byteBufferToSend = atomicSendBytes(byteOutput.toArray());
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
        byteOutput.writeUtf(int.class, Utils.gson().toJson(object));
        byte[] bytes = byteOutput.toArray();

        SendingMessage sendingMessage = new SendingMessage(messageId, bytes, sessionId, bufferSize, true);
        sendingMessages.put(sendingMessage.getId(), sendingMessage);
        atomicSendBytes(sendingMessage.getInitialBytes());
    }

    private ByteBuffer atomicSendBytes(byte[] bytes) {
        byte[] bytesToSend = new byte[bufferSize + 4];
        System.arraycopy(bytes, 0, bytesToSend, 0, bytes.length);
        ByteBuffer bbts = ByteBuffer.wrap(bytesToSend);
        try {
            datagramChannel.send(bbts, socketAddress);
            //System.out.println("send " + Arrays.toString(bts.array()));

        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
        return bbts;
    }

    private void atomicReceiveBytes(byte[] bytes) {
        ByteInput in = ByteInput.newInstance(bytes);

        int recId = in.readInt();
        if(datagramReceiveIds.contains(recId)) return;
        datagramReceiveIds.add(recId);

        int type = in.readUnsignedByte();

        switch (type) {
            case Type.SESSION_ID: {
                if (sessionId == 0) {
                    this.sessionId = in.readUnsignedByte();
                    onSessionStart(sessionId);
                }
                break;
            }
            case Type.PLAIN: {
                int sessionId = in.readUnsignedByte();
                byte[] data = in.readBytes(bytes.length - 2);
                onBytesReceive(data);
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
                    atomicSendBytes(Utils.createAcknowledgeBytes(sessionId, messageId, 0));
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
                        Utils.createAcknowledgeBytes(sessionId, messageId, receivingMessage.getPosition())
                    );

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
                        atomicSendBytes(
                            Utils.createMessageReceivedBytes(sessionId, messageId)
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
                    onNormalDisconnect();
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
                                    //System.out.println("send " + Arrays.toString(byteBufferToSend.array()));
                                }

                                for (SendingMessage message : sendingMessages.values()) {
                                    byte[] src = message.getChunkBytes();
                                    byte[] bytesToSend = new byte[bufferSize];
                                    System.arraycopy(src, 0, bytesToSend, 0, src.length);
                                    ByteBuffer bts = ByteBuffer.wrap(bytesToSend);
                                    datagramChannel.send(bts, socketAddress);
                                    //System.out.println("send " + Arrays.toString(bts.array()));
                                }

//                                ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
//                                SocketAddress socketAddress = datagramChannel.receive(byteBuffer);
//                                if (socketAddress != null) {
//                                    atomicReceiveBytes(byteBuffer.array());
//                                }

                                Thread.sleep(interval);
                            }
                        } catch (IOException e) {
                            log.debug(e.getMessage(), e);
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
                if(bytes.length == 0) return;
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
        //System.out.println("c receive " + Arrays.toString(bytes));

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
