package com.chat.client.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Hộp thoại modal để chọn người nhận khi chuyển tiếp một tin nhắn. */
public final class ForwardDialog {

    private final Dialog<String> dialog = new Dialog<>();
    private final ListView<Friend> friendList = new ListView<>();
    private final ButtonType forwardButtonType = new ButtonType("Chuyển tiếp", ButtonBar.ButtonData.OK_DONE);

    /**
     * Tạo hộp thoại chọn người nhận.
     *
     * @param owner cửa sổ cha; có thể là {@code null} nếu chưa có cửa sổ cha
     * @param snippet nội dung tin nhắn sẽ được chuyển tiếp
     * @param friends danh sách người nhận có thể chọn
     */
    public ForwardDialog(Window owner, String snippet, List<Friend> friends) {
        Objects.requireNonNull(friends, "friends");
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Chuyển tiếp tin nhắn");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefSize(360.0, 450.0);
        dialog.getDialogPane().setMinSize(360.0, 450.0);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE), forwardButtonType);

        Label summary = new Label("Nội dung: " + singleLine(snippet));
        summary.setWrapText(true);
        summary.setMaxHeight(58.0);
        summary.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8px;"
                + " -fx-padding: 10px; -fx-font-size: 12px; -fx-text-fill: #334155;");

        friendList.getItems().setAll(friends);
        friendList.setCellFactory(view -> new FriendCell());
        friendList.setPlaceholder(new Label("Không có người dùng nào trong danh sách."));
        friendList.setStyle("-fx-background-color: white; -fx-border-color: #E2E8F0;"
                + " -fx-border-radius: 8px; -fx-background-radius: 8px;");
        VBox.setVgrow(friendList, Priority.ALWAYS);

        VBox content = new VBox(12.0, summary, friendList);
        content.setPadding(new Insets(12.0, 6.0, 8.0, 6.0));
        content.setPrefSize(340.0, 365.0);
        dialog.getDialogPane().setContent(content);

        Node forwardButton = dialog.getDialogPane().lookupButton(forwardButtonType);
        forwardButton.disableProperty().bind(friendList.getSelectionModel().selectedItemProperty().isNull());
        forwardButton.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white;"
                + " -fx-font-weight: bold; -fx-background-radius: 6px;");
        dialog.setResultConverter(buttonType -> buttonType == forwardButtonType
                ? Optional.ofNullable(friendList.getSelectionModel().getSelectedItem())
                        .map(Friend::receiverId)
                        .orElse(null)
                : null);
        dialog.getDialogPane().getStylesheets().clear();
    }

    /** Hiển thị modal và trả về ID người nhận hoặc {@link Optional#empty()}. */
    public Optional<String> showAndWait() {
        return dialog.showAndWait();
    }

    /** Giá trị hiển thị cho từng người có thể nhận tin chuyển tiếp. */
    public record Friend(String receiverId, String displayName, boolean online, Image avatarImage) {
        public Friend {
            Objects.requireNonNull(receiverId, "receiverId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    private static String singleLine(String text) {
        if (text == null || text.isBlank()) {
            return "(Không có nội dung)";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 100 ? normalized.substring(0, 97) + "..." : normalized;
    }

    private static final class FriendCell extends ListCell<Friend> {

        private final AvatarView avatar = new AvatarView();
        private final Label name = new Label();
        private final Circle statusDot = new Circle(4.0);
        private final Label statusText = new Label();
        private final HBox row = new HBox(10.0);
        private final HBox nameAndStatus = new HBox(8.0, name, statusDot, statusText);

        private FriendCell() {
            name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
            statusText.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            nameAndStatus.setAlignment(Pos.CENTER_LEFT);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5.0));
        }

        @Override
        protected void updateItem(Friend friend, boolean empty) {
            super.updateItem(friend, empty);
            if (empty || friend == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            avatar.setSize(36.0);
            avatar.setName(friend.displayName());
            avatar.setImage(friend.avatarImage());
            name.setText(friend.displayName());
            statusDot.setFill(friend.online() ? Color.web("#22C55E") : Color.web("#94A3B8"));
            statusText.setText(friend.online() ? "Online" : "Offline");
            row.getChildren().setAll(avatar, nameAndStatus);
            setText(null);
            setGraphic(row);
        }
    }
}
