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
package com.ancevt.net.test;

import com.ancevt.commons.Holder;
import com.ancevt.commons.concurrent.Lock;
import com.ancevt.commons.io.ByteInputReader;
import com.ancevt.commons.io.ByteOutputWriter;
import com.ancevt.commons.unix.UnixDisplay;
import com.ancevt.net.CloseStatus;
import com.ancevt.net.connection.ConnectionListenerAdapter;
import com.ancevt.net.connection.IConnection;
import com.ancevt.net.connection.TcpConnection;
import com.ancevt.net.server.IServer;
import com.ancevt.net.server.ServerListenerAdapter;
import com.ancevt.net.server.TcpServer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

public class TcpTest {

    private static final String SERVER_HOST = "0.0.0.0";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 7777;

    private IServer server;

    @BeforeEach
    void beforeEach() {
        UnixDisplay.setEnabled(true);
        server = TcpServer.create();
    }

    private void serverStart() {
        boolean serverStartResult = server.asyncListenAndAwait(SERVER_HOST, PORT, 10, TimeUnit.SECONDS);
        if (!serverStartResult) {
            fail();
        }
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        if (server.isListening()) server.close();
        Thread.sleep(100);
    }

    @Test
    void testClientSendServerReceive() {
        Lock lock = new Lock();

        Holder<Integer> firstByte = new Holder<>();
        Holder<Integer> middleByte = new Holder<>();
        Holder<Integer> lastByte = new Holder<>();

        server.addServerListener(new ServerListenerAdapter() {

            @Override
            public void connectionEstablished(IConnection connectionWithClient) {

            }

            @Override
            public void connectionBytesReceived(IConnection connection, byte[] bytes) {
                ByteInputReader r = ByteInputReader.newInstance(bytes);

                firstByte.setValue(r.readByte());

                int length = 10;
                while (length-- > 0) {
                    middleByte.setValue(r.readByte());
                }

                lastByte.setValue(r.readByte());
                lock.unlockIfLocked();
            }

            @Override
            public void connectionClosed(IConnection connection, CloseStatus status) {
                lock.unlockIfLocked();
            }
        });
        serverStart();

        IConnection connection = createConnection(0);
        ByteOutputWriter w = ByteOutputWriter.newInstance();
        w.writeByte(0);
        writeBytesTo(w, 10, 1);
        writeBytesTo(w, 1, 2);
        connection.send(w.toByteArray());

        lock.lock(250, TimeUnit.MILLISECONDS);


        assertThat(firstByte.getValue(), equalTo(0));
        assertThat(middleByte.getValue(), equalTo(1));
        assertThat(lastByte.getValue(), equalTo(2));
    }

    @Test
    void testServerSendClientReceive() {
        int[] result = sendFromServerReceiveOnClient(0, 1, 10, 2);

        assertThat(result[0], equalTo(0)); // first byte value
        assertThat(result[1], equalTo(1)); // middle byte value
        assertThat(result[2], equalTo(2)); // last byte value
        assertThat(result[3], equalTo(result[4])); // total bytes sent and received compare
    }

    @Test
    void test253Bytes() {
        actualTestNBytes(253);
    }

    @Test
    void test253Bytes_2() {
        actualTestNBytes(253);
    }

    @Test
    void test255Bytes() {
        actualTestNBytes(255);
    }

    @Test
    void test256Bytes() {
        actualTestNBytes(256);
    }

    @Test
    void test257Bytes() {
        actualTestNBytes(257);
    }

    @Test
    void test258Bytes() {
        actualTestNBytes(258);
    }

    @Test
    void testString() {
        assertThat(sendFromServerReceiveOnClient("Hello world"), equalTo("Hello world"));
    }

    @Test
    void testBigStringMoreThanPacketSize() {
        assertThat(actualTestBigStringMoreThanPacketSize(1000), equalTo(true));
    }

    @Test
    void testOneMoreBigStringTest() {
        assertThat(actualTestBigStringMoreThanPacketSize(10000000 / 10), equalTo(true));
    }

