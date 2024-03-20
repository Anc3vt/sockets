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
package com.ancevt.net;

import com.ancevt.net.connection.ConnectionListener;
import com.ancevt.net.connection.IConnection;
import com.ancevt.net.dev.ServerStarter;

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
