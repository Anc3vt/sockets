package com.ancevt.net.dev;

import com.ancevt.util.command.Command;
import com.ancevt.util.command.CommandRepl;
import com.ancevt.util.command.CommandSet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;

public class UdpResearch {


    public static void main(String[] args) throws IOException, InterruptedException {


        new Thread(() -> {

            try {

                DatagramSocket socket = new DatagramSocket(5050);

                byte[] buff = new byte[8];

                while (true) {
                    DatagramPacket datagramPacket = new DatagramPacket(buff, buff.length);
                    socket.receive(datagramPacket);

                    System.out.println(Arrays.toString(buff));

                    DatagramPacket senddp = new DatagramPacket(new byte[]{1, 2}, 2, datagramPacket.getAddress(), datagramPacket.getPort());
                    socket.send(senddp);
                }


            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        }).start();


        Thread.sleep(1000);


        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");
        int port = 5050;
        byte buf[] = new byte[8];

        buf[0] = 1;
        buf[1] = 2;

        byte buf1[] = new byte[8];
        DatagramPacket dp = new DatagramPacket(buf, buf.length, address, port);
        DatagramPacket dptorec = new DatagramPacket(buf1, buf1.length);

        // connect() method
        socket.connect(address, port);

        // isBound() method
        System.out.println("IsBound : " + socket.isBound());

        // isConnected() method
        System.out.println("isConnected : " + socket.isConnected());

        // getInetAddress() method
        System.out.println("InetAddress : " + socket.getInetAddress());

        // getPort() method
        System.out.println("Port : " + socket.getPort());

        // getRemoteSocketAddress() method
        System.out.println("Remote socket address : " +
            socket.getRemoteSocketAddress());

        // getLocalSocketAddress() method
        System.out.println("Local socket address : " +
            socket.getLocalSocketAddress());

        // send() method
        socket.send(dp);
        System.out.println("...packet sent successfully....\n");

        // receive() method
        socket.receive(dptorec);
        System.out.println("Received packet data : " +
            Arrays.toString(dptorec.getData()));

        // getLocalPort() method
        System.out.println("Local Port : " + socket.getLocalPort());

        // getLocalAddress() method
        System.out.println("Local Address : " + socket.getLocalAddress());

        // setSOTimeout() method
        socket.setSoTimeout(50);

        // getSOTimeout() method
        System.out.println("SO Timeout : " + socket.getSoTimeout());


        CommandSet<Object> s = new CommandSet<>();
        CommandRepl<Object> repl = new CommandRepl<>(s);

        s.add(Command.of("send", a -> {

            byte[] b = new byte[4];
            new Random().nextBytes(b);

            DatagramPacket req = new DatagramPacket(b, b.length);
            try {
                socket.send(req);

                byte[] respBytes = new byte[8];

                DatagramPacket resp = new DatagramPacket(respBytes, respBytes.length, req.getAddress(), req.getPort());
                socket.receive(resp);

                System.out.println(">>>> " + Arrays.toString(respBytes));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return "";
        }));

        repl.start(System.in, System.out);


    }
}
