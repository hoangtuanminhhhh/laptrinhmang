package com.chat.client.net;

import com.chat.common.protocol.Packet;

/**
 * Interface định nghĩa các sự kiện mạng trả về cho tầng giao diện (UI).
 * Lưu ý cho TV4 (UI): Các hàm callback này được gọi từ Worker Thread,
 * khi cập nhật giao diện JavaFX cần bọc trong Platform.runLater(() -> { ... }).
 */
public interface NetworkEventListener {

    /**
     * Được gọi ngay khi bắt đầu tiến trình kết nối tới Server.
     * Giúp UI chuyển thanh trạng thái sang màu VÀNG (Đang kết nối...).
     *
     * @param ip   Địa chỉ IP đích
     * @param port Cổng đích
     */
    default void onConnecting(String ip, int port) {
        // Mặc định không bắt buộc override nếu UI không dùng tới
    }

    /**
     * Được gọi khi thiết lập kết nối TCP thành công với Server.
     * Giúp UI chuyển thanh trạng thái sang màu XANH (Đã kết nối).
     */
    void onConnected();

    /**
     * Được gọi khi kết nối với Server bị đóng hoặc ngắt đột ngột.
     * Giúp UI chuyển thanh trạng thái sang màu XÁM (Chưa kết nối).
     */
    void onDisconnected();

    /**
     * Được gọi khi nhận được một gói tin hoàn chỉnh từ Server.
     *
     * @param packet Gói tin đã được giải mã qua PacketFramer
     */
    void onPacketReceived(Packet packet);

    /**
     * Được gọi khi xảy ra lỗi mạng (sai IP, Server chưa bật, timeout, rớt mạng...).
     * Giúp UI chuyển thanh trạng thái sang màu ĐỎ (Lỗi kết nối) và hiện thông báo.
     *
     * @param friendlyMessage Thông báo lỗi tiếng Việt thân thiện để hiển thị trực tiếp lên UI
     * @param exception       Ngoại lệ gốc phục vụ ghi log/debug (có thể null)
     */
    void onError(String friendlyMessage, Throwable exception);
}