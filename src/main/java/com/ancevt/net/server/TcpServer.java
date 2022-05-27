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
package com.ancevt.net.server;

import com.ancevt.commons.Holder;
import com.ancevt.commons.concurrent.Lock;
import com.ancevt.commons.unix.UnixDisplay;
import com.ancevt.net.CloseStatus;
import com.ancevt.net.connection.ConnectionListener;
import com.ancevt.net.connection.IConnection;
import com.ancevt.net.connection.TcpConnection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.ancevt.commons.unix.UnixDisplay.debug;

public class TcpServer implements IServer {

    private static final int MAX_CONNECTIONS = Integer.MAX_VALUE;

    private final Set<IConnection> connections;
    private final Set<ServerListener> serverListeners;

    private ServerSocket serverSocket;

    private CountDownLatch countDownLatchForAsync;

    private boolean alive;

    private TcpServer() {
        connections = new CopyOnWriteArraySet<>();
        serverListeners = new HashSet<>();
    }

    @Override
    public void listen(String host, int port) {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                throw new IllegalStateException("Server is listening " + this);
            }

            alive = true;

            serverSocket = new ServerSocket();

            serverSocket.bind(new InetSocketAddress(host, port));

            dispatchServerStarted();

            while (alive) {
                Socket socket = serverSocket.accept();
                IConnection connectionWithClient = TcpConnection.createServerSide(
                        getNextFreeConnectionId(),
                        socket
                );
                connectionWithClient.addConnectionListener(new ConnectionListener() {
                    @Override
                    public void connectionEstablished() {
                        dispatchConnectionEstablished(connectionWithClient);
                    }

                    @Override
                    public void connectionBytesReceived(byte[] bytes) {
                        dispatchConnectionBytesReceived(connectionWithClient, bytes);
                    }

                    @Override
                    public void connectionClosed(CloseStatus status) {
                        connections.remove(connectionWithClient);
                        dispatchConnectionClosed(connectionWithClient, status);
                    }
                });
                connections.add(connectionWithClient);

                dispatchConnectionAccepted(connectionWithClient);

                new Thread(connectionWithClient::readLoop, "tcpservconn" + connectionWithClient.getId()).start();
            }

