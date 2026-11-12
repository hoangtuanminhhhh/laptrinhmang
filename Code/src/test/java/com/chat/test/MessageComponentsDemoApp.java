package com.chat.test;

import com.chat.client.component.AvatarHelper;
import com.chat.client.component.AvatarView;
import com.chat.client.component.EmojiPickerPopup;
import com.chat.client.component.ForwardDialog;
import com.chat.client.component.MessageCellFactory;
import com.chat.client.component.ReplyPreviewBar;
import com.chat.common.model.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Ứng dụng độc lập kiểm thử giao diện reply, forward, emoji và ảnh Base64. */
public class MessageComponentsDemoApp extends Application {

    private static final String CURRENT_USER_NAME = "Bạn";
    private static final int CURRENT_USER_ID = 1;

    private final Map<Message, MessagePresentation> presentations = new IdentityHashMap<>();
    private final List<ForwardDialog.Friend> friends = createSampleFriends();

    private VBox messageList;
    private ScrollPane messageScrollPane;
    private TextField messageInput;
    private ReplyPreviewBar replyPreviewBar;
    private Label statusLabel;
    private AvatarView base64AvatarPreview;
    private Message replyingTo;

    /** Khởi chạy cửa sổ minh họa các thành phần tin nhắn. */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F1F5F9;");
        root.setTop(createHeader());
        root.setCenter(createConversation());
        root.setBottom(createComposer(stage));

