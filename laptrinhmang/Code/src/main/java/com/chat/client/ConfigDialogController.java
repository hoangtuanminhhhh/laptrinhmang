package com.chat.client;

import com.chat.client.net.NetworkService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        connectButton.setOnAction(event -> {
            String ip = ipField.getText().trim();
            String portText = portField.getText().trim();
            String username = usernameField.getText().trim();

            if (ip.isEmpty() || portText.isEmpty() || username.isEmpty()) {
                return;
            }

            try {
                int port = Integer.parseInt(portText);
                NetworkService.getInstance().connect(ip, port);

                // Nap dung ten file MainChatView.fxml trong thu muc resources
                FXMLLoader loader = new FXMLLoader(ConfigDialogController.class.getResource("/MainChatView.fxml"));
                Parent root = loader.load();

                // Dong cua so cau hinh hien tai
                Stage currentStage = (Stage) connectButton.getScene().getWindow();
                currentStage.close();

                // Mo cua so giao dien chat chinh
                Stage chatStage = new Stage();
                chatStage.setScene(new Scene(root));
                chatStage.setTitle("Chat Client - " + username);
                chatStage.show();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}