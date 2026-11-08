package com.chat.client.component;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Thành phần avatar hình tròn: hiển thị ảnh khi ảnh dùng được, nếu không sẽ
 * hiển thị chữ cái đầu trên nền màu được chọn theo tên.
 */
public class AvatarView extends StackPane {

    private static final double DEFAULT_SIZE = 40.0;
    private static final double BORDER_WIDTH = 1.5;
    private static final Color BORDER_COLOR = Color.web("#E2E8F0");
    private static final Color[] PALETTE = {
            Color.web("#4F83CC"), Color.web("#3F9B8F"),
            Color.web("#8B72C8"), Color.web("#D88948"),
            Color.web("#CB6B91"), Color.web("#6EAA74"),
            Color.web("#D96C61"), Color.web("#657D9C")
    };

    private final Label initialLabel = new Label();
    private final Circle backgroundCircle = new Circle();
    private final Circle borderCircle = new Circle();
    private final ImageView imageView = new ImageView();

    private final ChangeListener<Number> imageProgressListener =
            (observable, oldValue, newValue) -> refreshView();
    private final ChangeListener<Boolean> imageErrorListener =
            (observable, oldValue, newValue) -> refreshView();

    private Image image;
    private String name = "";
    private double size = DEFAULT_SIZE;

    /** Tạo avatar dự phòng có đường kính 40 pixel và chưa có tên hiển thị. */
    public AvatarView() {
        configureNodes();
        refreshView();
    }

    /** Tạo avatar từ ảnh, tên hiển thị và đường kính được cung cấp. */
    public AvatarView(Image image, String name, double size) {
        configureNodes();
        setName(name);
        setSize(size);
        setImage(image);
    }

    /** Tạo avatar từ ảnh và tên, sử dụng đường kính mặc định 40 pixel. */
    public AvatarView(Image image, String name) {
        this(image, name, DEFAULT_SIZE);
    }

    /** Tạo avatar từ URL hoặc đường dẫn tệp, tên hiển thị và đường kính. */
    public AvatarView(String imageUrl, String name, double size) {
        this(loadImage(imageUrl), name, size);
    }

    /** Tạo avatar từ URL hoặc đường dẫn tệp, sử dụng đường kính mặc định. */
    public AvatarView(String imageUrl, String name) {
        this(imageUrl, name, DEFAULT_SIZE);
    }

    /**
     * Thay ảnh avatar. Ảnh bị thiếu, tải lỗi hoặc không có dữ liệu sẽ dùng
     * phần hiển thị chữ cái thay thế.
     *
     * @param image ảnh cần hiển thị; truyền {@code null} để dùng avatar dự phòng
     */
    public final void setImage(Image image) {
        if (this.image != null) {
            this.image.progressProperty().removeListener(imageProgressListener);
            this.image.errorProperty().removeListener(imageErrorListener);
        }
        this.image = image;
        if (image != null) {
            image.progressProperty().addListener(imageProgressListener);
            image.errorProperty().addListener(imageErrorListener);
        }
        refreshView();
    }

    /**
     * Đổi tên hiển thị, đồng thời cập nhật chữ cái và màu nền dự phòng.
     *
     * @param name tên hiển thị; {@code null} được xem như tên rỗng
     */
    public final void setName(String name) {
        this.name = name == null ? "" : name;
        refreshView();
    }

    /**
     * Đổi đường kính avatar, tính bằng pixel.
     *
     * @param size đường kính phải là số hữu hạn lớn hơn 0
     * @throws IllegalArgumentException nếu đường kính không hợp lệ
     */
    public final void setSize(double size) {
        if (!Double.isFinite(size) || size <= 0.0) {
            throw new IllegalArgumentException("Kích thước avatar phải là số hữu hạn lớn hơn 0.");
        }
        this.size = size;
        refreshView();
    }

    private void configureNodes() {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setPadding(Insets.EMPTY);
        setPickOnBounds(false);

        initialLabel.setTextFill(Color.WHITE);
        initialLabel.setStyle("-fx-font-weight: bold;");
        initialLabel.setMouseTransparent(true);

        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);

        borderCircle.setFill(Color.TRANSPARENT);
        borderCircle.setStroke(BORDER_COLOR);
        borderCircle.setStrokeWidth(BORDER_WIDTH);
        borderCircle.setMouseTransparent(true);
    }

    private void refreshView() {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);

        double center = size / 2.0;
        double radius = Math.max(0.0, center - BORDER_WIDTH / 2.0);
        borderCircle.setCenterX(center);
        borderCircle.setCenterY(center);
        borderCircle.setRadius(radius);

        getChildren().clear();
        if (hasUsableImage()) {
            imageView.setImage(image);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            if (image.getWidth() > 0.0 && image.getHeight() > 0.0) {
                double cropSize = Math.min(image.getWidth(), image.getHeight());
                double cropX = (image.getWidth() - cropSize) / 2.0;
                double cropY = (image.getHeight() - cropSize) / 2.0;
                imageView.setViewport(new Rectangle2D(cropX, cropY, cropSize, cropSize));
            } else {
                imageView.setViewport(null);
            }
            Circle clip = new Circle(center, center, center);
            imageView.setClip(clip);
            getChildren().addAll(imageView, borderCircle);
            return;
        }

        imageView.setImage(null);
        imageView.setClip(null);
        imageView.setViewport(null);
        backgroundCircle.setCenterX(center);
        backgroundCircle.setCenterY(center);
        backgroundCircle.setRadius(center);
        backgroundCircle.setFill(paletteColor());

        String visibleName = name.trim();
        if (visibleName.isEmpty()) {
            visibleName = "?";
        }
        int firstCodePoint = visibleName.codePointAt(0);
        initialLabel.setText(new String(Character.toChars(firstCodePoint)).toUpperCase(Locale.ROOT));
        initialLabel.setStyle("-fx-font-weight: bold; -fx-font-size: " + (size * 0.45) + "px;");
        getChildren().addAll(backgroundCircle, initialLabel, borderCircle);
    }

    private boolean hasUsableImage() {
        if (image == null || image.isError()) {
            return false;
        }
        return image.getProgress() < 1.0 || (image.getWidth() > 0.0 && image.getHeight() > 0.0);
    }

    private Color paletteColor() {
        String colorName = name.isBlank() ? "?" : name;
        int absoluteHash = Math.abs(colorName.hashCode());
        int paletteIndex = Math.floorMod(absoluteHash, PALETTE.length);
        return PALETTE[paletteIndex];
    }

    private static Image loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String location = imageUrl.trim();
        if (location.startsWith("file:") || location.contains("://")) {
            return new Image(location, false);
        }
        return new Image(Path.of(location).toAbsolutePath().toUri().toString(), false);
    }
}