        Scene scene = new Scene(root, 850.0, 650.0);
        stage.setTitle("Minh họa Reply, Forward và Avatar");
        stage.setMinWidth(720.0);
        stage.setMinHeight(540.0);
        stage.setScene(scene);
        stage.show();
    }

    private HBox createHeader() {
        Label title = new Label("Phòng chat — demo thành phần tin nhắn");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        statusLabel = new Label("Di chuột lên tin nhắn để Reply hoặc Forward");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        VBox titleBox = new VBox(4.0, title, statusLabel);
        HBox header = new HBox(titleBox);
        header.setPadding(new Insets(16.0, 20.0, 16.0, 20.0));
        header.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #E2E8F0 transparent;");
        return header;
    }

    private VBox createConversation() {
        messageList = new VBox(14.0);
        messageList.setPadding(new Insets(18.0));
        messageList.setFillWidth(true);

        Image sampleAvatar = loadSampleAvatar();
        Message normal = createMessage("1001", 2, "Chào cả nhóm, hôm nay mình họp lúc 14:00 nhé.", false, null);
        Message emoji = createMessage("1002", 3, "Được nha! Hẹn gặp mọi người 😄🎉", false, null);
        Message reply = createMessage("1003", 1, "Mình sẽ tham gia nhé.", false, "1001");
        Message forwarded = createMessage("1004", 2, "Tài liệu buổi học đã được cập nhật.", true, null);

        addMessageRow(normal, false, "Minh", "", null, LocalDateTime.now().minusMinutes(12), sampleAvatar);
        addMessageRow(emoji, false, "Lan", "", null, LocalDateTime.now().minusMinutes(9), null);
        addMessageRow(reply, true, CURRENT_USER_NAME, "Chào cả nhóm, hôm nay mình họp lúc 14:00 nhé.",
                null, LocalDateTime.now().minusMinutes(7), null);
        addMessageRow(forwarded, false, "Minh", "", "Tuấn", LocalDateTime.now().minusMinutes(3), sampleAvatar);

        messageScrollPane = new ScrollPane(messageList);
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messageScrollPane.setStyle("-fx-background: #F8FAFC; -fx-background-color: #F8FAFC;");
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);

        VBox conversation = new VBox(messageScrollPane);
        conversation.setPadding(new Insets(0.0, 16.0, 0.0, 16.0));
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);
        return conversation;
    }

    private VBox createComposer(Stage owner) {
        replyPreviewBar = new ReplyPreviewBar();
        replyPreviewBar.setOnCancel(() -> {
            replyingTo = null;
            statusLabel.setText("Đã hủy trả lời tin nhắn");
        });

        messageInput = new TextField();
        messageInput.setPromptText("Nhập nội dung tin nhắn...");
        messageInput.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px;"
                + " -fx-border-color: #CBD5E1; -fx-padding: 9px;");

        Button emojiButton = new Button("😀");
        emojiButton.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;"
                + " -fx-font-size: 18px; -fx-background-color: #F1F5F9; -fx-background-radius: 8px;");
        EmojiPickerPopup emojiPicker = new EmojiPickerPopup();
        emojiButton.setOnAction(event -> emojiPicker.show(emojiButton, messageInput));

        Button sendButton = new Button("Gửi");
        sendButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white;"
                + " -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 9px 14px;");
        sendButton.setOnAction(event -> sendMessage());
        messageInput.setOnAction(event -> sendMessage());

        HBox inputRow = new HBox(8.0, messageInput, emojiButton, sendButton);
        inputRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        base64AvatarPreview = new AvatarView((Image) null, "Ảnh", 42.0);
        Button testBase64Button = new Button("Test Base64 Avatar");
        testBase64Button.setOnAction(event -> testBase64Avatar(owner));
        testBase64Button.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #334155;"
                + " -fx-border-color: #CBD5E1; -fx-border-radius: 7px; -fx-background-radius: 7px;");

        HBox toolRow = new HBox(10.0, testBase64Button, base64AvatarPreview);
        toolRow.setAlignment(Pos.CENTER_LEFT);

        VBox composer = new VBox(9.0, replyPreviewBar, inputRow, toolRow);
        composer.setPadding(new Insets(12.0, 18.0, 14.0, 18.0));
        composer.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0 transparent transparent transparent;");
        return composer;
    }

    private Message createMessage(String messageId, int senderId, String content,
                                  boolean forwarded, String replyTo) {
        Message message = new Message();
        message.setMsgId(messageId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setForwarded(forwarded);
        message.setReplyTo(replyTo);
        return message;
    }

    private void addMessageRow(Message message, boolean own, String senderName,
                               String replySnippet, String forwardFrom,
                               LocalDateTime sentAt, Image avatarImage) {
        MessagePresentation presentation = new MessagePresentation(senderName, replySnippet,
                forwardFrom, sentAt, avatarImage);
        presentations.put(message, presentation);
        messageList.getChildren().add(MessageCellFactory.createMessageRow(
                message, own, this::handleMessageAction,
                presentation.senderName(), presentation.replySnippet(),
                presentation.forwardFrom(), presentation.sentAt(), presentation.avatarImage()));
    }

    private void handleMessageAction(String action, Message message) {
        MessagePresentation presentation = presentations.get(message);
        if (presentation == null) {
            return;
        }
        if ("REPLY".equals(action)) {
            replyingTo = message;
            replyPreviewBar.showReply(message.getMsgId(), presentation.senderName(), message.getContent());
            messageInput.requestFocus();
            statusLabel.setText("Đang trả lời " + presentation.senderName());
            return;
        }
        if ("FORWARD".equals(action)) {
            ForwardDialog forwardDialog = new ForwardDialog(
                    messageList.getScene().getWindow(), message.getContent(), friends);
            Optional<String> selectedReceiver = forwardDialog.showAndWait();
            selectedReceiver.ifPresent(receiverId -> statusLabel.setText(
                    "Đã chọn chuyển tiếp tới " + receiverId + " (demo chưa gửi qua mạng)"));
        }
    }

    private void sendMessage() {
        String content = messageInput.getText() == null ? "" : messageInput.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        String replyId = replyingTo == null ? null : replyingTo.getMsgId();
        Message message = createMessage(messageId, CURRENT_USER_ID, content, false, replyId);
        String quotedSnippet = replyingTo == null ? null : replyingTo.getContent();
        addMessageRow(message, true, CURRENT_USER_NAME, quotedSnippet, null,
                LocalDateTime.now(), null);

        messageInput.clear();
        replyingTo = null;
        replyPreviewBar.clear();
        statusLabel.setText("Tin nhắn đã được thêm vào giao diện demo");
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    private void testBase64Avatar(Stage owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh để thử mã hóa avatar Base64");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Tệp ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile == null) {
            statusLabel.setText("Đã hủy chọn ảnh; avatar thử nghiệm không thay đổi");
            return;
        }

        try {
            String encoded = AvatarHelper.imageToBase64(selectedFile, 128, 128);
            Image decodedImage = AvatarHelper.base64ToImage(encoded);
            if (decodedImage == null) {
                statusLabel.setText("Không giải mã được ảnh Base64 đã tạo");
                return;
            }
            base64AvatarPreview.setName(selectedFile.getName());
            base64AvatarPreview.setImage(decodedImage);
            statusLabel.setText("Mã hóa và giải mã thành công: " + encoded.length() + " ký tự Base64");
        } catch (IOException | IllegalArgumentException exception) {
            statusLabel.setText("Không thể xử lý ảnh: " + exception.getMessage());
        }
    }

    private Image loadSampleAvatar() {
        var imageResource = getClass().getResource("/images/default_avatar.png");
        if (imageResource == null) {
            return null;
        }
        Image image = new Image(imageResource.toExternalForm(), false);
        return image.isError() ? null : image;
    }

    private static List<ForwardDialog.Friend> createSampleFriends() {
        return new ArrayList<>(List.of(
                new ForwardDialog.Friend("2", "Minh", true, null),
                new ForwardDialog.Friend("3", "Lan", true, null),
                new ForwardDialog.Friend("4", "Tuấn", false, null),
                new ForwardDialog.Friend("5", "Hương", true, null),
                new ForwardDialog.Friend("6", "Nhóm học Java", false, null)
        ));
    }

    private record MessagePresentation(String senderName, String replySnippet,
                                       String forwardFrom, LocalDateTime sentAt,
                                       Image avatarImage) {
    }
}
