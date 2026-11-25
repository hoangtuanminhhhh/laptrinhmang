package com.chat.client.component;

import javafx.scene.media.AudioClip;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý duy nhất hiệu ứng âm thanh thông báo của ứng dụng.
 *
 * <p>Việc tìm và khởi tạo {@link AudioClip}, cũng như yêu cầu phát âm thanh,
 * được đưa vào một luồng nền. Vì vậy, lần phát đầu tiên không làm chặn JavaFX
 * Application Thread. Tệp âm thanh tùy chọn được nạp từ
 * {@code /sounds/notification.wav}; nếu không có tệp, ứng dụng vẫn hoạt động
 * bình thường và chỉ ghi một thông báo ở mức FINE.</p>
 */
public final class SoundManager {

    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());
    private static final String NOTIFICATION_RESOURCE = "/sounds/notification.wav";

    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "chat-audio");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean muted = false;
    private volatile double volume = 0.7;
    private AudioClip notificationClip;
    private boolean resourceLookupCompleted;

    private SoundManager() {
    }

    /** Trả về instance duy nhất của bộ quản lý âm thanh. */
    public static SoundManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Phát tiếng thông báo nếu âm thanh chưa bị tắt.
     *
     * <p>Phương thức trả về ngay; việc tải resource và phát clip chạy ở luồng
     * nền, không thực hiện I/O hoặc chờ phát trên UI Thread.</p>
     */
    public void playNotificationSound() {
        if (muted) {
            return;
        }
        audioExecutor.execute(() -> {
            if (muted) {
                return;
            }
            AudioClip clip = getOrLoadNotificationClip();
            if (clip != null && !muted) {
                clip.play(volume);
            }
        });
    }

    /** Bật hoặc tắt âm thanh thông báo. */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /** Cho biết âm thanh thông báo hiện có bị tắt hay không. */
    public boolean isMuted() {
        return muted;
    }

    /**
     * Đặt âm lượng thông báo trong khoảng từ 0.0 đến 1.0.
     *
     * @throws IllegalArgumentException nếu âm lượng không hữu hạn hoặc nằm
     *                                  ngoài khoảng cho phép
     */
    public void setVolume(double volume) {
        if (!Double.isFinite(volume) || volume < 0.0 || volume > 1.0) {
            throw new IllegalArgumentException("Âm lượng phải nằm trong khoảng 0.0 đến 1.0.");
        }
        this.volume = volume;
    }

    private AudioClip getOrLoadNotificationClip() {
        if (resourceLookupCompleted) {
            return notificationClip;
        }
        resourceLookupCompleted = true;

        URL resource = SoundManager.class.getResource(NOTIFICATION_RESOURCE);
        if (resource == null) {
            resource = findGeneratedResource();
            if (resource == null) {
                LOGGER.fine(() -> "Không tìm thấy resource âm thanh: " + NOTIFICATION_RESOURCE);
                return null;
            }
        }

        try {
            notificationClip = new AudioClip(resource.toExternalForm());
        } catch (IllegalArgumentException | SecurityException exception) {
            LOGGER.log(Level.WARNING, "Không thể nạp âm thanh thông báo từ " + resource, exception);
        }
        return notificationClip;
    }

    private URL findGeneratedResource() {
        Path projectResource = Path.of("src", "main", "resources", "sounds", "notification.wav");
        Path repositoryResource = Path.of("Code", "src", "main", "resources", "sounds", "notification.wav");
        for (Path candidate : new Path[]{projectResource, repositoryResource}) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                return candidate.toUri().toURL();
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Không thể mở resource âm thanh " + candidate, exception);
                return null;
            }
        }
        return null;
    }

    private static final class InstanceHolder {
        private static final SoundManager INSTANCE = new SoundManager();
    }
}
