package com.chat.client;

import com.chat.client.component.AvatarHelper;
import com.chat.client.component.AvatarView;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.Map;

public class ConfigDialogController implements NetworkEventListener {
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button loginModeButton;
    @FXML private Button registerModeButton;
    @FXML private Button actionButton;
    @FXML private Button chooseAvatarButton;
    @FXML private Label statusLabel;
    @FXML private HBox avatarSection;
    @FXML private StackPane avatarPreview;

    private final NetworkService service = NetworkService.getInstance();
    private final NetworkEventListener fxListener = NetworkEventListener.onFxThread(this);
    private final AvatarView preview = new AvatarView();
    private boolean registerMode;
    private String pendingUsername;
    private String pendingPassword;
    private String avatarBase64;

    @FXML public void initialize() {
        ipField.setText(service.getDefaultIp());
        portField.setText(String.valueOf(service.getDefaultPort()));
        preview.setSize(56);
        avatarPreview.getChildren().setAll(preview);
        service.addEventListener(fxListener);
        loginModeButton.setOnAction(event -> setRegisterMode(false));
        registerModeButton.setOnAction(event -> setRegisterMode(true));
        actionButton.setOnAction(event -> submit());
        chooseAvatarButton.setOnAction(event -> chooseAvatar());
        setRegisterMode(false);
    }

    private void setRegisterMode(boolean enabled) {
        registerMode = enabled;
        confirmPasswordField.setVisible(enabled);
        confirmPasswordField.setManaged(enabled);
        avatarSection.setVisible(enabled);
        avatarSection.setManaged(enabled);
        actionButton.setText(enabled ? "Đăng ký & Vào phòng chat" : "Đăng nhập");
        statusLabel.setText("");
    }

    private void submit() {
        String ip = ipField.getText() == null ? "" : ipField.getText().trim();
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText();
        if (ip.isBlank() || username.isBlank() || password == null || password.isBlank()) {
            showStatus("Vui lòng nhập IP, tên đăng nhập và mật khẩu.", true);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            showStatus("Port phải là số từ 1 đến 65535.", true);
            return;
        }
        if (registerMode && !password.equals(confirmPasswordField.getText())) {
            showStatus("Mật khẩu xác nhận không khớp.", true);
            return;
        }
        pendingUsername = username;
        pendingPassword = password;
        actionButton.setDisable(true);
        showStatus("Đang kết nối tới " + ip + ":" + port + "...", false);
        if (service.isConnected()) {
            sendCredentials();
        } else {
            service.connect(ip, port);
        }
    }

    private void sendCredentials() {
        if (pendingUsername == null || pendingPassword == null) return;
        if (registerMode) service.sendRegister(pendingUsername, pendingPassword, avatarBase64);
        else service.sendLogin(pendingUsername, pendingPassword);
        showStatus(registerMode ? "Đang đăng ký tài khoản..." : "Đang xác thực...", false);
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh Avatar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG/JPG", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(actionButton.getScene().getWindow());
        if (file == null) return;
        try {
            avatarBase64 = AvatarHelper.imageToBase64(file, 128, 128);
            preview.setImage(new Image(file.toURI().toString(), 56, 56, true, true));
        } catch (Exception exception) {
            showStatus("Không thể đọc ảnh Avatar: " + exception.getMessage(), true);
        }
    }

    @Override public void onConnected() { sendCredentials(); }
    @Override public void onDisconnected() { }
    @Override public void onPacketReceived(Packet packet) {
        if (packet.getType() == PacketType.REGISTER_RESPONSE) {
            boolean success = Boolean.TRUE.equals(packet.getPayloadValue("success"));
            String message = String.valueOf(packet.getPayloadValue("message"));
            if (success) {
                setRegisterMode(false);
                service.sendLogin(pendingUsername, pendingPassword);
                showStatus("Đăng ký thành công. Đang đăng nhập...", false);
            } else {
                actionButton.setDisable(false);
                showStatus(message, true);
            }
        } else if (packet.getType() == PacketType.LOGIN_RESPONSE) {
            boolean success = Boolean.TRUE.equals(packet.getPayloadValue("success"));
            if (!success) {
                actionButton.setDisable(false);
                showStatus(String.valueOf(packet.getPayloadValue("message")), true);
            } else {
                openChat(packet);
            }
        }
    }

    private void openChat(Packet packet) {
        try {
            String username = String.valueOf(packet.getPayloadValue("username"));
            String avatar = packet.getPayloadValue("avatar") == null ? null : packet.getPayloadValue("avatar").toString();
            Object rawUsers = packet.getPayloadValue("users");
            @SuppressWarnings("unchecked") List<Map<String, Object>> users = rawUsers instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();
            service.setCurrentUsername(username);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainChatView.fxml"));
            Parent root = loader.load();
            MainChatController controller = loader.getController();
            controller.setSession(username, avatar, users);
            Stage chatStage = new Stage();
            chatStage.setTitle("Chat TCP - " + username);
            chatStage.setScene(new Scene(root));
            chatStage.setOnCloseRequest(event -> {
                controller.dispose();
                service.sendLogout();
                service.shutdown();
            });
            chatStage.show();
            service.removeEventListener(fxListener);
            ((Stage) actionButton.getScene().getWindow()).close();
        } catch (Exception exception) {
            actionButton.setDisable(false);
            showStatus("Không thể mở cửa sổ chat: " + exception.getMessage(), true);
        }
    }

    @Override public void onError(String message, Throwable exception) {
        showStatus(message, true);
        actionButton.setDisable(false);
    }
    private void showStatus(String message, boolean error) {
        statusLabel.setText(message == null ? "" : message);
        statusLabel.getStyleClass().removeAll("form-status-error", "form-status-info");
        statusLabel.getStyleClass().add(error ? "form-status-error" : "form-status-info");
    }
}
