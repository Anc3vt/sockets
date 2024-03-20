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

import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

class ReceivingMessage {

    @Getter
    private final int id;

    @Getter
    private final int size;

    private int bytesReceived;

    private final ByteArrayOutputStream dataOutputStream;
    private final boolean dto;

    @Getter
    private int position;

    public ReceivingMessage(int id, int size, boolean dto) {
        this.id = id;
        this.size = size;
        this.dataOutputStream = new ByteArrayOutputStream(size);
        this.dto = dto;
    }

    public boolean isDto() {
        return dto;
    }

    public boolean isNew() {
        return position == 0;
    }

    public boolean isTotallyReceived() {
        return bytesReceived >= size;
    }

    public void addBytes(byte[] bytes) {
        try {
            bytesReceived += bytes.length;
            dataOutputStream.write(bytes);

            if (bytesReceived > size) bytesReceived = size;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes() {
        byte[] result = new byte[size];
        System.arraycopy(dataOutputStream.toByteArray(), 0, result, 0, size);
        return result;
    }

    public String getString() {
        return new String(getBytes(), StandardCharsets.UTF_8);
    }

    public void next() {
        position++;
    }
}
