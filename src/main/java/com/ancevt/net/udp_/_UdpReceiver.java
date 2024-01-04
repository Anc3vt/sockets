package com.ancevt.net.udp_;

import lombok.ToString;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ToString
public class _UdpReceiver {
    private final int receiveBufferSize;
    private int listeningPort;
    private boolean disposed;
    private DatagramSocket datagramSocket;


    public _UdpReceiver(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    private final List<UdpListener> udpListeners = new ArrayList<>();

    public void addUdpListener(UdpListener udpListener) {
        udpListeners.add(udpListener);
    }

    public void removeUdpListener(UdpListener udpListener) {
        udpListeners.remove(udpListener);
    }

    public void listen(int port) {
        this.listeningPort = port;

        while (!disposed) {
            try {
                if (datagramSocket == null) {
                    datagramSocket = new DatagramSocket(listeningPort);
                    datagramSocket.setSoTimeout(1);
                }
                byte[] receivedBytes = new byte[receiveBufferSize + 1];
                DatagramPacket datagramPacket = new DatagramPacket(receivedBytes, receivedBytes.length);
                datagramSocket.receive(datagramPacket);

                byte leadingByte = receivedBytes[0];
                byte[] receivedBytesToHandle = _UdpUtils.removeLeadingByte(receivedBytes);

                for (UdpListener l : udpListeners) {
                    byte[] responseBytes = l.udpBytesReceived(datagramPacket, receivedBytesToHandle);

                    if (responseBytes != null) {
                        byte[] bytesToResponse = _UdpUtils.addLeadingByte(responseBytes, (byte) 0);

                        DatagramPacket responseDatagramPacket = new DatagramPacket(
                            bytesToResponse,
                            bytesToResponse.length,
                            datagramPacket.getAddress(),
                            datagramPacket.getPort()
                        );

                        datagramSocket.send(responseDatagramPacket);
                    }
                }
                //datagramSocket.close();

            } catch (IOException e) {

            }

        }
    }

    public void dispose() {
        disposed = true;
        udpListeners.clear();
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
            datagramSocket = null;
        }
    }

    public void asyncListen(int port) {
        this.listeningPort = port;

        Thread thread = new Thread(() -> listen(port), "udplisten_" + port);
        thread.start();
    }


    @FunctionalInterface
    public interface UdpListener {

        byte[] udpBytesReceived(DatagramPacket datagramPacket, byte[] bytes);
    }

    public static void main(String[] args) {
        byte[] b = {0, 1, 2, 3, 4};

        byte[] t = new byte[b.length - 1];

        System.arraycopy(b, 1, t, 0, t.length);

        System.out.println(Arrays.toString(t));
    }
}
