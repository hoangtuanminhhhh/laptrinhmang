package com.chat.client;

import com.chat.client.net.NetworkService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ConfigDialogController {

    @FXML
    private TextField ipField;

    @FXML
    private TextField portField;

    @FXML
    private TextField usernameField;

    @FXML
    private Button connectButton;

    @FXML
    public void initialize() {
        NetworkService service = NetworkService.getInstance();
        if (ipField != null && (ipField.getText() == null || ipField.getText().trim().isEmpty())) {
            ipField.setText(service.getDefaultIp());
        }
        if (portField != null && (portField.getText() == null || portField.getText().trim().isEmpty())) {
            portField.setText(String.valueOf(service.getDefaultPort()));
        }

        connectButton.setOnAction(event -> {
            String ip = ipField.getText().trim();
            String portText = portField.getText().trim();
            String username = usernameField.getText().trim();

            if (ip.isEmpty() || portText.isEmpty() || username.isEmpty()) {
                showWarning("Vui lòng nhập đầy đủ địa chỉ IP, Port và tên hiển thị.");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portText);
                if (port < 1 || port > 65535) {
                    showWarning("Số cổng (Port) phải nằm trong khoảng từ 1 đến 65535.");
                    return;
                }
            } catch (NumberFormatException e) {
                showWarning("Số cổng (Port) không hợp lệ: " + portText);
                return;
            }

            try {
                service.setCurrentUsername(username);

                // Nạp giao diện chat và đăng ký listener TRƯỚC khi bắt đầu connect để không bỏ lỡ sự kiện mạng
                FXMLLoader loader = new FXMLLoader(ConfigDialogController.class.getResource("/MainChatView.fxml"));
                Parent root = loader.load();
                MainChatController chatController = loader.getController();

                Stage chatStage = new Stage();
                chatStage.setScene(new Scene(root));
                chatStage.setTitle("Chat Client - " + username);
                chatStage.setOnCloseRequest(e -> {
                    if (chatController != null) {
                        chatController.dispose();
                    }
                    service.shutdown();
                    Platform.exit();
                });
                chatStage.show();

                Stage currentStage = (Stage) connectButton.getScene().getWindow();
                currentStage.close();

                service.connect(ip, port);
            } catch (Exception e) {
                showWarning("Không thể khởi tạo cửa sổ chat: " + e.getMessage());
            }
        });
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông tin kết nối");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
