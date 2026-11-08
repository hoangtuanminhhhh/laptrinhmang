package com.chat.test;

import com.chat.client.component.AvatarView;
import com.chat.client.component.EmojiPickerPopup;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Ứng dụng JavaFX độc lập để thử avatar hình tròn và chèn emoji vào khung soạn
 * tin nhắn mô phỏng.
 */
public class AvatarEmojiDemoApp extends Application {

    private static final String CURRENT_USER = "You";

    private final AvatarView[] photoAvatars = new AvatarView[3];
    private VBox messages;
    private ScrollPane messageScrollPane;
    private Label selectionStatus;

    /** Khởi chạy màn hình minh họa avatar và bộ chọn emoji. */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Image sampleImage = loadDefaultAvatar();
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F1F5F9;");
        root.setTop(createHeader());
        root.setLeft(createAvatarPanel(sampleImage, stage));
        root.setCenter(createChatPanel(sampleImage));

        Scene scene = new Scene(root, 800.0, 600.0);
        stage.setTitle("Minh họa Avatar và Emoji");
        stage.setMinWidth(720.0);
        stage.setMinHeight(520.0);
        stage.setScene(scene);
        stage.show();
    }

    private HBox createHeader() {
        Label title = new Label("Thành phần trò chuyện");
        title.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        Label subtitle = new Label("Xem trước avatar và thao tác nhập emoji");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        VBox titles = new VBox(3.0, title, subtitle);
        HBox header = new HBox(titles);
        header.setPadding(new Insets(18.0, 22.0, 16.0, 22.0));
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #E2E8F0 transparent;");
        return header;
    }

    private VBox createAvatarPanel(Image sampleImage, Stage stage) {
        Label title = new Label("Xem trước avatar");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        Label description = new Label("Ảnh được cắt tròn và avatar chữ cái");
        description.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        VBox sizeRows = new VBox(18.0);
        String[] names = {"Nguyễn Văn A", "Trần B", "Dev"};
        double[] sizes = {32.0, 48.0, 64.0};
        for (int index = 0; index < sizes.length; index++) {
            AvatarView imageAvatar = new AvatarView(sampleImage, names[index], sizes[index]);
            AvatarView fallbackAvatar = new AvatarView((Image) null, names[index], sizes[index]);
            photoAvatars[index] = imageAvatar;

            Label sizeLabel = new Label((int) sizes[index] + " px");
            sizeLabel.setMinWidth(42.0);
            sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            VBox imageColumn = avatarColumn("Có ảnh", imageAvatar);
            VBox fallbackColumn = avatarColumn("Chữ cái", fallbackAvatar);
            HBox row = new HBox(13.0, sizeLabel, imageColumn, fallbackColumn);
            row.setAlignment(Pos.CENTER_LEFT);
            sizeRows.getChildren().add(row);
        }

        Button chooseImageButton = new Button("Chọn ảnh từ máy");
        chooseImageButton.setMaxWidth(Double.MAX_VALUE);
        chooseImageButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white;"
                + " -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 10px;");
        chooseImageButton.setOnAction(event -> chooseImage(stage));

        selectionStatus = new Label("Ảnh mẫu được đóng gói cùng ứng dụng");
        selectionStatus.setWrapText(true);
        selectionStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");

        VBox panel = new VBox(14.0, title, description, new Separator(), sizeRows,
                chooseImageButton, selectionStatus);
        panel.setPadding(new Insets(20.0, 18.0, 20.0, 20.0));
        panel.setPrefWidth(300.0);
        panel.setMinWidth(280.0);
        panel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent #E2E8F0 transparent transparent;");
        return panel;
    }

    private VBox createChatPanel(Image sampleImage) {
        Label title = new Label("Khung trò chuyện mẫu");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        messages = new VBox(14.0);
        messages.setPadding(new Insets(16.0));
        messages.setFillWidth(true);
        messages.getChildren().addAll(
                createMessage("Nguyễn Văn A", "Chào mọi người! 👋", sampleImage, false),
                createMessage("Trần B", "Giao diện avatar tròn trông ổn đấy 😎", null, false),
                createMessage("Dev", "Thử chọn emoji ở nút bên dưới nhé 🎉🚀", null, false)
        );

        messageScrollPane = new ScrollPane(messages);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScrollPane.setStyle("-fx-background: #F8FAFC; -fx-background-color: #F8FAFC;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        TextField messageInput = new TextField();
        messageInput.setPromptText("Nhập tin nhắn...");
        messageInput.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px;"
                + " -fx-border-color: #CBD5E1; -fx-padding: 9px;");
        Button emojiButton = new Button("😀");
        emojiButton.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;"
                + " -fx-font-size: 18px; -fx-background-color: #F1F5F9; -fx-background-radius: 8px;");
        Button sendButton = new Button("Gửi");
        sendButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white;"
                + " -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 9px 14px;");

        EmojiPickerPopup emojiPicker = new EmojiPickerPopup(emoji ->
                selectionStatus.setText("Đã chèn emoji " + emoji + " vào tin nhắn"));
        emojiButton.setOnAction(event -> emojiPicker.show(emojiButton, messageInput));
        sendButton.setOnAction(event -> sendMessage(messageInput));
        messageInput.setOnAction(event -> sendMessage(messageInput));

        HBox composer = new HBox(8.0, messageInput, emojiButton, sendButton);
        composer.setAlignment(Pos.CENTER);
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        composer.setPadding(new Insets(12.0, 14.0, 14.0, 14.0));
        composer.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0 transparent transparent transparent;");

        VBox chatPanel = new VBox(10.0, title, messageScrollPane, composer);
        chatPanel.setPadding(new Insets(18.0, 18.0, 0.0, 18.0));
        chatPanel.setStyle("-fx-background-color: #F8FAFC;");
        return chatPanel;
    }

    private VBox avatarColumn(String labelText, AvatarView avatar) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");
        VBox column = new VBox(7.0, avatar, label);
        column.setAlignment(Pos.CENTER);
        return column;
    }

    private HBox createMessage(String sender, String text, Image image, boolean ownMessage) {
        AvatarView avatar = new AvatarView(image, sender, 36.0);
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #64748B;");
        Label messageLabel = new Label(text);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #0F172A;");

        VBox bubble = new VBox(4.0, senderLabel, messageLabel);
        bubble.setPadding(new Insets(9.0, 12.0, 9.0, 12.0));
        bubble.setMaxWidth(320.0);
        bubble.setStyle(ownMessage
                ? "-fx-background-color: #DBEAFE; -fx-background-radius: 10px;"
                : "-fx-background-color: #FFFFFF; -fx-background-radius: 10px;"
                + " -fx-border-color: #E2E8F0; -fx-border-radius: 10px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = ownMessage
                ? new HBox(9.0, spacer, bubble, avatar)
                : new HBox(9.0, avatar, bubble);
        row.setAlignment(ownMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    private void sendMessage(TextField messageInput) {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        messages.getChildren().add(createMessage(CURRENT_USER, text, null, true));
        messageInput.clear();
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    private void chooseImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh avatar");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Tệp ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile == null) {
            return;
        }

        Image selectedImage = new Image(selectedFile.toURI().toString(), false);
        if (selectedImage.isError()) {
            selectionStatus.setText("Không thể đọc ảnh đã chọn: " + selectedFile.getName());
            return;
        }
        for (AvatarView photoAvatar : photoAvatars) {
            photoAvatar.setImage(selectedImage);
        }
        selectionStatus.setText("Đang dùng ảnh: " + selectedFile.getName());
    }

    private Image loadDefaultAvatar() {
        var imageUrl = getClass().getResource("/images/default_avatar.png");
        if (imageUrl == null) {
            return null;
        }
        Image image = new Image(imageUrl.toExternalForm(), false);
        return image.isError() ? null : image;
    }
}
