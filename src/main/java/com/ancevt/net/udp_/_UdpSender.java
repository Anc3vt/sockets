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
package com.ancevt.net.udp_;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

public class _UdpSender {

    private final int receiveBufferSize;
    private String targetHost;
    private int targetPort;
    private DatagramSocket datagramSocket;

    private boolean disposed;


    public _UdpSender(String targetHost, int targetPort, int receiveBufferSize) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.receiveBufferSize = receiveBufferSize;
    }

    public _UdpSender(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public void dispose() {
        datagramSocket.close();
    }

    private void actualSend(String host, int port, byte[] bytes, Consumer<byte[]> responseFunction, byte leadingByte) {
        this.targetHost = host;
        this.targetPort = port;

        try {
            if (datagramSocket == null || datagramSocket.isClosed()) {
                datagramSocket = new DatagramSocket();
                datagramSocket.setSoTimeout(1);
            }

            byte[] bytesToSend = _UdpUtils.createByteMessage(0, 0, bytes);
            DatagramPacket datagramPacketToSend = new DatagramPacket(
                bytesToSend,
                bytesToSend.length,
                InetAddress.getByName(targetHost),
                targetPort
            );

            datagramSocket.send(datagramPacketToSend);

            byte[] receivedBytes = new byte[receiveBufferSize + 1];
            DatagramPacket datagramPacket = new DatagramPacket(receivedBytes, receivedBytes.length);
            try {
                datagramSocket.receive(datagramPacket);

                byte responseLeadingByte = receivedBytes[0];
                byte[] receivedBytesToHandle = _UdpUtils.removeLeadingByte(receivedBytes);

                if (responseFunction != null) {
                    responseFunction.accept(receivedBytesToHandle);
                }

            } catch (SocketTimeoutException ex) {

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(String host, int port, byte[] bytes, Consumer<byte[]> responseFunction) {
        actualSend(host, port, bytes, responseFunction, (byte) 0);
    }

    public void send(byte[] bytes, Consumer<byte[]> responseFunction) {
        send(targetHost, targetPort, bytes, responseFunction);
    }

    public void guaranteedSend(byte[] bytes, Consumer<byte[]> responseFunction) {
        guaranteedSend(targetHost, targetPort, bytes, responseFunction);
    }

    private void guaranteedSend(String targetHost, int targetPort, byte[] bytes, Consumer<byte[]> responseFunction) {

    }
}
