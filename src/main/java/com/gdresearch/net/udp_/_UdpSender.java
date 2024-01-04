package com.gdresearch.net.udp_;

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
