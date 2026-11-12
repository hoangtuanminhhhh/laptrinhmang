package com.chat.client.component;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Iterator;

/**
 * Chuyển ảnh avatar giữa tệp, chuỗi Base64 và ảnh JavaFX. Việc thu nhỏ dùng
 * ImageIO và AWT có sẵn trong JDK, không cần thư viện hình ảnh bên ngoài.
 */
public final class AvatarHelper {

    private static final int DEFAULT_MAX_WIDTH = 128;
    private static final int DEFAULT_MAX_HEIGHT = 128;
    private static final int MAX_BASE64_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final int MAX_ENCODED_AVATAR_LENGTH = 100 * 1024;

    private AvatarHelper() {
        throw new AssertionError("Không tạo đối tượng từ lớp tiện ích AvatarHelper.");
    }

    /**
     * Đọc ảnh, thu nhỏ theo tỷ lệ để không vượt quá kích thước yêu cầu rồi mã
     * hóa thành PNG. Kích thước ảnh luôn bị giới hạn tối đa 128 × 128, kể cả
     * khi bên gọi truyền giới hạn lớn hơn; nhờ đó chuỗi Base64 nhỏ hơn 100 KiB.
     *
     * @param file tệp ảnh nguồn
     * @param maxWidth chiều rộng tối đa, phải lớn hơn 0
     * @param maxHeight chiều cao tối đa, phải lớn hơn 0
     * @return chuỗi Base64 chuẩn, không có tiền tố data URI
     * @throws IllegalArgumentException nếu tệp hoặc kích thước không hợp lệ,
     *                                  hoặc ảnh có kích thước vượt giới hạn an toàn
     * @throws IOException nếu không đọc hoặc mã hóa được ảnh
     */
    public static String imageToBase64(File file, int maxWidth, int maxHeight) throws IOException {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Tệp ảnh không tồn tại hoặc không thể đọc.");
        }
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("Chiều rộng và chiều cao tối đa phải lớn hơn 0.");
        }

        int boundedWidth = Math.min(maxWidth, DEFAULT_MAX_WIDTH);
        int boundedHeight = Math.min(maxHeight, DEFAULT_MAX_HEIGHT);
        BufferedImage source = readImageWithDimensionLimit(file);
        double scale = Math.min(1.0, Math.min(boundedWidth / (double) source.getWidth(),
                boundedHeight / (double) source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage resized = resizeImage(source, targetWidth, targetHeight);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(resized, "png", output)) {
            throw new IOException("JDK không tìm thấy bộ mã hóa PNG.");
        }

        String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
        if (encoded.length() >= MAX_ENCODED_AVATAR_LENGTH) {
            throw new IOException("Ảnh avatar sau khi thu nhỏ vẫn vượt giới hạn 100 KiB.");
        }
        return encoded;
    }

    /**
     * Giải mã Base64 và tạo ảnh JavaFX đồng bộ. Chuỗi lỗi, ảnh hỏng, dữ liệu
     * rỗng hoặc ảnh vượt giới hạn kích thước an toàn sẽ trả về {@code null}.
     *
     * @param base64Str dữ liệu ảnh Base64 chuẩn, có thể có tiền tố data URI
     * @return ảnh JavaFX hợp lệ hoặc {@code null} nếu không giải mã được
     */
    public static Image base64ToImage(String base64Str) {
        if (base64Str == null || base64Str.isBlank()) {
            return null;
        }

        String payload = stripDataUriPrefix(base64Str.trim());
        long estimatedBytes = (long) payload.length() * 3 / 4;
        if (estimatedBytes <= 0 || estimatedBytes > MAX_BASE64_IMAGE_BYTES) {
            return null;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(payload);
            if (imageBytes.length == 0 || imageBytes.length > MAX_BASE64_IMAGE_BYTES) {
                return null;
            }
            BufferedImage decoded = readImageWithDimensionLimit(imageBytes);
            ByteArrayOutputStream normalizedPng = new ByteArrayOutputStream();
            if (!ImageIO.write(decoded, "png", normalizedPng)) {
                return null;
            }
            Image image = new Image(new ByteArrayInputStream(normalizedPng.toByteArray()), 0, 0, true, true);
            return image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0 ? null : image;
        } catch (IllegalArgumentException | IOException exception) {
            return null;
        }
    }

    /**
     * Mã hóa nguyên trạng byte của tệp, không resize và không thay đổi định dạng.
     *
     * @param file tệp cần mã hóa
     * @return chuỗi Base64 chuẩn, không có tiền tố data URI
     * @throws IllegalArgumentException nếu tệp không tồn tại hoặc không đọc được
     * @throws UncheckedIOException nếu xảy ra lỗi đọc tệp
     */
    public static String fileToBase64(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Tệp không tồn tại hoặc không thể đọc.");
        }
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Không thể đọc tệp để mã hóa Base64.", exception);
        }
    }

    private static BufferedImage readImageWithDimensionLimit(File file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            return readImageWithDimensionLimit(input);
        }
    }

    private static BufferedImage readImageWithDimensionLimit(byte[] imageBytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            return readImageWithDimensionLimit(input);
        }
    }

    private static BufferedImage readImageWithDimensionLimit(ImageInputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Không thể mở dữ liệu ảnh.");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new IOException("Định dạng ảnh không được hỗ trợ hoặc dữ liệu ảnh bị hỏng.");
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                throw new IllegalArgumentException("Kích thước ảnh vượt quá giới hạn an toàn.");
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new IOException("Bộ giải mã không đọc được ảnh.");
            }
            return image;
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage resizeImage(BufferedImage source, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static String stripDataUriPrefix(String encoded) {
        if (!encoded.startsWith("data:")) {
            return encoded;
        }
        int separator = encoded.indexOf(',');
        return separator >= 0 ? encoded.substring(separator + 1) : "";
    }
}
