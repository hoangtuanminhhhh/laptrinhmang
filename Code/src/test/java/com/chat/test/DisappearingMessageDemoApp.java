package com.chat.test;

import com.chat.client.component.DisappearingMessageTimer;
import com.chat.client.component.MessageCellFactory;
import com.chat.client.component.SoundManager;
import com.chat.common.model.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ứng dụng JavaFX độc lập để minh họa và nghiệm thu tính năng
 * Tin nhắn tự hủy (Disappearing Messages) của Thành viên 6 (Quốc Bảo).
 * <p>
 * Tính năng thể hiện:
 * 1. Đặt thời gian tự hủy (TTL: 3s, 5s, 10s, hoặc không tự hủy).
 * 2. Huy hiệu đếm ngược theo thời gian thực (Countdown Badge) trên bubble tin nhắn.
 * 3. Hiệu ứng mờ dần (FadeTransition) mượt mà khi hết thời gian và tự xóa khỏi danh sách.
 * 4. Phát âm thanh hoặc ghi nhận log tự hủy an toàn.
 * <p>
 * Khởi chạy bằng {@link #main(String[])}.
 */
public class DisappearingMessageDemoApp extends Application {

    private final DisappearingMessageTimer timer = DisappearingMessageTimer.getInstance();
    private final SoundManager soundManager = SoundManager.getInstance();
    private final AtomicInteger messageCounter = new AtomicInteger(100);

    private VBox messageContainer;
    private ScrollPane scrollPane;
    private TextField inputField;
    private ComboBox<String> ttlComboBox;
    private Label statusLabel;
    private Label activeCountLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F8FAFC;");

        root.setTop(createHeader());
        root.setCenter(createMessageArea());
        root.setBottom(createInputBar());

        Scene scene = new Scene(root, 820.0, 620.0);
        var stylesheet = getClass().getResource("/css/style.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setTitle("Demo: Tính năng Tin nhắn tự hủy (Disappearing Messages) - TV6");
        stage.setMinWidth(680.0);
        stage.setMinHeight(500.0);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> timer.shutdown());
        stage.show();

        // Thêm một số tin nhắn mẫu ban đầu
        addSampleMessages();
    }

    private HBox createHeader() {
        Label title = new Label("Phòng chat bảo mật — Tin nhắn tự hủy (TV6)");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        statusLabel = new Label("Sẵn sàng. Gửi tin nhắn kèm thời gian tự hủy để trải nghiệm.");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        VBox titleBox = new VBox(4.0, title, statusLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        activeCountLabel = new Label("Đang đếm ngược: 0");
        activeCountLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #E11D48;"
                + " -fx-background-color: #FFE4E6; -fx-background-radius: 12px; -fx-padding: 4px 10px;");

        Button cancelAllBtn = new Button("Hủy tất cả đếm ngược");
        cancelAllBtn.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-radius: 6px;"
                + " -fx-font-size: 11px; -fx-text-fill: #475569; -fx-cursor: hand;");
        cancelAllBtn.setOnAction(e -> {
            timer.cancelAll();
            updateActiveCount();
            statusLabel.setText("Đã hủy toàn bộ các bộ đếm tự hủy.");
        });

        HBox headerRight = new HBox(8.0, activeCountLabel, cancelAllBtn);
        headerRight.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(titleBox, spacer, headerRight);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14.0, 18.0, 14.0, 18.0));
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #E2E8F0 transparent;");
        return header;
    }

    private VBox createMessageArea() {
        messageContainer = new VBox(12.0);
        messageContainer.setPadding(new Insets(16.0));
        messageContainer.setFillWidth(true);

        scrollPane = new ScrollPane(messageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: #F8FAFC; -fx-background-color: #F8FAFC;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox area = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return area;
    }

    private VBox createInputBar() {
        inputField = new TextField();
        inputField.setPromptText("Nhập tin nhắn cần gửi...");
        inputField.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px;"
                + " -fx-border-color: #CBD5E1; -fx-padding: 9px; -fx-font-size: 13px;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        ttlComboBox = new ComboBox<>();
        ttlComboBox.getItems().addAll(
                "Tự hủy sau 3s (Nhanh)",
                "Tự hủy sau 5s (Tiêu chuẩn)",
                "Tự hủy sau 10s (Đọc kỹ)",
                "Không tự hủy (Vĩnh viễn)"
        );
        ttlComboBox.getSelectionModel().select(1); // Mặc định 5s
        ttlComboBox.setStyle("-fx-font-size: 12px; -fx-background-radius: 8px;");

        Button sendBtn = new Button("Gửi tin");
        sendBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 8px; -fx-padding: 9px 18px; -fx-cursor: hand;");
        sendBtn.setOnAction(e -> handleSendMessage());
        inputField.setOnAction(e -> handleSendMessage());

        HBox inputRow = new HBox(8.0, inputField, ttlComboBox, sendBtn);
        inputRow.setAlignment(Pos.CENTER);

        HBox quickActions = new HBox(8.0);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        Label quickLabel = new Label("Gửi nhanh mẫu:");
        quickLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Button sample3s = createQuickButton("🔥 Mã OTP (Tự hủy 3s)", () -> sendSample("Mã xác thực OTP của bạn là: 894120. Hủy sau 3s!", 3));
        Button sample5s = createQuickButton("⏳ Bí mật nhóm (Tự hủy 5s)", () -> sendSample("Link tài liệu mật: https://internal.corp/docs. Hủy sau 5s!", 5));
        Button sampleNormal = createQuickButton("💬 Tin nhắn thường", () -> sendSample("Chào mọi người, đây là tin nhắn lưu trữ vĩnh viễn.", 0));

        quickActions.getChildren().addAll(quickLabel, sample3s, sample5s, sampleNormal);

        VBox composer = new VBox(8.0, quickActions, inputRow);
        composer.setPadding(new Insets(12.0, 16.0, 14.0, 16.0));
        composer.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0 transparent transparent transparent;");
        return composer;
    }

    private Button createQuickButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #334155; -fx-border-color: #CBD5E1;"
                + " -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-cursor: hand;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private void handleSendMessage() {
        String content = inputField.getText();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        long ttlSeconds = parseSelectedTtl();
        postMessage(content.trim(), true, "Bạn", ttlSeconds);
        inputField.clear();
    }

    private void sendSample(String text, long ttlSeconds) {
        postMessage(text, false, "Người gửi", ttlSeconds);
    }

    private void postMessage(String content, boolean isOwn, String senderName, long ttlSeconds) {
        String msgId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        Message message = new Message();
        message.setId(messageCounter.incrementAndGet());
        message.setMsgId(msgId);
        message.setSenderId(isOwn ? 1 : 2);
        message.setContent(content);

        HBox row = MessageCellFactory.createMessageRow(
                message, isOwn, (action, m) -> {}, senderName, null, null, LocalDateTime.now(), null
        );

        messageContainer.getChildren().add(row);
        scrollToBottom();

        // Nếu có cấu hình tự hủy (TTL > 0), kích hoạt DisappearingMessageTimer
        if (ttlSeconds > 0) {
            timer.attachCountdownBadge(row, msgId, ttlSeconds, messageContainer, () -> {
                updateActiveCount();
                statusLabel.setText("💥 Tin nhắn [" + msgId + "] đã tự hủy an toàn.");
            });
            updateActiveCount();
            statusLabel.setText("⏱️ Tin nhắn [" + msgId + "] đang đếm ngược (" + ttlSeconds + "s)...");
        } else {
            statusLabel.setText("Tin nhắn thường đã gửi (không tự hủy).");
        }

        soundManager.playNotificationSound();
    }

    private long parseSelectedTtl() {
        String selected = ttlComboBox.getSelectionModel().getSelectedItem();
        if (selected == null || selected.contains("Không")) return 0;
        if (selected.contains("3s")) return 3;
        if (selected.contains("5s")) return 5;
        if (selected.contains("10s")) return 10;
        return 5;
    }

    private void addSampleMessages() {
        postMessage("Chào mừng bạn đến với phòng chat bảo mật TCP UDM_08!", false, "Hệ thống", 0);
        postMessage("Thử nghiệm: Tin nhắn này sẽ tự biến mất sau 10 giây.", false, "Server", 10);
    }

    private void updateActiveCount() {
        Platform.runLater(() -> activeCountLabel.setText("Đang đếm ngược: " + timer.getActiveCount()));
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}