            dispatchServerClosed(new CloseStatus());
        } catch (IOException e) {
            dispatchServerClosed(new CloseStatus(e));
        }

        alive = false;
    }

    @Override
    public void asyncListen(String host, int port) {
        Thread thread = new Thread(() -> listen(host, port), "tcpservlisten_" + host + "_" + port);
        thread.start();
    }

    @Override
    public boolean asyncListenAndAwait(String host, int port) {
        return asyncListenAndAwait(host, port, Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public synchronized boolean asyncListenAndAwait(String host, int port, long time, TimeUnit timeUnit) {
        asyncListen(host, port);
        countDownLatchForAsync = new CountDownLatch(1);
        try {
            return countDownLatchForAsync.await(time, timeUnit);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void dispatchServerStarted() {
        serverListeners.forEach(ServerListener::serverStarted);
        if (countDownLatchForAsync != null) {
            countDownLatchForAsync.countDown();
            countDownLatchForAsync = null;
        }
    }

    private void dispatchServerClosed(CloseStatus status) {
        serverListeners.forEach(l -> l.serverClosed(status));
        if (countDownLatchForAsync != null) {
            countDownLatchForAsync.countDown();
            countDownLatchForAsync = null;
        }
    }

    private void dispatchConnectionEstablished(IConnection connectionWithClient) {
        serverListeners.forEach(l -> l.connectionEstablished(connectionWithClient));
    }

    private void dispatchConnectionClosed(IConnection connectionWithClient, CloseStatus status) {
        serverListeners.forEach(l -> l.connectionClosed(connectionWithClient, status));
    }

    private void dispatchConnectionBytesReceived(IConnection connection, byte[] data) {
        serverListeners.forEach(l -> l.connectionBytesReceived(connection, data));
    }

    private void dispatchConnectionAccepted(IConnection connectionWithClient) {
        serverListeners.forEach(l -> l.connectionAccepted(connectionWithClient));
    }

    private int getNextFreeConnectionId() {
        for (int i = 1; i < MAX_CONNECTIONS; i++) {
            if (getConnectionById(i) == null) return i;
        }
        throw new IllegalStateException("Connection count limit reached");
    }

    private IConnection getConnectionById(int id) {
        return connections.stream().filter(c -> c.getId() == id).findFirst().orElse(null);
    }

    @Override
    public boolean isListening() {
        return !serverSocket.isClosed() && alive;
    }

    @Override
    public synchronized void close() {
        try {
            if (!isListening()) {
                throw new IllegalStateException("Server not started");
            }

            connections.forEach(IConnection::closeIfOpen);
            serverSocket.close();
            alive = false;
        } catch (IOException e) {
            alive = false;
            dispatchServerClosed(new CloseStatus((e)));
        }
    }

    @Override
    public void addServerListener(ServerListener listener) {
        serverListeners.add(listener);
    }

    @Override
    public void removeServerListener(ServerListener listener) {
        serverListeners.remove(listener);
    }

    @Override
    public void sendToAll(byte[] bytes) {
        connections.forEach(c -> c.send(bytes));
    }

    @Override
    public Set<IConnection> getConnections() {
        return new HashSet<>(connections);
    }

    @Contract(" -> new")
    public static @NotNull IServer create() {
        return new TcpServer();
    }

    @Override
    public String toString() {
        return "TcpB254Server{" +
                "connections=" + connections.size() +
                ", serverListeners=" + serverListeners +
                ", serverSocket=" + serverSocket +
                ", alive=" + alive +
                '}';
    }

    public static void main(String[] args) {
        UnixDisplay.setEnabled(true);

        var lock = new Lock();

        Holder<Boolean> result = new Holder<>(false);

        IServer server = TcpServer.create();
        server.addServerListener(new ServerListener() {
            @Override
            public void serverStarted() {
                debug("com.ancevt.net.tcpb254.server.TcpServer.serverStarted(TcpServer:186): <A>STARTED");
            }

            @Override
            public void connectionAccepted(IConnection connection) {
                debug("com.ancevt.net.tcpb254.server.TcpServer.connectionAccepted(TcpServer:193): <A>ACCEPTED");
                System.out.println(connection);
            }

            @Override
            public void connectionClosed(IConnection connection, CloseStatus status) {
                debug("<R>com.ancevt.net.tcpb254.server.TcpServer.connectionClosed(TcpServer:198): <A>CONN CLOSED");
            }

            @Override
            public void connectionBytesReceived(IConnection connection, byte[] bytes) {
                debug("com.ancevt.net.tcpb254.server.TcpServer.connectionBytesReceived(TcpServer:203): <A>" + new String(bytes, StandardCharsets.UTF_8));
                connection.send("from server".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void serverClosed(CloseStatus status) {
                debug("com.ancevt.net.tcpb254.server.TcpServer.serverClosed(TcpServer:208): <A>SERVER CLOSED");
                server.removeServerListener(this);
            }

            @Override
            public void connectionEstablished(IConnection connectionWithClient) {
                debug("com.ancevt.net.tcpb254.server.TcpServer.connectionEstablished(TcpServer:213): <A>ESTABLISHED " + connectionWithClient);
            }
        });

        server.asyncListenAndAwait("0.0.0.0", 7777, 2, TimeUnit.SECONDS);

        IConnection connection = TcpConnection.create();
        connection.addConnectionListener(new ConnectionListener() {
            @Override
            public void connectionEstablished() {
                debug("<g>Connection connectionEstablished(TcpServer:225)");
                connection.send("Hello".repeat(10).getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void connectionBytesReceived(byte[] bytes) {
                debug("<g>Connection (TcpServer:230) <A>" + new String(bytes, StandardCharsets.UTF_8));
                result.setValue(true);
                lock.unlockIfLocked();
                connection.removeConnectionListener(this);
            }

            @Override
            public void connectionClosed(CloseStatus status) {
                debug("<g>Connection connectionClosed");
            }
        });
        connection.asyncConnectAndAwait("localhost", 7777, 2, TimeUnit.SECONDS);


        lock.lock(10, TimeUnit.SECONDS);
        server.close();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("done " + times);
        System.out.println("-".repeat(100));

        if(result.getValue()) {
            debug("TcpServer:317: <a><G>SUCCESS");
        } else {
            debug("<a><R>FAIL");
            System.exit(1);
        }


        times--;
        if (times > 0) {
            main(null);
        }
    }

    private static int times = 200;
}