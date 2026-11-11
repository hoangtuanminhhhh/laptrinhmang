package com.chat.client.net;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Luồng nền (Worker Runnable) đọc Socket liên tục, giải mã Packet và kích hoạt callback (Thành viên 3).
 * Tuyệt đối không chạy trên JavaFX Application Thread.
 *
 * Quy trình xử lý mỗi gói tin:
 *  1. Đọc khung nhị phân 4-byte Length-Prefixed qua PacketFramer.readPacket(inputStream).
 *  2. Nếu JSON của một khung bị hỏng (JsonProcessingException): báo lỗi nhẹ và bỏ qua gói đó,
 *     giữ nguyên kết nối vì khung nhị phân đã được đọc trọn vẹn.
 *  3. Nếu nhận gói PING từ Server -> tự động phản hồi PONG kèm requestId.
 *  4. Chuyển gói tin hợp lệ cho NetworkService để kích hoạt callback đẩy dữ liệu lên UI.
 */
public class ServerListener implements Runnable {

    private final DataInputStream inputStream;
    private final NetworkService networkService;
    private volatile boolean running = true;

    private final AtomicLong packetsReceivedCount = new AtomicLong(0);
    private volatile long lastPacketReceivedAt = 0L;

    public ServerListener(DataInputStream inputStream, NetworkService networkService) {
        this.inputStream = inputStream;
        this.networkService = networkService;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                Packet packet;
                try {
                    // Đọc gói tin nhị phân qua PacketFramer (4-byte length prefix + JSON payload)
                    packet = PacketFramer.readPacket(inputStream);
                } catch (JsonProcessingException e) {
                    // Khung đã đọc trọn vẹn nhưng JSON hỏng -> bỏ qua gói này, giữ nguyên kết nối
                    if (running) {
                        networkService.notifyError("Nhận được gói tin sai định dạng JSON từ máy chủ, đã bỏ qua.", e);
                    }
                    continue;
                }

                if (packet == null || packet.getType() == null) {
                    continue;
                }

                lastPacketReceivedAt = System.currentTimeMillis();
                packetsReceivedCount.incrementAndGet();

                dispatchPacket(packet);
            }
        } catch (EOFException e) {
            if (running) {
                networkService.notifyError("Máy chủ đã đóng kết nối.", e);
            }
        } catch (SocketException e) {
            // Nếu running == false nghĩa là Client chủ động gọi disconnect() -> Không báo lỗi
            if (running) {
                networkService.notifyError("Mất kết nối đột ngột với máy chủ.", e);
            }
        } catch (IOException e) {
            if (running) {
                networkService.notifyError("Lỗi đọc dữ liệu từ máy chủ: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            if (running) {
                networkService.notifyError("Lỗi không xác định khi xử lý gói tin: " + e.getMessage(), e);
            }
        } finally {
            running = false;
            // Đảm bảo chỉ đóng kết nối nếu đây vẫn là listener hiện hành
            networkService.onListenerStopped(this);
        }
    }

    /**
     * Phân loại gói tin nhận được và kích hoạt callback tương ứng.
     * Bọc try-catch để ngoại lệ ở tầng UI không làm sập luồng đọc socket.
     */
    private void dispatchPacket(Packet packet) {
        if (packet.getType() == PacketType.PING) {
            networkService.sendPong(packet.getRequestId());
            return;
        }

        try {
            networkService.notifyPacketReceived(packet);
        } catch (RuntimeException e) {
            System.err.println("[ServerListener] Ngoại lệ trong callback khi xử lý gói "
                    + packet.getType() + ": " + e.getMessage());
        }
    }

    /**
     * Dừng vòng lặp lắng nghe một cách an toàn.
     */
    public void stop() {
        this.running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public long getPacketsReceivedCount() {
        return packetsReceivedCount.get();
    }

    public long getLastPacketReceivedAt() {
        return lastPacketReceivedAt;
    }
}
