package com.gdresearch.net;

import com.gdresearch.net.connection.ConnectionListener;
import com.gdresearch.net.connection.IConnection;
import com.gdresearch.net.dev.ServerStarter;

import java.util.Map;
import java.util.function.BiConsumer;

public class Request {

    private static int idCounter = 0;

    private final IConnection connection;
    private BiConsumer<IConnection, Message> onResponseFunction;

    private int id = ++idCounter;

    public Request(IConnection connection) {
        this.connection = connection;
        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void connectionEstablished() {

            }

            @Override
            public void connectionBytesReceived(byte[] bytes) {
                Message message = Message.readMessage(bytes);
                if (message.getId() == id) {
                    onResponseFunction.accept(connection, Message.readMessage(bytes));
                    connection.removeConnectionListener(this);
                }
            }

            @Override
            public void connectionClosed(CloseStatus status) {
                connection.removeConnectionListener(this);
            }
        };

        connection.addConnectionListener(connectionListener);
    }

    public Request onResponse(BiConsumer<IConnection, Message> fn) {
        this.onResponseFunction = fn;
        return this;
    }

    public Request send(Map<String, String> headers, byte[] data) {
        Message message = new Message(id);
        headers.forEach(message::putHeader);
        message.setBody(data);
        connection.send(message.getMessageBytes());
        return this;
    }

    public Request send(Map<String, String> headers, String data) {
        Message message = new Message(id);
        headers.forEach(message::putHeader);
        message.setBody(data);
        connection.send(message.getMessageBytes());
        return this;
    }

    public Request send(byte[] data) {
        Message message = new Message(id);
        message.setBody(data);
        connection.send(message.getMessageBytes());
        return this;
    }

    public Request send(String data) {
        Message message = new Message(id);
        message.setBody(data);
        connection.send(message.getMessageBytes());
        return this;
    }

}
