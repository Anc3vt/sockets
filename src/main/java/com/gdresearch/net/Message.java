package com.gdresearch.net;

import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class Message {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private final int id;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body = EMPTY_BYTE_ARRAY;

    public Message(int id) {
        this.id = id;
    }

    public Message(int id, byte[] body) {
        this.id = id;
        this.body = body;
    }

    public Message(int id, String text) {
        this.id = id;
        this.body = text.getBytes(StandardCharsets.UTF_8);
    }

    public Message(int id, Map<String, String> headers) {
        this.id = id;
        headers.forEach(this::putHeader);
    }

    public Message(int id, Map<String, String> headers, byte[] body) {
        this.id = id;
        this.body = body;
        headers.forEach(this::putHeader);
    }

    public Message(int id, Map<String, String> headers, String text) {
        this.id = id;
        this.body = text.getBytes(StandardCharsets.UTF_8);
        headers.forEach(this::putHeader);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Message putHeader(String key, Object value) {
        getHeaders().put(key, String.valueOf(value));
        return this;
    }

    public String readText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public int getId() {
        return id;
    }

    public void setBody(byte[] data) {
        this.body = data;
    }

    public void setBody(String string) {
        setBody(string.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] getBody() {
        return body;
    }

    public byte[] getMessageBytes() {
        ByteOutput byteOutput = ByteOutput.newInstance();
        byteOutput.writeInt(getId());

        byte[] headersBytes = headersToBytes(headers);
        byteOutput.writeShort(headersBytes.length);
        byteOutput.write(headersBytes);

        byteOutput.writeInt(body.length);
        byteOutput.write(body);

        return byteOutput.toArray();
    }

    private static byte[] headersToBytes(Map<String, String> headers) {
        StringBuilder stringBuilder = new StringBuilder();
        headers.forEach((k, v) -> {
            stringBuilder
                .append(k)
                .append(':')
                .append(v)
                .append('\n');
        });
        return stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Message{");
        sb.append("id=").append(id);
        sb.append(", headers=").append(headers);
        sb.append(", body size=").append(body.length);
        sb.append('}');
        return sb.toString();
    }

    public static Message readMessage(byte[] bytes) {
        ByteInput byteInput = ByteInput.newInstance(bytes);

        int id = byteInput.readInt();

        Message result = new Message(id);

        int headersBytesLength = byteInput.readUnsignedShort();
        byte[] headersBytes = byteInput.readBytes(headersBytesLength);
        String headersString = new String(headersBytes, StandardCharsets.UTF_8);

        if(!headersString.isEmpty()) {
            String[] lines = headersString.split("\n");
            for (String line : lines) {
                StringTokenizer st = new StringTokenizer(line, ":");
                String key = st.nextToken();
                String value = st.nextToken();
                result.putHeader(key, value);
            }
        }

        int bodyLength = byteInput.readInt();
        byte[] body = byteInput.readBytes(bodyLength);
        result.setBody(body);

        return result;
    }
}
