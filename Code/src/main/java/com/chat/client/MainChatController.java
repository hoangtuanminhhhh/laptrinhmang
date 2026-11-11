package com.chat.client;

import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
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

public class MainChatController implements NetworkEventListener {

    @FXML private TextArea chatTextArea;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ListView<String> userListView;

    // Bổ sung các thành phần cho Status Bar ở đáy ứng dụng
    @FXML private Circle statusCircle;
    @FXML private Label statusLabel;

    // Danh sách mẫu người dùng cho Sidebar (có thể cập nhật động sau)
    private final ObservableList<String> userList = FXCollections.observableArrayList(
        "User_A (Online)", 
        "User_B (Offline)", 
        "User_C (Online)"
    );

    @FXML
    public void initialize() {
        // Đăng ký nhận sự kiện mạng từ NetworkService
        NetworkService.getInstance().setEventListener(this);

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

        // Thiết lập trạng thái ban đầu cho Status Bar
        updateStatusIndicator("GREEN", "Đã kết nối tới Server");
    }

    private void handleSendMessage() {
        String message = messageField.getText();
        if (message != null && !message.trim().isEmpty()) {
            // Chuyển đèn sang màu Vàng khi đang gửi tin nhắn
            updateStatusIndicator("YELLOW", "Đang gửi tin nhắn...");

            // Gửi tin nhắn lên Server thông qua NetworkService
            NetworkService.getInstance().sendEchoMessage(message);
            messageField.clear();

            // Sau khi gửi xong, đưa đèn về trạng thái Xanh bình thường
            Platform.runLater(() -> {
                try { Thread.sleep(200); } catch (InterruptedException e) {}
                updateStatusIndicator("GREEN", "Đã kết nối tới Server");
            });
        }
    }

    /**
     * Hàm tiện ích thay đổi màu sắc đèn (Xanh, Vàng, Đỏ) và nội dung trên Status Bar
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

    @Override
    public void onConnecting(String ip, int port) {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Đang kết nối tới " + ip + ":" + port + "...\n");
            updateStatusIndicator("YELLOW", "Đang kết nối tới " + ip + ":" + port + "...");
        });
    }

    @Override
    public void onConnected() {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Kết nối thành công tới Server!\n");
            updateStatusIndicator("GREEN", "Đã kết nối tới Server");
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Hệ thống] Đã ngắt kết nối khỏi Server.\n");
            updateStatusIndicator("RED", "Mất kết nối tới Server. Đang thử kết nối lại...");
        });
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet != null) {
                // Sử dụng getPayload() đúng với cấu trúc lớp Packet của nhóm
                chatTextArea.appendText("Server: " + packet.getPayload() + "\n");
                updateStatusIndicator("GREEN", "Đã nhận phản hồi từ Server");
            }
        });
    }

    @Override
    public void onError(String friendlyMessage, Throwable exception) {
        Platform.runLater(() -> {
            chatTextArea.appendText("[Lỗi] " + friendlyMessage + "\n");
            updateStatusIndicator("RED", "Lỗi kết nối: " + friendlyMessage);
        });
    }
}