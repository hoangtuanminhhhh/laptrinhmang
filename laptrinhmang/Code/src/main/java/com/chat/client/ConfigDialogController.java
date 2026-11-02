package com.chat.client;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class ConfigDialogController {
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private Button connectButton;

    @FXML
    public void initialize() {
        connectButton.setOnAction(event -> {
            String ip = ipField.getText();
            int port = Integer.parseInt(portField.getText());
            String username = usernameField.getText();
            
            System.out.println("Đang kết nối tới IP: " + ip + ", Port: " + port + " với tên: " + username);
        });
    }
}