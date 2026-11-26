package com.chat.test;

import com.chat.client.component.MessageCellFactory;
import com.chat.client.component.SoundManager;
import com.chat.common.model.Message;
import javafx.animation.Interpolator;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ứng dụng JavaFX độc lập để nghiệm thu âm thanh, cuộn tin nhắn và chống tràn
 * chuỗi dài trong bong bóng chat. Chạy bằng {@link #main(String[])}.
 */
public final class SoundAndPolishDemoApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(SoundAndPolishDemoApp.class.getName());
    private static final int DEMO_USER_ID = 1;

    private final SoundManager soundManager = SoundManager.getInstance();
    private VBox messageList;
    private ScrollPane messageScrollPane;
    private Button muteButton;
    private Label statusLabel;

    /** Khởi chạy cửa sổ demo JavaFX. */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ensureNotificationSoundExists();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F1F5F9;");
        root.setTop(createHeader());
        root.setCenter(createConversation());
        root.setBottom(createControls());

        Scene scene = new Scene(root, 800.0, 600.0);
        var stylesheet = getClass().getResource("/css/style.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setTitle("Chat TCP — Demo âm thanh & giao diện");
        stage.setMinWidth(640.0);
        stage.setMinHeight(480.0);
        stage.setScene(scene);
        stage.show();
    }

    private HBox createHeader() {
        Label title = new Label("Phòng chat");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label subtitle = new Label("Âm thanh thông báo và giao diện chống tràn chữ");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        VBox titleBox = new VBox(4.0, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        muteButton = new Button();
        muteButton.getStyleClass().add("mute-button");
        muteButton.setFocusTraversable(false);
        muteButton.setOnAction(event -> {
            soundManager.setMuted(!soundManager.isMuted());
            updateMuteButton();
            updateStatus(soundManager.isMuted()
                    ? "Đã tắt âm thanh thông báo"
                    : "Đã bật âm thanh thông báo");
        });
        updateMuteButton();

        HBox header = new HBox(12.0, titleBox, spacer, muteButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14.0, 18.0, 14.0, 18.0));
        header.setStyle("-fx-background-color: #FFFFFF;"
                + " -fx-border-color: transparent transparent #E2E8F0 transparent;");
        return header;
    }

    private VBox createConversation() {
        messageList = new VBox(14.0);
        messageList.setPadding(new Insets(18.0));
        messageList.setFillWidth(true);

        addMessage("Xin chào! Đây là khung chat minh họa.", false, 2);
        addMessage("Bấm nút bên dưới để thử thông báo và chuỗi URL rất dài.", true, DEMO_USER_ID);

        messageScrollPane = new ScrollPane(messageList);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScrollPane.setStyle("-fx-background: #F8FAFC; -fx-background-color: #F8FAFC;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        VBox conversation = new VBox(messageScrollPane);
        conversation.setPadding(new Insets(0.0, 12.0, 0.0, 12.0));
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);
        return conversation;
    }

    private VBox createControls() {
        Button incomingButton = new Button("Mô phỏng tin nhắn người khác gửi đến");
        incomingButton.getStyleClass().add("demo-button");
        incomingButton.setOnAction(event -> simulateIncomingMessage());

        Button longMessageButton = new Button("Mô phỏng gửi tin nhắn dài không dấu cách (Test tràn chữ)");
        longMessageButton.getStyleClass().add("demo-button");
        longMessageButton.setOnAction(event -> simulateLongMessage());

        HBox buttonRow = new HBox(10.0, incomingButton, longMessageButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Sẵn sàng — tiếng chuông chỉ phát với tin nhắn đến");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        VBox controls = new VBox(9.0, buttonRow, statusLabel);
        controls.setPadding(new Insets(12.0, 18.0, 14.0, 18.0));
        controls.setStyle("-fx-background-color: #FFFFFF;"
                + " -fx-border-color: #E2E8F0 transparent transparent transparent;");
        return controls;
    }

    private void simulateIncomingMessage() {
        addMessage("Ting! Đây là tin nhắn mô phỏng từ người khác.", false, 2);
        soundManager.playNotificationSound();
        updateStatus(soundManager.isMuted()
                ? "Đã nhận tin nhắn (âm thanh đang tắt)"
                : "Đã nhận tin nhắn và phát âm thanh thông báo");
        scrollToBottomSmoothly();
    }

    private void simulateLongMessage() {
        String longUrl = "https://example.com/very/long/url/test_wrap_text_without_spaces_123456789"
                + "0123456789".repeat(32);
        addMessage(longUrl, true, DEMO_USER_ID);
        updateStatus("Đã thêm chuỗi URL dài không có dấu cách để kiểm tra wrap");
        scrollToBottomSmoothly();
    }

    private void addMessage(String content, boolean own, int senderId) {
        Message message = new Message();
        message.setMsgId(UUID.randomUUID().toString());
        message.setSenderId(senderId);
        message.setContent(content);
        message.setForwarded(false);
        messageList.getChildren().add(MessageCellFactory.createMessageRow(
                message,
                own,
                (action, selectedMessage) -> updateStatus("Thao tác tin nhắn: " + action),
                own ? "Bạn" : "Thành viên " + senderId,
                null,
                null,
                LocalDateTime.now(),
                null));
    }

    private void scrollToBottomSmoothly() {
        Platform.runLater(() -> {
            messageScrollPane.applyCss();
            messageScrollPane.layout();
            Timeline scroll = new Timeline(new javafx.animation.KeyFrame(
                    Duration.millis(260.0),
                    new KeyValue(messageScrollPane.vvalueProperty(), 1.0, Interpolator.EASE_BOTH)));
            scroll.play();
        });
    }

    private void updateMuteButton() {
        boolean muted = soundManager.isMuted();
        muteButton.setText(muted ? "🔇" : "🔊");
        muteButton.setAccessibleText(muted
                ? "Bật âm thanh thông báo"
                : "Tắt âm thanh thông báo");
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Tạo WAV PCM đơn âm 800 Hz tại thư mục resource Maven nếu tệp chưa tồn tại.
     * Nếu chạy ứng dụng từ thư mục gốc repository, tự tìm thư mục con {@code Code}.
     */
    private static void ensureNotificationSoundExists() {
        Path resourceDirectory = Path.of("src", "main", "resources");
        Path repositoryResourceDirectory = Path.of("Code", "src", "main", "resources");
        if (!Files.isDirectory(resourceDirectory) && Files.isDirectory(repositoryResourceDirectory)) {
            resourceDirectory = repositoryResourceDirectory;
        }
        Path soundFile = resourceDirectory.resolve("sounds").resolve("notification.wav");
        if (Files.exists(soundFile)) {
            return;
        }

        try {
            Files.createDirectories(soundFile.getParent());
            Files.write(soundFile, createNotificationWave());
            LOGGER.info(() -> "Đã tạo âm thanh thông báo demo tại " + soundFile.toAbsolutePath());
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Không thể tạo WAV demo tại " + soundFile.toAbsolutePath(), exception);
        }
    }

    private static byte[] createNotificationWave() {
        final int sampleRate = 22_050;
        final double durationSeconds = 0.32;
        final int sampleCount = (int) (sampleRate * durationSeconds);
        final int dataSize = sampleCount * Short.BYTES;
        ByteBuffer wave = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);

        wave.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        wave.putInt(36 + dataSize);
        wave.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        wave.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        wave.putInt(16);
        wave.putShort((short) 1);
        wave.putShort((short) 1);
        wave.putInt(sampleRate);
        wave.putInt(sampleRate * Short.BYTES);
        wave.putShort((short) Short.BYTES);
        wave.putShort((short) 16);
        wave.put("data".getBytes(StandardCharsets.US_ASCII));
        wave.putInt(dataSize);

        for (int sample = 0; sample < sampleCount; sample++) {
            double elapsed = (double) sample / sampleRate;
            double attack = Math.min(1.0, elapsed / 0.015);
            double release = Math.min(1.0, (durationSeconds - elapsed) / 0.08);
            double envelope = Math.max(0.0, Math.min(attack, release));
            double tone = Math.sin(2.0 * Math.PI * 800.0 * elapsed);
            wave.putShort((short) (tone * envelope * Short.MAX_VALUE * 0.24));
        }
        return wave.array();
    }
}
