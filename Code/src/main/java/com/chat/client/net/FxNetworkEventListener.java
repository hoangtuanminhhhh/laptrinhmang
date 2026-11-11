package com.chat.client.net;

import com.chat.common.protocol.Packet;
import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Decorator đảm bảo MỌI callback mạng đẩy về tầng UI đều chạy an toàn trên JavaFX Application Thread
 * thông qua {@link Platform#runLater(Runnable)} (Thành viên 3).
 *
 * Cách dùng khuyên nghị trong Controller của TV4:
 * <pre>{@code
 * NetworkService.getInstance().setUiEventListener(this);
 * // hoặc:
 * NetworkService.getInstance().setEventListener(NetworkEventListener.onFxThread(this));
 * }</pre>
 */
public final class FxNetworkEventListener implements NetworkEventListener {

    private final NetworkEventListener delegate;
    private final Executor uiExecutor;

    public FxNetworkEventListener(NetworkEventListener delegate) {
        this(delegate, Platform::runLater);
    }

    public FxNetworkEventListener(NetworkEventListener delegate, Executor uiExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate không được null");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor không được null");
    }

    public NetworkEventListener getDelegate() {
        return delegate;
    }

    private void runOnUi(Runnable task) {
        uiExecutor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("[FxNetworkEventListener] Ngoại lệ khi cập nhật UI: " + e.getMessage());
            }
        });
    }

    @Override
    public void onConnecting(String ip, int port) {
        runOnUi(() -> delegate.onConnecting(ip, port));
    }

    @Override
    public void onConnected() {
        runOnUi(delegate::onConnected);
    }

    @Override
    public void onDisconnected() {
        runOnUi(delegate::onDisconnected);
    }

    @Override
    public void onConnectionLost() {
        runOnUi(delegate::onConnectionLost);
    }

    @Override
    public void onReconnecting(int attempt, long delayMs) {
        runOnUi(() -> delegate.onReconnecting(attempt, delayMs));
    }

    @Override
    public void onReconnected() {
        runOnUi(delegate::onReconnected);
    }

    @Override
    public void onReconnectGaveUp() {
        runOnUi(delegate::onReconnectGaveUp);
    }

    @Override
    public void onPacketReceived(Packet packet) {
        runOnUi(() -> delegate.onPacketReceived(packet));
    }

    @Override
    public void onError(String friendlyMessage, Throwable exception) {
        runOnUi(() -> delegate.onError(friendlyMessage, exception));
    }
}
