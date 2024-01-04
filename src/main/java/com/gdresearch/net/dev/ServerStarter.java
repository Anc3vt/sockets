package com.gdresearch.net.dev;

import com.ancevt.commons.log.ColorizedLogTurboFilter;
import com.gdresearch.net.CloseStatus;
import com.gdresearch.net.Message;
import com.gdresearch.net.Request;
import com.gdresearch.net.connection.IConnection;
import com.gdresearch.net.connection.TcpConnection;
import com.gdresearch.net.server.IServer;
import com.gdresearch.net.server.ServerListenerAdapter;
import com.gdresearch.net.server.TcpServer;
import com.ancevt.util.command.Command;
import com.ancevt.util.command.CommandRepl;
import com.ancevt.util.command.CommandSet;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
public class ServerStarter {

    private static final int PORT = 5050;
    private static IServer server;

    private static Map<Integer, IConnection> connections = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ColorizedLogTurboFilter.setEnabled(true);

        server = TcpServer.create();

        server.addServerListener(new ServerListenerAdapter() {
            @Override
            public void serverStarted() {
                log.debug("<g>server started<>");
            }

            @Override
            public void connectionAccepted(IConnection connection) {
                log.debug("<g>connectionAccepted {}<>", connection);
            }

            @Override
            public void connectionClosed(IConnection connection, CloseStatus status) {
                log.debug("<g>connectionClosed {}<> {}", connection, status);
            }

            @Override
            public void connectionBytesReceived(IConnection connection, byte[] bytes) {
                log.debug("<g>connectionBytesReceived {}<> {}", connection, bytes.length);
                Message message = Message.readMessage(bytes);
                int id = message.getId();
                int a = Integer.parseInt(message.getHeaders().get("a"));

                try {
                    Thread.sleep(new Random().nextInt(3000));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                Message responseMessage = new Message(id, Collections.emptyMap(), "" + (a + 1));
                connection.send(responseMessage.getMessageBytes());
            }

            @Override
            public void serverClosed(CloseStatus status) {
                log.debug("<g>serverClosed {}<>", status);
            }

            @Override
            public void connectionEstablished(IConnection connectionWithClient) {
                log.debug("<g>connectionEstablished {}<>", connectionWithClient);
            }
        });

        server.asyncListen("localhost", PORT);

//        IConnection connection1 = TcpConnection.create(1);
//        connection1.addConnectionListener(new ConnectionListener() {
//            @Override
//            public void connectionEstablished() {
//
//            }
//
//            @Override
//            public void connectionBytesReceived(byte[] bytes) {
//                Message message = Message.readMessage(bytes);
//                log.debug("<b>{} {}<>", message.readText(), message);
//            }
//
//            @Override
//            public void connectionClosed(CloseStatus status) {
//
//            }
//        });
//        connection1.asyncConnect("localhost", PORT);
//        connections.put(1, connection1);





        CommandSet<Object> s = new CommandSet<>();
        CommandRepl<Object> repl = new CommandRepl<>(s);

        s.add(Command.of("/exit", a -> {
            System.exit(1);
            return "";
        }));
        s.add(Command.of("/start", a -> {
            server.asyncListen("localhost", PORT);
            return "";
        }));
        s.add(Command.of("conn", a -> {
            int id = a.next(int.class, new Random().nextInt());
            IConnection connection = TcpConnection.create(id);
            connection.asyncConnect("localhost", PORT);
            connections.put(id, connection);
            return "";
        }));
        s.add(Command.of("disc", a -> {
            int id = a.next(int.class);
            IConnection connection = connections.get(id);
            connection.close();
            return "";
        }));
        s.add(Command.of("send", a -> {
            int id = a.next(int.class);
            String text = a.next(String.class);
            IConnection connection = connections.get(id);
            connection.send(text.getBytes(StandardCharsets.UTF_8));
            return "";
        }));
        s.add(Command.of("sendm", a -> {
            int id = a.next(int.class);
            IConnection connection = connections.get(id);

            Request request = new Request(connection);
            request
                .onResponse(((connection2, message) -> {
                    System.out.println(message.readText());
                }))
                .send(new HashMap<String, String>() {
                    {
                        put("a", a.next());
                    }
                }, "test");

            return "";
        }));
        s.add(Command.of("info", a -> {
            int id = a.next(int.class);
            IConnection connection = connections.get(id);
            System.out.println(connection);
            return null;
        }));
        s.add(Command.of("test1", a -> {
            int id = a.next(int.class);
            IConnection connection = connections.get(id);
            System.out.println(connection);
            return null;
        }));

        repl.start(System.in, System.out);
    }


}
