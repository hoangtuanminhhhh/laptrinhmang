package com.chat.client.net;

import com.chat.common.protocol.Packet;

import java.util.concurrent.Executor;

/**
 * Interface định nghĩa các sự kiện mạng trả về cho tầng giao diện (UI) (Thành viên 3).
 * <p>
 * Thứ tự vòng đời sự kiện khi mất kết nối ngoài ý muốn (Heartbeat timeout hoặc đứt TCP):
 * {@code onConnectionLost() -> onDisconnected() -> onReconnecting(attempt, delayMs)
 * -> (onConnected() + onReconnected()) hoặc onReconnectGaveUp()}.
 * Mỗi sự cố mất kết nối chỉ phát {@code onConnectionLost()} tối đa một lần.
 * <p>
 * Lưu ý cho TV4 (UI): Các hàm callback mặc định được gọi từ Worker Thread,
 * khi cập nhật giao diện JavaFX phải đẩy về JavaFX Application Thread bằng
 * {@code Platform.runLater(() -> { ... })} hoặc đăng ký qua helper {@link #onFxThread(NetworkEventListener)}:
 * <pre>{@code
 * NetworkService.getInstance().setUiEventListener(this);
 * // hoặc:
 * NetworkService.getInstance().setEventListener(NetworkEventListener.onFxThread(this));
 * }</pre>
 */
public interface NetworkEventListener {

    /**
     * Bọc một listener để toàn bộ callback của nó được tự động chuyển sang JavaFX Application Thread
     * thông qua {@code Platform.runLater(...)} mà vẫn giữ đúng thứ tự FIFO.
     *
     * @param listener listener gốc cần chạy trên UI Thread
     * @return listener an toàn luồng (không bọc lặp nếu đã là {@link FxNetworkEventListener})
     */
    static NetworkEventListener onFxThread(NetworkEventListener listener) {
        if (listener == null || listener instanceof FxNetworkEventListener) {
            return listener;
        }
        return new FxNetworkEventListener(listener);
    }

    /**
     * Bọc một listener để mọi callback của nó chạy tuần tự trên {@code uiExecutor} chỉ định.
     *
     * @param listener   listener gốc cần chuyển luồng
     * @param uiExecutor executor đích (ví dụ {@code Platform::runLater} hoặc single-thread executor khi test)
     * @return listener đã bọc, hoặc {@code null} nếu {@code listener == null}
     */
    static NetworkEventListener onExecutor(NetworkEventListener listener, Executor uiExecutor) {
        if (listener == null) {
            return null;
        }
        return new FxNetworkEventListener(listener, uiExecutor);
    }

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
     * Được gọi tối đa một lần cho mỗi sự cố khi phát hiện mất kết nối ngoài ý muốn
     * (Heartbeat quá chu kỳ không nhận được PONG hoặc socket TCP bị ngắt bất thường).
     * Giúp UI chuyển thanh trạng thái sang màu ĐỎ: "Mất kết nối tới Server. Đang thử kết nối lại...".
     */
    default void onConnectionLost() {
        // Mặc định không bắt buộc override
    }

    /**
     * Được gọi ngay trước khi lên lịch mỗi lần thử tự động kết nối lại (Auto-reconnect),
     * đảm bảo luôn phát trước {@link #onConnected()} / {@link #onReconnected()} ngay cả khi retry ngay ({@code delayMs == 0}).
     *
     * @param attempt Thứ tự lần thử kết nối lại (bắt đầu từ 1)
     * @param delayMs Thời gian chờ trước lần thử này (ms, tăng dần theo exponential backoff có jitter)
     */
    default void onReconnecting(int attempt, long delayMs) {
        // Mặc định không bắt buộc override
    }

    /**
     * Được gọi ngay sau khi tiến trình Auto-reconnect khôi phục kết nối TCP thành công với Server
     * (trước khi tự động gửi lại {@code LOGIN_REQUEST} nếu có phiên đăng nhập trước đó).
     */
    default void onReconnected() {
        // Mặc định không bắt buộc override
    }

    /**
     * Được gọi khi đã thử kết nối lại tối đa {@code maxAttempts} lần mà Server vẫn không phản hồi.
     * Lúc này tiến trình tự động kết nối lại tạm dừng; UI có thể cho phép người dùng bấm thử lại thủ công.
     */
    default void onReconnectGaveUp() {
        // Mặc định không bắt buộc override
    }

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