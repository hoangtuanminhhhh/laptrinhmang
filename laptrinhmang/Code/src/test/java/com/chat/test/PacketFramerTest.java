package com.chat.test;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ Unit Test tự động kiểm tra cơ chế đóng/mở gói (Framing) của PacketFramer.
 * Chịu trách nhiệm: Thành viên 6 (QA & Testing Lead).
 */
public class PacketFramerTest {

    @Test
    @DisplayName("TC-F01: Đóng và đọc gói tin cơ bản thành công")
    public void testPackAndUnpackSinglePacket() throws Exception {
        Packet packet = Packet.createEchoRequest("tester_alpha", "Hello Socket TCP!");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketFramer.writePacket(out, packet);

        byte[] rawBytes = out.toByteArray();
        assertTrue(rawBytes.length > 4, "Khung dữ liệu phải chứa 4-byte header và payload");

        ByteArrayInputStream in = new ByteArrayInputStream(rawBytes);
        Packet received = PacketFramer.readPacket(in);

        assertNotNull(received);
        assertEquals(PacketType.ECHO_REQUEST, received.getType());
        assertEquals("tester_alpha", received.getSender());
        assertEquals("Hello Socket TCP!", received.getPayloadValue("message"));
    }

    @Test
    @DisplayName("TC-F02: Bảo toàn chuỗi Tiếng Việt đầy đủ dấu và Emoji Unicode")
    public void testVietnameseAccentsAndEmojis() throws Exception {
        String testMessage = "Xin chào Việt Nam! 🇻🇳 Chúc mừng năm mới 🎉 😊 🚀";
        Packet packet = Packet.createEchoRequest("minh_test", testMessage);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketFramer.writePacket(out, packet);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Packet received = PacketFramer.readPacket(in);

        assertEquals(testMessage, received.getPayloadValue("message"));
    }

    @Test
    @DisplayName("TC-F03: Xử lý Dính gói (Sticky Packets) — Nhiều gói liên tiếp trong cùng 1 stream")
    public void testStickyPackets() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Ghi liên tiếp 3 gói tin vào cùng 1 stream nhị phân mà không có khoảng trống
        Packet packet1 = Packet.createEchoRequest("client_1", "Tin nhắn 1");
        Packet packet2 = Packet.createEchoRequest("client_1", "Tin nhắn 2");
        Packet packet3 = Packet.createEchoRequest("client_1", "Tin nhắn 3");

        PacketFramer.writePacket(out, packet1);
        PacketFramer.writePacket(out, packet2);
        PacketFramer.writePacket(out, packet3);

        // Đọc tuần tự từ stream
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        Packet recv1 = PacketFramer.readPacket(in);
        Packet recv2 = PacketFramer.readPacket(in);
        Packet recv3 = PacketFramer.readPacket(in);

        assertEquals("Tin nhắn 1", recv1.getPayloadValue("message"));
        assertEquals("Tin nhắn 2", recv2.getPayloadValue("message"));
        assertEquals("Tin nhắn 3", recv3.getPayloadValue("message"));

        // Khi đọc hết, phải gặp EOFException
        assertThrows(EOFException.class, () -> PacketFramer.readFrame(in));
    }

    @Test
    @DisplayName("TC-F04: Xử lý Xé gói (Fragmented Packets) — Stream cung cấp dữ liệu nhỏ giọt từng byte")
    public void testFragmentedPackets() throws Exception {
        byte[] payload = "{\"type\":\"PING\",\"sender\":\"tester\"}".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketFramer.writeFrame(out, payload);
        byte[] frameBytes = out.toByteArray();

        // Tạo InputStream chậm (Slow/Fragmented InputStream) chỉ trả về tối đa 3 bytes mỗi lần gọi read
        InputStream slowIn = new InputStream() {
            private int index = 0;

            @Override
            public int read() {
                if (index >= frameBytes.length) return -1;
                return frameBytes[index++] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (index >= frameBytes.length) return -1;
                // Giả lập xé gói: Mỗi lần chỉ cấp tối đa 3 bytes
                int bytesToRead = Math.min(len, Math.min(3, frameBytes.length - index));
                System.arraycopy(frameBytes, index, b, off, bytesToRead);
                index += bytesToRead;
                return bytesToRead;
            }
        };

        // readFrame phải đọc đủ nhờ readFully mà không báo lỗi xé gói
        byte[] receivedPayload = PacketFramer.readFrame(slowIn);
        assertArrayEquals(payload, receivedPayload);
    }

    @Test
    @DisplayName("TC-F05: Chặn gói tin có độ dài âm hoặc vượt quá 10MB")
    public void testBoundaryLengthProtection() throws IOException {
        // Test độ dài âm
        ByteArrayOutputStream outNegative = new ByteArrayOutputStream();
        DataOutputStream dosNeg = new DataOutputStream(outNegative);
        dosNeg.writeInt(-100);
        dosNeg.flush();

        ByteArrayInputStream inNeg = new ByteArrayInputStream(outNegative.toByteArray());
        assertThrows(IOException.class, () -> PacketFramer.readFrame(inNeg));

        // Test độ dài vượt quá MAX_PAYLOAD_SIZE (15 MB)
        ByteArrayOutputStream outHuge = new ByteArrayOutputStream();
        DataOutputStream dosHuge = new DataOutputStream(outHuge);
        dosHuge.writeInt(15 * 1024 * 1024);
        dosHuge.flush();

        ByteArrayInputStream inHuge = new ByteArrayInputStream(outHuge.toByteArray());
        assertThrows(IOException.class, () -> PacketFramer.readFrame(inHuge));
    }
}
