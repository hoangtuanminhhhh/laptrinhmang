package com.chat.test;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Bộ kiểm thử tự động (Unit Test / Integration Runner) cho tầng giao thức mạng:
 * Kiểm tra cơ chế đóng/mở gói (Framing) của PacketFramer chống dính gói và xé
 * gói.
 *
 * Thiết kế dựa theo cấu trúc test độc lập có sẵn trong com.chat.test (như
 * DatabaseTest),
 * cho phép chạy trực tiếp qua hàm main() mà không phụ thuộc vào thư viện ngoài.
 *
 * Chịu trách nhiệm: Thành viên 6 — QA & Git Flow & Architecture Documentation.
 */
public class PacketFramerTest {

    private static int passedCount = 0;
    private static int failedCount = 0;

    public static void main(String[] args) {
        System.out.println("======================================================================");
        System.out.println("  BẮT ĐẦU KIỂM THỬ TẦNG GIAO THỨC: com.chat.test.PacketFramerTest    ");
        System.out.println("======================================================================\n");

        runTestCase("TC-F01: Đóng và giải mã gói tin đơn thành công", PacketFramerTest::testPackAndUnpackSinglePacket);
        runTestCase("TC-F02: Bảo toàn chuỗi Tiếng Việt có dấu và Emoji Unicode",
                PacketFramerTest::testVietnameseAccentsAndEmojis);
        runTestCase("TC-F03: Xử lý Dính gói (Sticky Packets) — 3 gói liên tiếp trong 1 stream",
                PacketFramerTest::testStickyPackets);
        runTestCase("TC-F04: Xử lý Xé gói (Fragmented Packets) — Stream cung cấp dữ liệu nhỏ giọt",
                PacketFramerTest::testFragmentedPackets);
        runTestCase("TC-F05: Cơ chế tự vệ khi gặp độ dài âm hoặc dữ liệu cụt (Boundary)",
                PacketFramerTest::testBoundaryLengthProtection);

        System.out.println("\n----------------------------------------------------------------------");
        System.out.println(String.format("TỔNG KẾT KIỂM THỬ: ĐÃ CHẠY %d | THÀNH CÔNG: %d | THẤT BẠI: %d",
                (passedCount + failedCount), passedCount, failedCount));
        System.out.println("KẾT LUẬN: " + (failedCount == 0 ? "TẤT CẢ CÁC CA KIỂM THỬ ĐẠT YÊU CẦU (PASS 100%)"
                : "CÓ CA KIỂM THỬ BỊ LỖI (FAIL)"));
        System.out.println("======================================================================");
    }

