package com.chat.client.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Objects;

/** Thanh xem trước nội dung đang được trả lời, đặt phía trên vùng nhập tin nhắn. */
public class ReplyPreviewBar extends HBox {

    private final Label senderLabel = new Label();
    private final Label snippetLabel = new Label();
    private final Button cancelButton = new Button("✕");

    private String messageId;
    private String senderName;
    private String snippet;
    private Runnable onCancel = () -> { };

    /** Tạo thanh xem trước đang ẩn; vùng bố cục được quản lý theo trạng thái hiển thị. */
    public ReplyPreviewBar() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10.0);
        setPadding(new Insets(8.0, 10.0, 8.0, 10.0));
        setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8px;"
                + " -fx-border-color: transparent transparent transparent #2563EB;"
                + " -fx-border-width: 0 0 0 4px; -fx-border-radius: 8px;");
        visibleProperty().set(false);
        managedProperty().bind(visibleProperty());

        Label replyIcon = new Label("↩");
        replyIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #2563EB;");
        senderLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2563EB;");
        snippetLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        snippetLabel.setMaxWidth(Double.MAX_VALUE);
        snippetLabel.setEllipsisString("...");
        snippetLabel.setWrapText(false);

        cancelButton.setFocusTraversable(false);
        cancelButton.setPadding(new Insets(3.0, 6.0, 3.0, 6.0));
        cancelButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748B;"
                + " -fx-font-size: 12px; -fx-background-radius: 5px;");
        cancelButton.setOnAction(event -> {
            clear();
            onCancel.run();
        });

        HBox textContent = new HBox(3.0, senderLabel, snippetLabel);
        textContent.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContent, Priority.ALWAYS);
        HBox.setHgrow(snippetLabel, Priority.ALWAYS);
        getChildren().addAll(replyIcon, textContent, cancelButton);
    }

    /**
     * Hiển thị bản xem trước của tin nhắn được chọn để trả lời.
     *
     * @param msgId mã tin nhắn nguồn
     * @param senderName tên người gửi tin nhắn nguồn
     * @param snippet phần trích dẫn ngắn
     */
    public void showReply(String msgId, String senderName, String snippet) {
        this.messageId = Objects.requireNonNull(msgId, "msgId");
        this.senderName = senderName == null || senderName.isBlank() ? "Người dùng" : senderName;
        this.snippet = snippet == null ? "" : snippet;
        senderLabel.setText("Đang trả lời " + this.senderName + " —");
        snippetLabel.setText(this.snippet);
        setVisible(true);
    }

    /** Xóa dữ liệu và ẩn thanh xem trước. */
    public void clear() {
        messageId = null;
        senderName = null;
        snippet = null;
        senderLabel.setText("");
        snippetLabel.setText("");
        setVisible(false);
    }

    /** Trả về mã tin nhắn đang được trả lời hoặc {@code null}. */
    public String getMessageId() {
        return messageId;
    }

    /** Trả về tên người gửi của tin đang được trả lời hoặc {@code null}. */
    public String getSenderName() {
        return senderName;
    }

    /** Trả về nội dung trích dẫn hoặc {@code null} nếu thanh đang trống. */
    public String getSnippet() {
        return snippet;
    }

    /** Đăng ký hành động bổ sung chạy sau khi người dùng hủy thao tác trả lời. */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    }
}
