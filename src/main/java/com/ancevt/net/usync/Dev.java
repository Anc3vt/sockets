package com.ancevt.net.usync;

import com.ancevt.commons.debug.TraceUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

class Dev {

    public static void main(String[] args) {
        final int INTERVAL = 1;
        final int BUFFER_SIZE = 256;
        final int PERSIST_MESSAGE_DELAY = 10;

        USyncServer server = USyncServer.newInstance("0.0.0.0", 7778, BUFFER_SIZE, INTERVAL, false, PERSIST_MESSAGE_DELAY);
        server.addUSyncServerListener(new USyncServer.Listener() {
            @Override
            public void uSyncServerSessionStart(USyncSession session) {
                TraceUtils.trace_1("SERVER SESSION STARTED " + session);
            }

            @Override
            public void uSyncServerBytesReceived(USyncSession session, byte[] bytes) {

            }

            @Override
            public void uSyncServerMessageReceived(USyncSession session, byte[] bytes) {
                TraceUtils.trace_1("SERVER MESSAGE RECEIVED size: " + bytes.length);
            }

            @Override
            public void uSyncServerObjectReceived(USyncSession session, Object object) {
                TraceUtils.trace_1("SERVER OBJECT RECEIVED: " + object.toString());

                session.sendObject(
                    Dto.builder()
                        .id(200)
                        .name("RESPONSE")
                        .build()
                );
            }

            @Override
            public void uSyncServerSessionDisconnect(USyncSession session) {
                TraceUtils.trace_1("SERVER SESSION DISCONNECT: " + session);
            }
        });
        server.start();

        USyncClient client = USyncClient.newInstance("localhost", 7778, BUFFER_SIZE, INTERVAL, false, PERSIST_MESSAGE_DELAY);
        client.addUSyncClientListener(new USyncClient.Listener() {
            @Override
            public void uSyncClientSessionStarted(int sessionId) {
                TraceUtils.trace_4("CLIENT SESSION STARTED " + sessionId);

                client.sendObject(
                    Dto.builder()
                        .id(148)
                        .name("request")
                        .build()
                );
            }

            @Override
            public void uSyncClientSessionBytesReceived(byte[] bytes) {

            }

            @Override
            public void uSyncClientMessageReceived(byte[] bytes) {
                TraceUtils.trace_4("CLIENT MESSAGE RECEIVED size: " + bytes.length);
            }

            @Override
            public void uSyncClientObjectReceived(Object object) {
                TraceUtils.trace_4("CLIENT OBJECT RECEIVED: " + object.toString());
            }

            @Override
            public void uSyncClientNormalDisconnected() {
                TraceUtils.trace_4("CLIENT DISCONNECT");
            }
        });
        client.start();
        client.requestSession();
    }

    @ToString
    @Builder
    private static class Dto {
        private int id;
        private String name;

        @Builder.Default
        private List<String> list = generateList();

        private static List<String> generateList() {
            List<String> result = new ArrayList<>();
            for(int i = 0; i < 100; i ++) {
                result.add("" + Math.random());
            }

            return result;
        }


    }
}
