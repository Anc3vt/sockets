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

import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.Getter;

class SendingMessage {

    @Getter
    private final int id;
    private final byte[] data;
    private final ByteInput in;
    private final int sessionId;
    private final int bufferSize;
    private final boolean dto;

    @Getter
    private int position;

    private byte[] chunkBytes;
    private int bytesSent;

    public SendingMessage(int id, byte[] data, int sessionId, int bufferSize, boolean dto) {
        this.id = id;
        this.data = data;
        this.in = ByteInput.newInstance(data);
        this.sessionId = sessionId;
        this.bufferSize = bufferSize;
        this.dto = dto;
    }

    public boolean isDto() {
        return dto;
    }

    public boolean isNew() {
        return chunkBytes == null;
    }

    public byte[] getChunkBytes() {
        return chunkBytes;
    }

    public boolean nextBytes() {
        if (!in.hasNextData()) return false;

        ByteOutput byteOutput = ByteOutput.newInstance(bufferSize);
        byteOutput
            .writeByte(Type.CHUNK) // 1
            .writeByte(sessionId)  // 1
            .writeByte(id)         // 1
            .writeInt(position);   // 4

        position++;

        byteOutput.write(in.readBytes(bufferSize - 1 - 1 - 1 - 4));
        bytesSent += bufferSize - 1 - 1 - 1 - 4;

        byte[] bytes = byteOutput.toArray();

        if (bytes.length < bufferSize) {
            byteOutput.write(new byte[bufferSize - bytes.length]);
        }

        chunkBytes = byteOutput.toArray();

        return bytesSent < data.length;
    }

    public byte[] getInitialBytes() {
        ByteOutput byteOutput = ByteOutput.newInstance(bufferSize);
        byteOutput
            .writeByte(Type.START_CHUNKS) // 1
            .writeByte(sessionId)         // 1
            .writeByte(id)                // 1
            .writeInt(data.length)        // 4
            .writeBoolean(dto);           // 1

        byte[] bytes = byteOutput.toArray();

        if (bytes.length < bufferSize) {
            byteOutput.write(new byte[bufferSize - bytes.length]);
        }

        return chunkBytes = byteOutput.toArray();
    }

}
