package com.chat.client.net;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;

/**
 * Worker Runnable chạy ngầm liên tục để lắng nghe và đọc các Packet từ Server gửi về.
 * Tuyệt đối không chạy trên UI Thread của JavaFX.
 */
public class ServerListener implements Runnable {

    private final DataInputStream inputStream;
    private final NetworkService networkService;
    private volatile boolean running = true;

    public ServerListener(DataInputStream inputStream, NetworkService networkService) {
        this.inputStream = inputStream;
        this.networkService = networkService;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                // Đọc gói tin nhị phân qua PacketFramer (4-byte length prefix + payload)
                Packet packet = PacketFramer.readPacket(inputStream);

                if (packet == null) {
                    // Stream trả về null tức là phía Server đã đóng kết nối
                    break;
                }

                // Nếu Server gửi PING kiểm tra kết nối, tự động phản hồi PONG
                if (packet.getType() == PacketType.PING) {
                    networkService.sendPacket(new Packet(PacketType.PONG, "PONG"));
                    continue;
                }

                // Chuyển gói tin nhận được cho tầng UI xử lý
                networkService.notifyPacketReceived(packet);
            }
        } catch (EOFException e) {
            // Server chủ động đóng kết nối (End of Stream)
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
            // Đảm bảo dọn dẹp tài nguyên Socket và thông báo trạng thái Disconnected cho UI
            networkService.handleConnectionClosed();
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
}