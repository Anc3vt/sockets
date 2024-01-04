/**
 * Copyright (C) 2022 the original author or authors.
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
/*
 *     Copyright 2015-2022 Ancevt (me@ancevt.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "LICENSE");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ancevt.net.connection;

import com.ancevt.commons.io.ByteOutput;
import com.ancevt.commons.unix.UnixDisplay;
import com.ancevt.net.CloseStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

@Slf4j
public class TcpConnection implements IConnection {

    static {
        UnixDisplay.setEnabled(true); // TODO: remove
    }

    private static final int MAX_CHUNK_SIZE = 254;

    private final Object sendMonitor = new Object();
    private final Object receiveMonitor = new Object();

    private final int id;
    private final Set<ConnectionListener> listeners;
    private String host;
    private String remoteAddress;
    private int port;
    private int remotePort;

    private Socket socket;
    private volatile DataOutputStream dataOutputStream;

    private volatile CountDownLatch countDownLatchForAsync;

    private long bytesLoaded;
    private long bytesSent;

    TcpConnection(int id) {
        this.id = id;
        listeners = new CopyOnWriteArraySet<>();
        log.debug("New connection {}", this);
    }

    TcpConnection(int id, @NotNull Socket socket) {
        this(id);
        this.socket = socket;
        this.host = socket.getLocalAddress().getHostName();
        this.port = socket.getLocalPort();
        this.remoteAddress = socket.getRemoteSocketAddress().toString();
        this.remotePort = socket.getPort();
        try {
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void readLoop() {
        try {
            if (isOpen()) {
                dispatchConnectionEstablished();
            }

            InputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            ByteOutput byteOutput = ByteOutput.newInstance();

            while (isOpen()) {
                int len = readInt(in);
                bytesLoaded += 4;
                int left = len;
                while (left > 0) {
                    final int a = in.available();
                    byte[] bytes = new byte[Math.min(a, left)];
                    int read = in.read(bytes);
                    left -= read;
                    byteOutput.write(bytes);
                }

                if (byteOutput.hasData()) {
                    byte[] data = byteOutput.toArray();
                    byteOutput = ByteOutput.newInstance();
                    dispatchConnectionBytesReceived(data);
                }
            }
        } catch (IOException e) {
            closeIfOpen();
        }
        closeIfOpen();
    }

    private static int readInt(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    @Override
    public void connect(String host, int port) {
        this.socket = new Socket();
        log.debug("Connecting to {}:{}, {}", host, port, this);
        try {
            socket.connect(new InetSocketAddress(host, port));
            this.host = socket.getLocalAddress().getHostName();
            this.port = socket.getLocalPort();
            this.remoteAddress = socket.getRemoteSocketAddress().toString();
            this.remotePort = socket.getPort();
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            dispatchConnectionClosed(new CloseStatus(e));
        }
        readLoop();
    }

    @Override
    public long bytesSent() {
        return bytesSent;
    }

    @Override
    public long bytesLoaded() {
        return bytesLoaded;
    }

    @Override
    public void asyncConnect(String host, int port) {
        Thread thread = new Thread(() -> connect(host, port), "tcpB254Conn_" + getId() + "to_" + host + "_" + port);
        thread.start();
    }

    @Override
    public boolean asyncConnectAndAwait(String host, int port, long time, TimeUnit timeUnit) {
        countDownLatchForAsync = new CountDownLatch(1);
        asyncConnect(host, port);
        try {
            return isOpen() || countDownLatchForAsync.await(time, timeUnit);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean asyncConnectAndAwait(String host, int port) {
        return asyncConnectAndAwait(host, port, Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public void close() {
        try {
            socket.close();
            dataOutputStream = null;
            log.debug("Close connection {}", this);
            dispatchConnectionClosed(new CloseStatus());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return dataOutputStream != null;
    }

    private synchronized void dispatchConnectionEstablished() {
        listeners.forEach(ConnectionListener::connectionEstablished);
        if (countDownLatchForAsync != null) {
            countDownLatchForAsync.countDown();
            countDownLatchForAsync = null;
        }
    }

    private synchronized void dispatchConnectionClosed(CloseStatus status) {
        dataOutputStream = null;
        listeners.forEach(l -> l.connectionClosed(status));
        if (countDownLatchForAsync != null) {
            countDownLatchForAsync.countDown();
            countDownLatchForAsync = null;
        }
    }

    private void dispatchConnectionBytesReceived(byte[] bytes) {
        synchronized (receiveMonitor) {
            listeners.forEach(l -> {
                try {
                    l.connectionBytesReceived(bytes);
                } catch (Exception e) {
                    // TODO: improve
                    log.error(e.getMessage(), e);
                }
            });
        }
    }


    @Override
    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void send(byte[] bytes) {
        if (!isOpen()) return;

        synchronized (sendMonitor) {
            try {
                dataOutputStream.writeInt(bytes.length);
                dataOutputStream.write(bytes);
                bytesSent += 4 + bytes.length;
            } catch (SocketException e) {
                log.debug("Socket closed when send data");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void sendChunk(byte[] bytes, boolean fin) {
        try {
            if (socket.isConnected() && dataOutputStream != null) {
                if (bytes.length == 0) {
                    dataOutputStream.writeByte(255);
                    bytesSent++;
                } else {
                    dataOutputStream.writeByte(fin ? bytes.length : 0);
                    dataOutputStream.write(bytes);
                    bytesSent += 1 + bytes.length;
                }
            }

        } catch (SocketException e) {
            log.debug("Socket closed when send data");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sendComposite(byte[] bytes) {
        int pos = 0;
        int length;
        boolean fin = false;

        while (!fin) {
            length = min(bytes.length - pos, MAX_CHUNK_SIZE);
            byte[] dest = new byte[length];
            System.arraycopy(bytes, pos, dest, 0, length);
            fin = length < MAX_CHUNK_SIZE;
            sendChunk(dest, fin);
            pos += length;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcpConnection that = (TcpConnection) o;
        return id == that.id && port == that.port && remotePort == that.remotePort && bytesLoaded == that.bytesLoaded &&
                bytesSent == that.bytesSent && Objects.equals(listeners, that.listeners) &&
                Objects.equals(host, that.host) && Objects.equals(remoteAddress, that.remoteAddress) &&
                Objects.equals(socket, that.socket) && Objects.equals(dataOutputStream, that.dataOutputStream)
                && Objects.equals(countDownLatchForAsync, that.countDownLatchForAsync);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                host, port, remoteAddress, remotePort
        );
    }

    @Override
    public String toString() {
        return "TcpConnection{" +
                "id=" + id +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", remotePort=" + remotePort +
                ", isOpen=" + isOpen() +
                ", bytesLoaded=" + bytesLoaded +
                ", bytesSent=" + bytesSent +
                ", hashcode=" + hashCode() +
                '}';
    }

    @Contract(" -> new")
    public static @NotNull IConnection create() {
        return create(0);
    }

    @Contract("_ -> new")
    public static @NotNull IConnection create(int id) {
        return new TcpConnection(id);
    }

    @Contract("_, _ -> new")
    public static @NotNull IConnection createServerSide(int id, Socket socket) {
        return new TcpConnection(id, socket);
    }





}






















