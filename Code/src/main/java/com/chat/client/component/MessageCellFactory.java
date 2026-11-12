package com.chat.client.component;

import com.chat.common.model.Message;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Tạo giao diện một dòng tin nhắn, gồm avatar, nội dung, thời gian và thao tác. */
public final class MessageCellFactory {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String OWN_BUBBLE_STYLE = "-fx-background-color: #2563EB;"
            + " -fx-background-radius: 16px; -fx-padding: 10px 12px;";
    private static final String OTHER_BUBBLE_STYLE = "-fx-background-color: #FFFFFF;"
            + " -fx-background-radius: 16px; -fx-border-color: #E2E8F0;"
            + " -fx-border-radius: 16px; -fx-border-width: 1px; -fx-padding: 10px 12px;";

    private MessageCellFactory() {
        throw new AssertionError("Không tạo đối tượng từ lớp tiện ích MessageCellFactory.");
    }

    /**
     * Tạo dòng tin nhắn dựa trên các trường sẵn có của model Message. Vì model
     * hiện tại không lưu tên người gửi, thời gian hoặc tên nguồn forward, phương
     * thức dùng ID và thời gian hiển thị hiện tại làm giá trị dự phòng.
     */
    public static HBox createMessageRow(Message message, boolean isOwn,
                                        BiConsumer<String, Message> onAction) {
        Objects.requireNonNull(message, "message");
        String sender = "Người dùng " + message.getSenderId();
        String replySnippet = message.getReplyTo() == null
                ? null
                : "Tin nhắn " + message.getReplyTo();
        String forwardFrom = message.isForwarded() ? "người gửi ban đầu" : null;
        return createMessageRow(message, isOwn, onAction, sender, replySnippet,
                forwardFrom, LocalDateTime.now(), null);
    }

    /**
     * Tạo dòng tin nhắn cùng metadata giao diện bổ sung không có trong model
     * mạng hiện tại. Metadata này hữu ích cho dữ liệu UI đã được làm giàu hoặc
     * cho màn hình demo mà không cần thay đổi JSON/giao thức của dự án.
     */
    public static HBox createMessageRow(Message message, boolean isOwn,
                                        BiConsumer<String, Message> onAction,
                                        String senderName, String replySnippet,
                                        String forwardFrom, LocalDateTime sentAt,
                                        Image avatarImage) {
        Objects.requireNonNull(message, "message");
        BiConsumer<String, Message> actionHandler = onAction == null ? (action, item) -> { } : onAction;
        String safeSenderName = senderName == null || senderName.isBlank()
                ? "Người dùng " + message.getSenderId()
                : senderName;
        String content = message.getContent() == null ? "" : message.getContent();

        VBox bubbleContent = new VBox(5.0);
        if (!isOwn) {
            Label senderLabel = new Label(safeSenderName);
            senderLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #334155;");
            bubbleContent.getChildren().add(senderLabel);
        }
        if (message.isForwarded()) {
            String source = forwardFrom == null || forwardFrom.isBlank()
                    ? "người gửi ban đầu"
                    : forwardFrom;
            Label forwardedLabel = new Label("↪ Đã chuyển tiếp từ " + source);
            forwardedLabel.setStyle("-fx-font-size: 10px; -fx-font-style: italic;"
                    + " -fx-text-fill: " + (isOwn ? "#DBEAFE" : "#64748B") + ";");
            bubbleContent.getChildren().add(forwardedLabel);
        }
        if (message.getReplyTo() != null) {
            String quoteText = replySnippet == null || replySnippet.isBlank()
                    ? "Tin nhắn " + message.getReplyTo()
                    : replySnippet;
            Label quoteLabel = new Label(quoteText);
            quoteLabel.setWrapText(false);
            quoteLabel.setMaxWidth(300.0);
            quoteLabel.setEllipsisString("...");
            quoteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                    + (isOwn ? "#DBEAFE" : "#64748B")
                    + "; -fx-border-color: " + (isOwn ? "#93C5FD" : "#CBD5E1")
                    + "; -fx-border-width: 0 0 0 2px; -fx-padding: 3px 6px;");
            bubbleContent.getChildren().add(quoteLabel);
        }

        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setMaxWidth(340.0);
        contentLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: "
                + (isOwn ? "#FFFFFF" : "#0F172A") + ";");
        bubbleContent.getChildren().add(contentLabel);

        LocalDateTime displayTime = sentAt == null ? LocalDateTime.now() : sentAt;
        Label timeLabel = new Label(TIME_FORMATTER.format(displayTime));
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: "
                + (isOwn ? "#DBEAFE" : "#94A3B8") + ";");
        bubbleContent.getChildren().add(timeLabel);

        VBox bubble = new VBox(bubbleContent);
        bubble.setMaxWidth(370.0);
        bubble.setStyle(isOwn ? OWN_BUBBLE_STYLE : OTHER_BUBBLE_STYLE);

        Button replyButton = createActionButton("↩", "Trả lời");
        Button forwardButton = createActionButton("↪", "Chuyển tiếp");
        replyButton.setOnAction(event -> actionHandler.accept("REPLY", message));
        forwardButton.setOnAction(event -> actionHandler.accept("FORWARD", message));
        HBox actionButtons = new HBox(4.0, replyButton, forwardButton);
        actionButtons.setAlignment(Pos.CENTER);
        actionButtons.setOpacity(0.0);
        actionButtons.setMouseTransparent(true);

        AvatarView avatar = null;
        if (!isOwn) {
            avatar = new AvatarView(avatarImage, safeSenderName, 36.0);
        }

        HBox messageAndActions = new HBox(7.0);
        messageAndActions.setAlignment(Pos.BOTTOM_LEFT);
        messageAndActions.getChildren().addAll(bubble, actionButtons);

        HBox row = new HBox(8.0);
        row.setAlignment(isOwn ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (isOwn) {
            row.getChildren().addAll(spacer, messageAndActions);
        } else {
            row.getChildren().addAll(avatar, messageAndActions, spacer);
        }

        row.setOnMouseEntered(event -> setActionsVisible(actionButtons, true));
        row.setOnMouseExited(event -> setActionsVisible(actionButtons, false));
        return row;
    }

    private static Button createActionButton(String icon, String accessibleText) {
        Button button = new Button(icon);
        button.setAccessibleText(accessibleText);
        button.setFocusTraversable(false);
        button.setMinSize(28.0, 28.0);
        button.setPrefSize(28.0, 28.0);
        button.setMaxSize(28.0, 28.0);
        button.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #475569;"
                + " -fx-background-radius: 14px; -fx-border-color: #E2E8F0;"
                + " -fx-border-radius: 14px; -fx-padding: 0px;");
        return button;
    }

    private static void setActionsVisible(HBox actionButtons, boolean visible) {
        actionButtons.setMouseTransparent(!visible);
        FadeTransition transition = new FadeTransition(Duration.millis(120.0), actionButtons);
        transition.setToValue(visible ? 1.0 : 0.0);
        transition.play();
    }
}
