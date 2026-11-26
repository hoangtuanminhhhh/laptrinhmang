package com.chat.client;

import com.chat.client.component.SoundManager;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class MainChatController implements NetworkEventListener {

    @FXML private TextArea chatTextArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private Button muteButton;
    @FXML private ListView<String> userListView;

    // Thành phần Status Bar ở đáy ứng dụng
    @FXML private Circle statusCircle;
    @FXML private Label statusLabel;
    @FXML private Button reconnectButton;

    // Danh sách mẫu người dùng cho Sidebar (có thể cập nhật động sau)
    private final ObservableList<String> userList = FXCollections.observableArrayList(
        "User_A (Online)",
        "User_B (Offline)",
        "User_C (Online)"
    );

    private Timeline backoffCountdownTimeline;
    private PauseTransition sendStatusTransition;
    private boolean reconnectingCycle = false;
    private long countdownRemainingSec = 0L;

    @FXML
    public void initialize() {
        // Đăng ký nhận sự kiện mạng tự động chuyển về JavaFX Application Thread qua FxNetworkEventListener
        NetworkService.getInstance().setUiEventListener(this);

        updateMuteButton();
        if (muteButton != null) {
            muteButton.setOnAction(event -> {
                SoundManager soundManager = SoundManager.getInstance();
                soundManager.setMuted(!soundManager.isMuted());
                updateMuteButton();
            });
        }

        // Cấu hình hiển thị danh sách người dùng bên Sidebar với chấm trạng thái Xanh/Xám
        userListView.setItems(userList);
        userListView.setCellFactory(param -> new ListCell<String>() {
            private final Circle cellCircle = new Circle(5);
            private final Label cellLabel = new Label();
            private final HBox cellContainer = new HBox(10, cellCircle, cellLabel);

            @Override
            protected void updateItem(String user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    cellLabel.setText(user);
                    if (user.contains("Online")) {
                        cellCircle.setFill(Color.GREEN);
                    } else {
                        cellCircle.setFill(Color.GRAY);
                    }
                    setGraphic(cellContainer);
                }
            }
        });

        // Xử lý sự kiện click chọn user trong danh sách
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                System.out.println("Đang mở cuộc trò chuyện với: " + newValue);
            }
        });

        // Xử lý sự kiện khi bấm nút Gửi hoặc nhấn Enter
        sendButton.setOnAction(event -> handleSendMessage());
        messageField.setOnAction(event -> handleSendMessage());
        if (reconnectButton != null) {
            reconnectButton.setOnAction(event -> handleManualReconnect());
        }

        // Đồng bộ trạng thái kết nối thực tế thay vì hardcode
        syncInitialConnectionState();
    }

    private void syncInitialConnectionState() {
        NetworkService service = NetworkService.getInstance();
        if (service.isConnected()) {
            setMessagingEnabled(true);
            setReconnectButtonVisible(false, false);
            updateStatusIndicator("GREEN", formatConnectedStatus());
        } else if (service.isConnecting()) {
            setMessagingEnabled(false);
            setReconnectButtonVisible(true, true);
            updateStatusIndicator("YELLOW", "Đang kết nối tới " + formatEndpoint() + "...");
        } else {
            setMessagingEnabled(false);
            setReconnectButtonVisible(false, false);
            updateStatusIndicator("GRAY", "Chưa kết nối tới Server");
        }
    }

    private void handleSendMessage() {
        if (messageField == null) {
            return;
        }
        String raw = messageField.getText();
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        NetworkService service = NetworkService.getInstance();
        if (!service.isConnected()) {
            updateStatusIndicator("RED", "Chưa kết nối tới Server. Đang chờ kết nối lại...");
            setReconnectButtonVisible(true, service.isConnecting());
            return;
        }

        updateStatusIndicator("YELLOW", "Đang gửi tin nhắn...");
        service.sendEchoMessage(trimmed);
        messageField.clear();

        if (sendStatusTransition != null) {
            sendStatusTransition.stop();
        }
        sendStatusTransition = new PauseTransition(Duration.millis(180));
        sendStatusTransition.setOnFinished(e -> {
            if (service.isConnected()) {
                updateStatusIndicator("GREEN", formatConnectedStatus());
            }
        });
        sendStatusTransition.playFromStart();
    }

    private void handleManualReconnect() {
        NetworkService service = NetworkService.getInstance();
        if (service.retryReconnectNow() && !service.isConnected()) {
            setReconnectButtonVisible(true, true);
            updateStatusIndicator("YELLOW", "Đang thử kết nối lại ngay...");
        }
    }

    /**
     * Hàm tiện ích thay đổi màu sắc đèn (Xanh, Vàng, Đỏ, Xám) và nội dung trên Status Bar.
     */
    private void updateStatusIndicator(String colorCode, String message) {
        if (statusCircle != null && statusLabel != null) {
            switch (colorCode.toUpperCase()) {
                case "GREEN":
                    statusCircle.setFill(Color.GREEN);
                    break;
                case "YELLOW":
                    statusCircle.setFill(Color.GOLD);
                    break;
                case "RED":
                    statusCircle.setFill(Color.RED);
                    break;
                default:
                    statusCircle.setFill(Color.GRAY);
            }
            statusLabel.setText(message);
        }
    }

    private void setMessagingEnabled(boolean enabled) {
        if (sendButton != null) {
            sendButton.setDisable(!enabled);
        }
        if (messageField != null) {
            messageField.setDisable(!enabled);
            messageField.setPromptText(enabled ? "Nhập tin nhắn..." : "Đang mất kết nối, vui lòng chờ...");
            if (enabled) {
                messageField.requestFocus();
            }
        }
    }

    private void setReconnectButtonVisible(boolean visible, boolean disable) {
        if (reconnectButton != null) {
            reconnectButton.setVisible(visible);
            reconnectButton.setManaged(visible);
            reconnectButton.setDisable(disable);
        }
    }

    private String formatEndpoint() {
        NetworkService service = NetworkService.getInstance();
        String host = service.getLastIp() != null ? service.getLastIp() : service.getDefaultIp();
        int port = service.getLastPort() > 0 ? service.getLastPort() : service.getDefaultPort();
        return host + ":" + port;
    }

    private String formatConnectedStatus() {
        return "Đã kết nối tới Server [" + formatEndpoint() + "]";
    }

    private void startBackoffCountdown(int attempt, long initialSec) {
        stopBackoffCountdown();
        countdownRemainingSec = initialSec;
        updateStatusIndicator("RED", "Mất kết nối. Tự thử lại lần #" + attempt + " sau " + countdownRemainingSec + "s...");

        backoffCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdownRemainingSec--;
            if (countdownRemainingSec > 0) {
                updateStatusIndicator("RED", "Mất kết nối. Tự thử lại lần #" + attempt + " sau " + countdownRemainingSec + "s...");
            } else {
                stopBackoffCountdown();
                setReconnectButtonVisible(true, true);
                updateStatusIndicator("YELLOW", "Đang thử kết nối lại tới " + formatEndpoint() + "... (Lần #" + attempt + ")");
            }
        }));
        backoffCountdownTimeline.setCycleCount((int) Math.max(1L, initialSec));
        backoffCountdownTimeline.playFromStart();
    }

    private void stopBackoffCountdown() {
        if (backoffCountdownTimeline != null) {
            backoffCountdownTimeline.stop();
            backoffCountdownTimeline = null;
        }
    }

    private void appendChatSystemLine(String text) {
        if (chatTextArea != null) {
            chatTextArea.appendText(text);
            chatTextArea.positionCaret(chatTextArea.getLength());
        }
    }

    private void runOnFxIfNeeded(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    @Override
    public void onConnecting(String ip, int port) {
        runOnFxIfNeeded(() -> {
            stopBackoffCountdown();
            setMessagingEnabled(false);
            setReconnectButtonVisible(reconnectingCycle, true);
            appendChatSystemLine("[Hệ thống] Đang kết nối tới " + ip + ":" + port + "...\n");
            updateStatusIndicator("YELLOW", "Đang kết nối tới " + ip + ":" + port + "...");
        });
    }

    @Override
    public void onConnected() {
        runOnFxIfNeeded(() -> {
            stopBackoffCountdown();
            setMessagingEnabled(true);
            setReconnectButtonVisible(false, false);
            if (!reconnectingCycle) {
                appendChatSystemLine("[Hệ thống] Kết nối thành công tới Server (" + formatEndpoint() + ")!\n");
            }
            updateStatusIndicator("GREEN", formatConnectedStatus());
        });
    }

    @Override
    public void onConnectionLost() {
        runOnFxIfNeeded(() -> {
            reconnectingCycle = true;
            stopBackoffCountdown();
            setMessagingEnabled(false);
            setReconnectButtonVisible(true, false);
            appendChatSystemLine("[Hệ thống] Mất kết nối tới Server. Đang kích hoạt tự động kết nối lại...\n");
            updateStatusIndicator("RED", "Mất kết nối tới Server. Đang chuẩn bị kết nối lại...");
        });
    }

    @Override
    public void onDisconnected() {
        runOnFxIfNeeded(() -> {
            setMessagingEnabled(false);
            if (!NetworkService.getInstance().isNetworkLost()) {
                reconnectingCycle = false;
                stopBackoffCountdown();
                setReconnectButtonVisible(true, false);
                appendChatSystemLine("[Hệ thống] Đã ngắt kết nối khỏi Server.\n");
                updateStatusIndicator("GRAY", "Đã ngắt kết nối khỏi Server");
            }
        });
    }

    @Override
    public void onReconnecting(int attempt, long delayMs) {
        runOnFxIfNeeded(() -> {
            reconnectingCycle = true;
            setMessagingEnabled(false);
            if (delayMs <= 0L) {
                stopBackoffCountdown();
                setReconnectButtonVisible(true, true);
                appendChatSystemLine("[Hệ thống] Đang thử kết nối lại ngay (Lần #" + attempt + ")...\n");
                updateStatusIndicator("YELLOW", "Đang thử kết nối lại ngay... (Lần #" + attempt + ")");
            } else {
                setReconnectButtonVisible(true, false);
                long initialSec = Math.max(1L, Math.round(delayMs / 1000.0));
                appendChatSystemLine("[Hệ thống] Tự động kết nối lại lần #" + attempt + " sau " + initialSec + "s...\n");
                startBackoffCountdown(attempt, initialSec);
            }
        });
    }

    @Override
    public void onReconnected() {
        runOnFxIfNeeded(() -> {
            reconnectingCycle = false;
            stopBackoffCountdown();
            setMessagingEnabled(true);
            setReconnectButtonVisible(false, false);
            appendChatSystemLine("[Hệ thống] Đã khôi phục kết nối tới Server thành công!\n");
            updateStatusIndicator("GREEN", "Đã kết nối lại tới Server [" + formatEndpoint() + "]");
        });
    }

    @Override
    public void onReconnectGaveUp() {
        runOnFxIfNeeded(() -> {
            stopBackoffCountdown();
            setMessagingEnabled(false);
            setReconnectButtonVisible(true, false);
            appendChatSystemLine("[Hệ thống] Đã thử kết nối lại tối đa số lần cho phép. Bấm 'Kết nối lại ngay' để thử lại.\n");
            updateStatusIndicator("RED", "Không thể tự kết nối lại. Bấm 'Kết nối lại ngay' để thử lại.");
        });
    }

    @Override
    public void onPacketReceived(Packet packet) {
        runOnFxIfNeeded(() -> {
            if (packet != null) {
                appendChatSystemLine("Server: " + packet.getPayload() + "\n");
                if (isIncomingMessage(packet)) {
                    SoundManager.getInstance().playNotificationSound();
                }
                updateStatusIndicator("GREEN", "Đã nhận phản hồi từ Server [" + formatEndpoint() + "]");
            }
        });
    }

    private boolean isIncomingMessage(Packet packet) {
        PacketType type = packet.getType();
        if (type != PacketType.MESSAGE_RECEIVE
                && type != PacketType.REPLY_RECEIVE
                && type != PacketType.FORWARD_RECEIVE) {
            return false;
        }
        String sender = packet.getSender();
        String currentUser = NetworkService.getInstance().getCurrentUsername();
        return sender == null || currentUser == null || !sender.equalsIgnoreCase(currentUser);
    }

    private void updateMuteButton() {
        if (muteButton == null) {
            return;
        }
        boolean muted = SoundManager.getInstance().isMuted();
        muteButton.setText(muted ? "🔇" : "🔊");
        muteButton.setAccessibleText(muted
                ? "Bật âm thanh thông báo"
                : "Tắt âm thanh thông báo");
    }

    @Override
    public void onError(String friendlyMessage, Throwable exception) {
        runOnFxIfNeeded(() -> {
            appendChatSystemLine("[Lỗi] " + friendlyMessage + "\n");
            NetworkService service = NetworkService.getInstance();
            if (!service.isConnected()) {
                setMessagingEnabled(false);
                setReconnectButtonVisible(true, service.isConnecting());
                if (!service.isReconnecting()) {
                    updateStatusIndicator("RED", "Lỗi kết nối: " + friendlyMessage);
                }
            }
        });
    }

    /**
     * Giải phóng các timer giao diện và hủy đăng ký listener khi đóng cửa sổ chat.
     */
    public void dispose() {
        stopBackoffCountdown();
        if (sendStatusTransition != null) {
            sendStatusTransition.stop();
            sendStatusTransition = null;
        }
        NetworkService.getInstance().removeEventListener(this);
    }
}
