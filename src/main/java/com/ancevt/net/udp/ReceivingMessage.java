package com.ancevt.net.udp;

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
