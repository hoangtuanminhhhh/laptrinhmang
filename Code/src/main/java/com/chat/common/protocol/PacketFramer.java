package com.chat.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Xử lý đóng gói và bóc tách nhị phân theo cơ chế 4-byte Length-Prefixed Framing.
 * Đảm bảo giải quyết triệt để hiện tượng Dính gói (Sticky) và Xé gói (Fragmented) trong giao thức TCP.
 */
public class PacketFramer {

    /**
     * Kích thước payload tối đa được chấp nhận: 10 MB.
     */
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024;

    /**
     * Đóng khung và gửi một mảng byte nhị phân ra stream.
     */
    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream không được null.");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload không được null.");
        }
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IOException("Kích thước payload " + payload.length
                    + " byte vượt quá giới hạn " + MAX_PAYLOAD_SIZE + " byte.");
        }

        DataOutputStream dos = (out instanceof DataOutputStream) ? (DataOutputStream) out : new DataOutputStream(out);
        dos.writeInt(payload.length); // 4-byte Big-Endian length header
        dos.write(payload);
        dos.flush();
    }

    /**
     * Đọc một khung nhị phân hoàn chỉnh từ InputStream.
     */
    public static byte[] readFrame(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("InputStream không được null.");
        }
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);

        int length;
        try {
            length = dis.readInt(); // Đọc 4 byte header
        } catch (EOFException e) {
            throw e; // Kết nối đã đóng từ phía đối tác
        }

        if (length < 0) {
            throw new IOException("Độ dài khung không hợp lệ: " + length + ".");
        }
        if (length > MAX_PAYLOAD_SIZE) {
            throw new IOException("Độ dài khung " + length
                    + " byte vượt quá giới hạn " + MAX_PAYLOAD_SIZE + " byte.");
        }

        byte[] payload = new byte[length];
        dis.readFully(payload); // Đọc đủ chính xác length bytes, giải quyết xé gói
        return payload;
    }

    /**
     * Đóng gói và gửi một đối tượng Packet ra Stream.
     */
    public static void writePacket(OutputStream out, Packet packet) throws IOException {
        if (packet == null) {
            throw new IllegalArgumentException("Packet không được null.");
        }
        byte[] bytes = packet.toBytes();
        writeFrame(out, bytes);
    }

    /**
     * Đọc một đối tượng Packet từ Stream.
     */
    public static Packet readPacket(InputStream in) throws IOException {
        byte[] bytes = readFrame(in);
        return Packet.fromBytes(bytes);
    }
}
