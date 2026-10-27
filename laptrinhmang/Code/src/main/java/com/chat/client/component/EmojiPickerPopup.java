package com.chat.client.component;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Hộp chọn emoji có thể cuộn, chèn emoji được chọn vào vị trí con trỏ hiện tại
 * của trường nhập liệu JavaFX.
 */
public class EmojiPickerPopup extends Popup {

    private static final String[] EMOJIS = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😍", "🥰", "😘",
            "😋", "😎", "🤓", "🤔", "🤗", "🤫", "🤭", "😴",
            "😭", "😢", "😤", "😡", "🤯", "🥳", "😱", "🤩",
            "👍", "👎", "👏", "🙌", "🙏", "🤝", "💪", "👋",
            "✌️", "🤞", "👌", "👀", "💃", "🕺", "🫶", "🤟",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "💔", "💕", "💖", "💗", "💘", "💝", "💞", "💓",
            "🔥", "🎉", "🎊", "🚀", "⭐", "🌟", "✨", "💯",
            "✅", "❌", "⚡", "🌈", "☕", "🍰", "🎁", "🏆"
    };

    private static final String EMOJI_FONT_STYLE = "-fx-font-family: 'Segoe UI Emoji', "
            + "'Apple Color Emoji', 'Noto Color Emoji', sans-serif; -fx-font-size: 18px;";

    private final FlowPane emojiGrid = new FlowPane();
    private Consumer<String> onEmojiSelected;

    /** Tạo hộp chọn emoji không đăng ký hàm xử lý sau khi chọn. */
    public EmojiPickerPopup() {
        this(emoji -> { });
    }

    /** Tạo hộp chọn emoji và đăng ký hàm xử lý mỗi khi người dùng chọn emoji. */
    public EmojiPickerPopup(Consumer<String> onEmojiSelected) {
        this.onEmojiSelected = Objects.requireNonNull(onEmojiSelected, "onEmojiSelected");
        setAutoHide(true);
        setHideOnEscape(true);
        getContent().add(createPopupContent());
    }

    /** Thay hàm xử lý được gọi sau khi emoji đã được chèn vào ô nhập. */
    public void setOnEmojiSelected(Consumer<String> onEmojiSelected) {
        this.onEmojiSelected = Objects.requireNonNull(onEmojiSelected, "onEmojiSelected");
    }

    /**
     * Hiển thị hộp chọn cạnh nút neo và chèn lựa chọn vào ô nhập đích. Hộp chọn
     * xuất hiện bên dưới nút neo, trừ khi không đủ chỗ trên màn hình thì chuyển
     * lên phía trên.
     *
     * @param anchorNode nút dùng để xác định vị trí hiển thị hộp chọn
     * @param targetInput ô nhập nhận emoji được chọn
     * @throws NullPointerException nếu một trong hai tham số là {@code null}
     * @throws IllegalStateException nếu nút neo chưa thuộc một cửa sổ đang hiển thị
     */
    public void show(Node anchorNode, TextInputControl targetInput) {
        Objects.requireNonNull(anchorNode, "anchorNode");
        Objects.requireNonNull(targetInput, "targetInput");
        if (anchorNode.getScene() == null || anchorNode.getScene().getWindow() == null
                || !anchorNode.getScene().getWindow().isShowing()) {
            throw new IllegalStateException("Nút neo của hộp chọn phải nằm trong cửa sổ đang hiển thị.");
        }

        Window owner = anchorNode.getScene().getWindow();
        Bounds screenBounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        if (screenBounds == null) {
            throw new IllegalStateException("Không xác định được tọa độ màn hình của nút neo.");
        }

        VBox popupRoot = (VBox) getContent().get(0);
        popupRoot.applyCss();
        popupRoot.autosize();
        double popupWidth = popupRoot.prefWidth(-1);
        double popupHeight = popupRoot.prefHeight(popupWidth);

        Screen screen = Screen.getScreensForRectangle(
                screenBounds.getMinX(), screenBounds.getMinY(),
                screenBounds.getWidth(), screenBounds.getHeight()).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
        Rectangle2D visualBounds = screen.getVisualBounds();

        double x = screenBounds.getMinX();
        double y = screenBounds.getMaxY() + 6.0;
        if (y + popupHeight > visualBounds.getMaxY()) {
            y = screenBounds.getMinY() - popupHeight - 6.0;
        }
        x = Math.max(visualBounds.getMinX(),
                Math.min(x, visualBounds.getMaxX() - popupWidth));
        y = Math.max(visualBounds.getMinY(),
                Math.min(y, visualBounds.getMaxY() - popupHeight));

        for (Node node : emojiGrid.getChildren()) {
            if (node instanceof Button emojiButton) {
                emojiButton.setOnAction(event -> insertEmoji(targetInput, emojiButton.getText()));
            }
        }
        show(owner, x, y);
    }

    private VBox createPopupContent() {
        emojiGrid.setHgap(4.0);
        emojiGrid.setVgap(4.0);
        emojiGrid.setPadding(new Insets(8.0));
        emojiGrid.setPrefWrapLength(8.0 * 36.0 + 7.0 * 4.0);

        for (String emoji : EMOJIS) {
            Button emojiButton = new Button(emoji);
            emojiButton.setMinSize(36.0, 36.0);
            emojiButton.setPrefSize(36.0, 36.0);
            emojiButton.setMaxSize(36.0, 36.0);
            emojiButton.setFocusTraversable(false);
            emojiButton.setStyle(EMOJI_FONT_STYLE + " -fx-background-color: transparent;"
                    + " -fx-background-radius: 6px; -fx-padding: 0px;");
            emojiButton.setOnMouseEntered(event -> emojiButton.setStyle(EMOJI_FONT_STYLE
                    + " -fx-background-color: #F1F5F9; -fx-background-radius: 6px; -fx-padding: 0px;"));
            emojiButton.setOnMouseExited(event -> emojiButton.setStyle(EMOJI_FONT_STYLE
                    + " -fx-background-color: transparent; -fx-background-radius: 6px; -fx-padding: 0px;"));
            emojiGrid.getChildren().add(emojiButton);
        }

        ScrollPane scrollPane = new ScrollPane(emojiGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefViewportWidth(8.0 * 36.0 + 7.0 * 4.0 + 16.0);
        scrollPane.setPrefViewportHeight(8.0 * 36.0 + 7.0 * 4.0 + 16.0);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox popupRoot = new VBox(scrollPane);
        popupRoot.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 12px;"
                + " -fx-border-color: #E2E8F0; -fx-border-radius: 12px; -fx-border-width: 1px;");
        popupRoot.setEffect(new DropShadow(12.0, 0.0, 4.0, Color.rgb(15, 23, 42, 0.16)));
        return popupRoot;
    }

    private void insertEmoji(TextInputControl targetInput, String emoji) {
        int caret = targetInput.getCaretPosition();
        targetInput.insertText(caret, emoji);
        targetInput.positionCaret(caret + emoji.length());
        hide();
        onEmojiSelected.accept(emoji);
    }
}
