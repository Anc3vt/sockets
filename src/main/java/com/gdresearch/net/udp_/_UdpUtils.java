package com.gdresearch.net.udp_;

import com.ancevt.commons.io.ByteInput;
import com.ancevt.commons.io.ByteOutput;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

class _UdpUtils {

    private static long idCounter = 1;

    public static byte[] addLeadingByte(byte[] src, byte b) {
        byte[] result = new byte[src.length + 1];
        System.arraycopy(src, 0, result, 1, src.length);
        result[0] = b;
        return result;
    }

    public static byte[] removeLeadingByte(byte[] src) {
        byte[] result = new byte[src.length - 1];
        System.arraycopy(src, 1, result, 0, result.length);
        return result;
    }

    public static byte[] createByteMessage(int type, long id, byte[] payload) {
        ByteOutput byteOutput = ByteOutput.newInstance(1 + Long.BYTES + payload.length);
        byteOutput.writeByte(type);

        if (type != 0) {
            byteOutput.writeLong(id);
        }
        byteOutput.write(payload, 0, payload.length);
        return byteOutput.toArray();
    }

    public static Message readMessage(byte[] bytes) {
        ByteInput byteInput = ByteInput.newInstance(bytes);

        int type = byteInput.readUnsignedByte();
        long id = 0;

        if (type > 0) {
            id = byteInput.readLong();
        }
        return null;

    }

    public static void main(String[] args) {

        System.out.println(Arrays.toString(addLeadingByte(new byte[]{1, 2, 3}, (byte) 0)));

        System.out.println(Arrays.toString(removeLeadingByte(new byte[]{0, 1, 2, 3})));

    }

    @RequiredArgsConstructor
    public static class Message {
        private final int type;
        private final long id;
        private final byte[] payload;
    }

}
