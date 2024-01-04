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
package com.ancevt.net.usync;

import com.ancevt.commons.io.ByteOutput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

class Utils {

    private static Gson gson;

    public static DatagramChannel openChannel() throws IOException {
        return DatagramChannel.open();
    }

    public static DatagramChannel bindChannel(SocketAddress local) throws IOException {
        return openChannel().bind(local);
    }

    public static byte[] createAcknowledgeBytes(int sessionId, int messageId, int position) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        return byteOutput
            .writeByte(Type.ACKNOWLEDGE)
            .writeByte(sessionId)
            .writeByte(messageId)
            .writeInt(position)
            .toArray();
    }

    public static byte[] createMessageReceivedBytes(int sessionId, int messageId) {
        ByteOutput byteOutput = ByteOutput.newInstance();
        return byteOutput
            .writeByte(Type.MESSAGE_RECEIVED)
            .writeByte(sessionId)
            .writeByte(messageId)
            .toArray();
    }

    public static Gson gson() {
        if (gson == null) {
            gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .setPrettyPrinting()
                .create();
        }

        return gson;
    }


    public static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonElement jsonElement, java.lang.reflect.Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            String ldtString = jsonElement.getAsString();
            return LocalDateTime.parse(ldtString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        @Override
        public JsonElement serialize(LocalDateTime localDateTime, java.lang.reflect.Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
}