    public static String repeat(String value, int count) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            stringBuilder.append(value);
        }
        return stringBuilder.toString();
    }

    @Test
    void testMultithreadingSend() throws InterruptedException {
        final int clientCount = 100;

        CountDownLatch countDownLatch = new CountDownLatch(clientCount);

        String sourceString = repeat("string-10 ", 1000 / 10);
        byte[] sourceStringBytes = sourceString.getBytes(StandardCharsets.UTF_8);

        server.addServerListener(new ServerListenerAdapter() {
            @Override
            public void serverStarted() {
                System.out.println("Server started");
            }

            @Override
            public void connectionEstablished(IConnection connectionWithClient) {
                connectionWithClient.send(sourceStringBytes);
            }

            @Override
            public void serverClosed(CloseStatus status) {
                System.out.println(status);
            }
        });
        serverStart();

        List<Boolean> list = new CopyOnWriteArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            new Thread(() -> {
                IConnection connection = TcpConnection.create();
                connection.addConnectionListener(new ConnectionListenerAdapter() {
                    @Override
                    public void connectionBytesReceived(byte[] bytes) {
                        if (bytes.length != sourceStringBytes.length) {
                            countDownLatch.countDown();
                            throw new IllegalStateException("bytes.length != sourceStringBytes.length");
                        }
                        String resultString = new String(bytes, StandardCharsets.UTF_8);
                        list.add(resultString.equals(sourceString));
                        countDownLatch.countDown();

                    }
                });
                connection.connect(HOST, PORT);


            }, "thread" + clientCount).start();
        }

        countDownLatch.await(2, TimeUnit.SECONDS);

        assertThat(list.size(), equalTo(clientCount));
        list.forEach(Assertions::assertTrue);
    }

    private static IConnection createConnection(int connectionId) {
        IConnection connection = TcpConnection.create();
        connection.asyncConnectAndAwait(HOST, PORT, 2, TimeUnit.SECONDS);
        return connection;
    }

    private static ByteOutputWriter writeBytesTo(ByteOutputWriter w, int length, int valueByte) {
        while (length-- > 0) w.writeByte(valueByte);
        return w;
    }


    private void actualTestNBytes(int count) {
        int[] result = sendFromServerReceiveOnClient(0, 1, count - 2, 2);
        assertThat(result[0], equalTo(0)); // first byte value
        assertThat(result[1], equalTo(1)); // middle byte value
        assertThat(result[2], equalTo(2)); // last byte value
        assertThat(result[3], equalTo(result[4])); // total bytes sent and received compare
    }

    private boolean actualTestBigStringMoreThanPacketSize(int repeats) {
        boolean result;

        String sourceString = "string--" + (int) (Math.random() * 9) + " ";
        sourceString = repeat(sourceString, repeats);
        String resultString = sendFromServerReceiveOnClient(sourceString);
        result = resultString.equals(sourceString);

        String[] stringTokens = sourceString.split(" ");
        String[] resultTokens = resultString.split(" ");

        for (int i = 0; i < stringTokens.length; i++) {
            if (!stringTokens[i].equals(resultTokens[i])) {
                result = false;
                break;
            }
        }

        return result;
    }

    private String sendFromServerReceiveOnClient(String string) {
        System.out.println("sendFromServerReceiveOnClient string");
        Lock lock = new Lock();

        server.addServerListener(new ServerListenerAdapter() {

            @Override
            public void serverStarted() {
                System.out.println("Server started");
            }

            @Override
            public void connectionEstablished(IConnection connectionWithClient) {
                try {
                    System.out.println("Connection established: " + connectionWithClient);
                    ByteOutputWriter w = ByteOutputWriter.newInstance();
                    w.writeUtf(int.class, string);
                    byte[] array = w.toByteArray();
                    connectionWithClient.send(array);
                    System.out.println("Server sent " + array.length + " bytes of string");
                } catch (Exception e) {
                    lock.unlockIfLocked();
                    e.printStackTrace();
                }
            }

            @Override
            public void connectionClosed(IConnection connection, CloseStatus status) {
                System.out.println("Connection closed " + connection + " " + status);
            }

            @Override
            public void serverClosed(CloseStatus status) {
                System.out.println("Server closed " + status);
                lock.unlockIfLocked();
            }
        });

        serverStart();

        Holder<String> stringReceived = new Holder<>();

        IConnection connection = TcpConnection.create();
        connection.addConnectionListener(new ConnectionListenerAdapter() {

            @Override
            public void connectionEstablished() {
                System.out.println("Client connection established");
            }

            @Override
            public void connectionBytesReceived(byte[] bytes) {
                System.out.println("low rec " + bytes.length);
                try {
                    ByteInputReader r = ByteInputReader.newInstance(bytes);
                    stringReceived.setValue(r.readUtf(int.class));
                    System.out.println("Client received " + bytes.length + " bytes of string");
                    lock.unlockIfLocked();
                } catch (IllegalStateException e) {
                    lock.unlockIfLocked();
                    e.printStackTrace();
                }
            }

            @Override
            public void connectionClosed(CloseStatus status) {
                lock.unlockIfLocked();
            }
        });

        System.out.println("before asyncConnectAndAwait");
        connection.asyncConnectAndAwait(HOST, PORT, 1, TimeUnit.SECONDS);
        System.out.println("after asyncConnectAndAwait");

        if (stringReceived.getValue() == null) {
            System.out.println("lock 10");
            lock.lock(10, TimeUnit.SECONDS);
        }

        server.close();

        return stringReceived.getValue();
    }

    @Contract("_, _, _, _ -> new")
    private int @NotNull [] sendFromServerReceiveOnClient(int firstByte, int middleByte, int middleBytesLength, int lastByte) {
        System.out.println("sendFromServerReceiveOnClient ints");

        Lock lock = new Lock();

        Holder<Integer> bytesSent = new Holder<>();

        server.addServerListener(new ServerListenerAdapter() {
            @Override
            public void connectionEstablished(IConnection connectionWithClient) {
                try {
                    ByteOutputWriter w = ByteOutputWriter.newInstance();
                    w.writeByte(firstByte);
                    for (int i = 0; i < middleBytesLength; i++) w.writeByte(middleByte);
                    w.writeByte(lastByte);

                    byte[] array = w.toByteArray();
                    System.out.println("Server sent " + array.length + " byte packet");
                    bytesSent.setValue(array.length);
                    connectionWithClient.send(array);
                } catch (Exception e) {
                    lock.unlockIfLocked();
                    e.printStackTrace();
                }
            }

            @Override
            public void serverClosed(CloseStatus status) {
                System.out.println(status);
            }
        });
        serverStart();

        Holder<Integer> firstByteHolder = new Holder<>();
        Holder<Integer> middleByteHolder = new Holder<>();
        Holder<Integer> lastByteHolder = new Holder<>();
        Holder<Integer> bytesReceived = new Holder<>();

        IConnection connection = TcpConnection.create();
        connection.addConnectionListener(new ConnectionListenerAdapter() {

            @Override
            public void connectionEstablished() {
                System.out.println("Client connectionEstablished");
            }

            @Override
            public void connectionBytesReceived(byte[] bytes) {
                try {

                    ByteInputReader r = ByteInputReader.newInstance(bytes);

                    firstByteHolder.setValue(r.readByte());

                    int length = middleBytesLength;
                    while (length-- > 0) {
                        middleByteHolder.setValue(r.readByte());
                    }

                    lastByteHolder.setValue(r.readByte());

                    bytesReceived.setValue(bytes.length);
                    System.out.println("Client received " + bytes.length + " bytes");
                    lock.unlockIfLocked();
                    connection.close();
                } catch (IllegalStateException e) {
                    lock.unlockIfLocked();
                    e.printStackTrace();
                }
            }

            @Override
            public void connectionClosed(CloseStatus status) {
                lock.unlockIfLocked();
            }
        });


        System.out.println("before asyncConnectAndAwait");
        connection.asyncConnectAndAwait(HOST, PORT, 2, TimeUnit.SECONDS);
        System.out.println("after asyncConnectAndAwait");
        if (bytesReceived.getValue() == null) {
            System.out.println("lock");
            lock.lock(5, TimeUnit.SECONDS);
        }

        return new int[]{
                firstByteHolder.getValue(),
                middleByteHolder.getValue(),
                lastByteHolder.getValue(),
                bytesSent.getValue(),
                bytesReceived.getValue()
        };
    }


}






