    private static void runTestCase(String testName, TestRunnable test) {
        System.out.print(String.format("[CHẠY TEST] %-60s ... ", testName));
        try {
            test.run();
            passedCount++;
            System.out.println("PASS");
        } catch (Throwable t) {
            failedCount++;
            System.out.println("FAIL");
            System.err.println("       -> Lỗi: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    /**
     * TC-F01: Kiểm tra chu trình đóng gói một Packet ra byte stream rồi giải mã
     * ngược lại.
     */
    public static void testPackAndUnpackSinglePacket() throws Exception {
        String testPayload = "Hello Socket TCP!";
        Packet originalPacket = createTextPacket(PacketType.ECHO_REQUEST, testPayload);

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        PacketFramer.writePacket(dataOut, originalPacket);

        byte[] rawBytes = byteOut.toByteArray();
        assertTrue(rawBytes.length > 4, "Khung dữ liệu nhị phân phải chứa ít nhất 4 bytes độ dài");

        ByteArrayInputStream byteIn = new ByteArrayInputStream(rawBytes);
        DataInputStream dataIn = new DataInputStream(byteIn);
        Packet receivedPacket = PacketFramer.readPacket(dataIn);

        assertNotNull(receivedPacket, "Packet nhận được không được null");
        assertEquals(PacketType.ECHO_REQUEST, receivedPacket.getType(), "PacketType phải là ECHO_REQUEST");
        assertEquals(testPayload, receivedPacket.getPayloadValue("message"),
                "Payload nhận được phải trùng khớp với payload gốc");
    }

    /**
     * TC-F02: Kiểm tra việc truyền tải chuỗi tiếng Việt có dấu và Emoji Unicode.
     * Xác minh không bị lệch độ dài byte UTF-8 và không bị lỗi hiển thị.
     */
    public static void testVietnameseAccentsAndEmojis() throws Exception {
        String vietnameseAndEmoji = "Xin chào Việt Nam! 🇻🇳 Chúc mừng năm mới 🎉 😊 🚀";
        Packet originalPacket = createTextPacket(PacketType.ECHO_REQUEST, vietnameseAndEmoji);

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        PacketFramer.writePacket(dataOut, originalPacket);

        ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
        DataInputStream dataIn = new DataInputStream(byteIn);
        Packet receivedPacket = PacketFramer.readPacket(dataIn);

        assertEquals(vietnameseAndEmoji, receivedPacket.getPayloadValue("message"),
                "Chuỗi tiếng Việt có dấu và Emoji Unicode phải được giữ nguyên vẹn 100%");
    }

    /**
     * TC-F03: Ghi liên tục 3 gói tin dính liền nhau trong cùng 1 stream nhị phân
     * (Sticky Packets).
     * Xác minh PacketFramer đọc tuần tự tách bạch đúng 3 gói tin độc lập và bắt
     * EOFException khi hết stream.
     */
    public static void testStickyPackets() throws Exception {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);

        Packet p1 = createTextPacket(PacketType.ECHO_REQUEST, "Gói tin dính 1");
        Packet p2 = createTextPacket(PacketType.ECHO_RESPONSE, "Gói tin dính 2");
        Packet p3 = createTextPacket(PacketType.PING, "Gói tin dính 3");

        PacketFramer.writePacket(dataOut, p1);
        PacketFramer.writePacket(dataOut, p2);
        PacketFramer.writePacket(dataOut, p3);

        ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
        DataInputStream dataIn = new DataInputStream(byteIn);

        Packet r1 = PacketFramer.readPacket(dataIn);
        Packet r2 = PacketFramer.readPacket(dataIn);
        Packet r3 = PacketFramer.readPacket(dataIn);

        assertEquals("Gói tin dính 1", r1.getPayloadValue("message"), "Gói 1 phải đọc đúng nội dung");
        assertEquals(PacketType.ECHO_REQUEST, r1.getType(), "Gói 1 phải đúng type ECHO_REQUEST");

        assertEquals("Gói tin dính 2", r2.getPayloadValue("message"), "Gói 2 phải đọc đúng nội dung");
        assertEquals(PacketType.ECHO_RESPONSE, r2.getType(), "Gói 2 phải đúng type ECHO_RESPONSE");

        assertEquals("Gói tin dính 3", r3.getPayloadValue("message"), "Gói 3 phải đọc đúng nội dung");
        assertEquals(PacketType.PING, r3.getType(), "Gói 3 phải đúng type PING");

        // Sau 3 gói tin, stream đã cạn; đọc tiếp phải ném EOFException
        boolean threwEof = false;
        try {
            PacketFramer.readPacket(dataIn);
        } catch (EOFException e) {
            threwEof = true;
        }
        assertTrue(threwEof, "Phải ném EOFException khi stream cạn dữ liệu");
    }

    /**
     * TC-F04: Xử lý Xé gói (Fragmented Packets) — Stream cung cấp dữ liệu nhỏ giọt
     * từng byte (tối đa 3 bytes/lần).
     * Chứng minh DataInputStream.readFully trong PacketFramer đọc kiên nhẫn và tái
     * tạo hoàn chỉnh payload 100%.
     */
    public static void testFragmentedPackets() throws Exception {
        String longPayload = "Dữ liệu kiểm thử xé gói mạng TCP với nội dung tương đối dài để kiểm chứng readFully hoạt động kiên nhẫn!";
        Packet originalPacket = createTextPacket(PacketType.ECHO_REQUEST, longPayload);

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        PacketFramer.writePacket(dataOut, originalPacket);
        byte[] frameBytes = byteOut.toByteArray();

        // Custom InputStream giả lập hiện tượng xé gói mạng: mỗi lần đọc chỉ trả tối đa
        // 3 bytes
        InputStream slowIn = new InputStream() {
            private int cursor = 0;

            @Override
            public int read() {
                if (cursor >= frameBytes.length) {
                    return -1;
                }
                return frameBytes[cursor++] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (cursor >= frameBytes.length) {
                    return -1;
                }
                int bytesToSupply = Math.min(len, Math.min(3, frameBytes.length - cursor));
                System.arraycopy(frameBytes, cursor, b, off, bytesToSupply);
                cursor += bytesToSupply;
                return bytesToSupply;
            }
        };

        DataInputStream dataSlowIn = new DataInputStream(slowIn);
        Packet receivedPacket = PacketFramer.readPacket(dataSlowIn);

        assertNotNull(receivedPacket, "Packet nhận được qua mạng xé gói không được null");
        assertEquals(longPayload, receivedPacket.getPayloadValue("message"),
                "Payload nhận được qua stream xé gói phải nguyên vẹn 100%");
    }

    /**
     * TC-F05: Kiểm tra cơ chế tự vệ khi gặp độ dài âm hoặc dữ liệu cụt bất thường.
     */
    public static void testBoundaryLengthProtection() throws Exception {
        // Trường hợp 1: Stream có độ dài âm
        ByteArrayOutputStream byteOutNeg = new ByteArrayOutputStream();
        DataOutputStream dataOutNeg = new DataOutputStream(byteOutNeg);
        dataOutNeg.writeInt(-100); // Ghi length = -100
        dataOutNeg.flush();

        ByteArrayInputStream byteInNeg = new ByteArrayInputStream(byteOutNeg.toByteArray());
        DataInputStream dataInNeg = new DataInputStream(byteInNeg);

        boolean threwNegError = false;
        try {
            PacketFramer.readPacket(dataInNeg);
        } catch (Throwable t) {
            threwNegError = true;
        }
        assertTrue(threwNegError, "Phải ném ngoại lệ khi chiều dài gói tin là số âm");

        // Trường hợp 2: Stream bị ngắt cụt giữa chừng (Truncated Stream)
        ByteArrayOutputStream byteOutTrunc = new ByteArrayOutputStream();
        DataOutputStream dataOutTrunc = new DataOutputStream(byteOutTrunc);
        dataOutTrunc.writeInt(500); // Khai báo 500 bytes
        dataOutTrunc.writeInt(PacketType.ECHO_REQUEST.ordinal());
        dataOutTrunc.write(new byte[] { 1, 2, 3, 4 }); // Nhưng chỉ ghi 4 bytes rồi dừng
        dataOutTrunc.flush();

        ByteArrayInputStream byteInTrunc = new ByteArrayInputStream(byteOutTrunc.toByteArray());
        DataInputStream dataInTrunc = new DataInputStream(byteInTrunc);

        boolean threwTruncError = false;
        try {
            PacketFramer.readPacket(dataInTrunc);
        } catch (EOFException e) {
            threwTruncError = true;
        } catch (IOException e) {
            threwTruncError = true;
        }
        assertTrue(threwTruncError, "Phải ném ngoại lệ EOFException khi gói tin bị ngắt cụt giữa chừng");
    }

    // --- Các hàm hỗ trợ Assertion đơn giản (tương tự JUnit Assertions) ---

    private static Packet createTextPacket(PacketType type, String message) {
        return new Packet(type, "CLIENT", "SERVER").addPayload("message", message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Kỳ vọng TRUE nhưng thực tế FALSE: " + message);
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError("Kỳ vọng NOT NULL: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(String.format("Không khớp dữ liệu: %s (Kỳ vọng: [%s], Thực tế: [%s])",
                    message, expected, actual));
        }
    }
}
