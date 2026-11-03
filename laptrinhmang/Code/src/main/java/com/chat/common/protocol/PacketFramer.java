package com.chat.common.protocol;

<<<<<<< HEAD
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
     * Vượt quá kích thước này sẽ bị từ chối để tránh tràn bộ nhớ (OOM).
     */
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024;

    /**
     * Đóng khung và gửi một mảng byte nhị phân ra stream.
     *
     * @param out     OutputStream đích (thường từ Socket)
     * @param payload Dữ liệu byte cần gửi (chuỗi JSON UTF-8)
     * @throws IOException khi lỗi đường truyền hoặc payload vượt quá dung lượng
     */
    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IOException("Payload size (" + payload.length + " bytes) exceeds maximum allowed size (" + MAX_PAYLOAD_SIZE + " bytes)");
        }

        DataOutputStream dos = (out instanceof DataOutputStream) ? (DataOutputStream) out : new DataOutputStream(out);
        dos.writeInt(payload.length); // 4-byte Big-Endian length header
        dos.write(payload);
        dos.flush();
    }

    /**
     * Đọc một khung nhị phân hoàn chỉnh từ InputStream.
     * Sử dụng readFully để đảm bảo đọc đủ số byte được khai báo trong Header.
     *
     * @param in InputStream nguồn (thường từ Socket)
     * @return Mảng byte chứa dữ liệu payload
     * @throws IOException khi ngắt kết nối (EOF) hoặc dữ liệu sai định dạng
     */
    public static byte[] readFrame(InputStream in) throws IOException {
        DataInputStream dis = (in instanceof DataInputStream) ? (DataInputStream) in : new DataInputStream(in);

        int length;
        try {
            length = dis.readInt(); // Đọc 4 byte header
        } catch (EOFException e) {
            throw e; // Kết nối đã đóng từ phía đối tác
        }

        if (length < 0) {
            throw new IOException("Invalid packet length: " + length + " (negative length is not permitted)");
        }
        if (length > MAX_PAYLOAD_SIZE) {
            throw new IOException("Packet length " + length + " bytes exceeds maximum allowed limit " + MAX_PAYLOAD_SIZE + " bytes");
        }

        byte[] payload = new byte[length];
        dis.readFully(payload); // Chờ và đọc đủ chính xác length bytes, giải quyết xé gói
        return payload;
    }

    /**
     * Tiện ích: Đóng gói và gửi một đối tượng Packet ra Stream.
     */
    public static void writePacket(OutputStream out, Packet packet) throws IOException {
        byte[] bytes = packet.toBytes();
        writeFrame(out, bytes);
    }

    /**
     * Tiện ích: Đọc một đối tượng Packet từ Stream.
     */
    public static Packet readPacket(InputStream in) throws Exception {
        byte[] bytes = readFrame(in);
        return Packet.fromBytes(bytes);
    }
}
=======
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PacketFramer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void writePacket(DataOutputStream out, Packet packet) throws IOException {
        String jsonPayload = objectMapper.writeValueAsString(packet.getPayload());
        byte[] payloadBytes = jsonPayload.getBytes("UTF-8");
        int length = payloadBytes.length + 4; // 4 bytes cho PacketType

        out.writeInt(length);
        out.writeInt(packet.getType().ordinal());
        out.write(payloadBytes);
        out.flush();
    }

    public static Packet readPacket(DataInputStream in) throws IOException {
        int length = in.readInt();
        int typeOrdinal = in.readInt();
        PacketType type = PacketType.values()[typeOrdinal];

        int payloadLength = length - 4;
        byte[] payloadBytes = new byte[payloadLength];
        in.readFully(payloadBytes);

        String jsonPayload = new String(payloadBytes, "UTF-8");
        String payload = objectMapper.readValue(jsonPayload, String.class);

        Packet packet = new Packet(type, payload);
        packet.setLength(length);
        return packet;
    }
}
>>>>>>> 8baad60d68f0886283b7ccda1ca4376fd03e9b80
