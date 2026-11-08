package com.chat.client;

import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class MainChatController implements NetworkEventListener {

    @FXML private TextArea chatTextArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ListView<String> userListView;

    @FXML
    public void initialize() {
        // Đăng ký nhận sự kiện mạng từ NetworkService
        NetworkService.getInstance().setEventListener(this);

        // Xử lý sự kiện khi bấm nút Gửi
        sendButton.setOnAction(event -> {
            String message = messageField.getText();
            if (message != null && !message.trim().isEmpty()) {
                // Gửi tin nhắn lên Server thông qua NetworkService
                NetworkService.getInstance().sendEchoMessage(message);
                messageField.clear();
            }
        });
    }

    @Override
    public void onConnecting(String ip, int port) {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Đang kết nối tới " + ip + ":" + port + "...\n");
        });
    }

    @Override
    public void onConnected() {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Kết nối thành công tới Server!\n");
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Đã ngắt kết nối khỏi Server.\n");
        });
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet != null) {
                // Sử dụng getPayload() đúng với cấu trúc lớp Packet của nhóm
                chatTextArea.appendText("Server: " + packet.getPayload() + "\n");
            }
        });
    }

    @Override
    public void onError(String friendlyMessage, Throwable exception) {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Lỗi] " + friendlyMessage + "\n");
        });
    }
}