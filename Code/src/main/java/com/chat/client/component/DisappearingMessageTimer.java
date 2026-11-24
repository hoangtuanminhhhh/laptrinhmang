package com.chat.client.component;

import com.chat.common.model.Message;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý tính năng Tin nhắn tự hủy (Disappearing Messages) trong hệ thống TCP Chat.
 * <p>
 * Tính năng cho phép thiết lập thời gian tồn tại (TTL - Time-To-Live) cho tin nhắn.
 * Khi đồng hồ đếm ngược kết thúc:
 * <ul>
 *   <li>Cập nhật giao diện đếm ngược theo thời gian thực (giây).</li>
 *   <li>Thực hiện hiệu ứng biến mất mượt mà (FadeTransition) trên giao diện JavaFX.</li>
 *   <li>Tự động xóa tin nhắn khỏi khung hiển thị trò chuyện (Pane / VBox / ListView).</li>
 *   <li>Kích hoạt callback thông báo hết hạn để dọn dẹp dữ liệu tương ứng.</li>
 * </ul>
 * <p>
 * Lớp được thiết kế đa luồng an toàn (thread-safe), hỗ trợ cả môi trường JavaFX
 * đồ họa lẫn môi trường kiểm thử không giao diện (headless / JUnit test).
 */
public class DisappearingMessageTimer {

    private static final Logger LOGGER = Logger.getLogger(DisappearingMessageTimer.class.getName());

    /** Thời gian hiệu ứng mờ dần (Fade out) trước khi gỡ khỏi container (mili-giây). */
    public static final int DEFAULT_FADE_DURATION_MS = 400;

    /** Thời gian tự hủy mặc định đề xuất (giây). */
    public static final long DEFAULT_TTL_SECONDS = 10;

    private static volatile DisappearingMessageTimer instance;

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final Map<String, DisappearingTask> activeTasks = new ConcurrentHashMap<>();

    /**
     * Khởi tạo bộ đếm với Scheduler mặc định (Daemon threads).
     */
    public DisappearingMessageTimer() {
        this(Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("DisappearingMessageTimer")), true);
    }

    /**
     * Khởi tạo với ScheduledExecutorService tùy biến (tiện lợi cho dependency injection hoặc mock test).
     *
     * @param scheduler Scheduler dùng để lên lịch đếm ngược
     */
    public DisappearingMessageTimer(ScheduledExecutorService scheduler) {
        this(scheduler, false);
    }

    private DisappearingMessageTimer(ScheduledExecutorService scheduler, boolean ownsScheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler không được null");
        this.ownsScheduler = ownsScheduler;
    }

    /**
     * Lấy đối tượng Singleton dùng chung cho toàn bộ ứng dụng Client.
     */
    public static DisappearingMessageTimer getInstance() {
        if (instance == null) {
            synchronized (DisappearingMessageTimer.class) {
                if (instance == null) {
                    instance = new DisappearingMessageTimer();
                }
            }
        }
        return instance;
    }

    /**
     * Đặt lại instance (chủ yếu phục vụ dọn dẹp sau các bài kiểm thử unit test).
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    /**
     * Lên lịch tự hủy tin nhắn dựa trên mã tin nhắn (hỗ trợ headless / logic phi giao diện).
     *
     * @param messageId       Mã định danh tin nhắn duy nhất
     * @param durationSeconds Thời gian tồn tại tính bằng giây
     * @param onExpired       Hành động thực thi khi tin nhắn hết hạn (có thể null)
     * @return ScheduledFuture đại diện cho tác vụ đếm ngược
     */
    public ScheduledFuture<?> schedule(String messageId, long durationSeconds, Runnable onExpired) {
        return scheduleWithCountdown(messageId, null, null, durationSeconds, null, onExpired);
    }

    /**
     * Lên lịch tự hủy tin nhắn đồ họa với hiệu ứng FadeTransition và tự xóa khỏi container.
     *
     * @param messageId       Mã định danh tin nhắn
     * @param messageNode     Node giao diện của dòng tin nhắn (HBox / VBox)
     * @param parentContainer Container chứa tin nhắn (VBox / Pane)
     * @param durationSeconds Thời gian tồn tại tính bằng giây
     * @param onExpired       Callback khi hết hạn (có thể null)
     * @return ScheduledFuture đại diện cho tác vụ
     */
    public ScheduledFuture<?> schedule(String messageId, Node messageNode, Pane parentContainer,
                                       long durationSeconds, Runnable onExpired) {
        return scheduleWithCountdown(messageId, messageNode, parentContainer, durationSeconds, null, onExpired);
    }

    /**
     * Lên lịch tự hủy tích hợp trực tiếp với model Message của hệ thống.
     *
     * @param message         Đối tượng Message
     * @param messageNode     Node giao diện của dòng tin nhắn
     * @param parentContainer Container chứa tin nhắn
     * @param durationSeconds Thời gian tồn tại tính bằng giây
     * @param onExpired       Callback nhận đối tượng Message khi hết hạn
     * @return ScheduledFuture đại diện cho tác vụ
     */
    public ScheduledFuture<?> schedule(Message message, Node messageNode, Pane parentContainer,
                                       long durationSeconds, Consumer<Message> onExpired) {
        Objects.requireNonNull(message, "message không được null");
        String messageId = message.getMsgId() != null ? message.getMsgId() : String.valueOf(message.getId());
        Runnable callback = onExpired != null ? () -> onExpired.accept(message) : null;
        return scheduleWithCountdown(messageId, messageNode, parentContainer, durationSeconds, null, callback);
    }

    /**
     * Lên lịch tự hủy đầy đủ với cập nhật đồng hồ đếm ngược từng giây (tick) và hành động hoàn tất.
     *
     * @param messageId       Mã định danh tin nhắn
     * @param messageNode     Node giao diện (nếu có)
     * @param parentContainer Container cha (nếu có)
     * @param durationSeconds Thời gian tồn tại (giây, phải > 0)
     * @param onTick          Callback cập nhật giây còn lại mỗi giây (có thể null)
     * @param onExpired       Callback khi hết hạn (có thể null)
     * @return ScheduledFuture đại diện cho tác vụ
     */
    public ScheduledFuture<?> scheduleWithCountdown(String messageId, Node messageNode, Pane parentContainer,
                                                    long durationSeconds, Consumer<Long> onTick, Runnable onExpired) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId không được rỗng.");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds phải lớn hơn 0.");
        }

        // Hủy tác vụ cũ nếu tin nhắn này đã được lên lịch trước đó
        cancel(messageId);

        AtomicLong remainingSeconds = new AtomicLong(durationSeconds);
        AtomicBoolean triggered = new AtomicBoolean(false);

        DisappearingTask taskInfo = new DisappearingTask(messageId, durationSeconds, remainingSeconds,
                messageNode, parentContainer, onTick, onExpired);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                long currentRemaining = remainingSeconds.decrementAndGet();

                if (currentRemaining > 0) {
                    if (onTick != null) {
                        runOnFxThread(() -> {
                            try {
                                onTick.accept(currentRemaining);
                            } catch (Throwable t) {
                                LOGGER.log(Level.FINE, "Lỗi khi chạy onTick callback cho tin nhắn " + messageId, t);
                            }
                        });
                    }
                } else {
                    // Đã hết thời gian (TTL = 0)
                    if (triggered.compareAndSet(false, true)) {
                        activeTasks.remove(messageId);
                        taskInfo.cancelFuture();
                        handleMessageExpiration(taskInfo);
                    }
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Lỗi trong chu kỳ đếm ngược của tin nhắn " + messageId, t);
            }
        }, 1, 1, TimeUnit.SECONDS);

        taskInfo.setFuture(future);
        activeTasks.put(messageId, taskInfo);

        // Kích hoạt tick ban đầu với số giây khởi điểm
        if (onTick != null) {
            runOnFxThread(() -> onTick.accept(durationSeconds));
        }

        return future;
    }

    /**
     * Gắn một huy hiệu đếm ngược tự hủy (Countdown Badge) trực tiếp vào dòng tin nhắn JavaFX,
     * tự động định dạng giao diện pill sắc nét và kích hoạt quy trình tự hủy.
     *
     * @param messageRow      Dòng tin nhắn (HBox do MessageCellFactory tạo ra)
     * @param messageId       Mã tin nhắn
     * @param durationSeconds Số giây tự hủy
     * @param parentContainer Container chứa tin nhắn
     * @param onExpired       Callback khi tự hủy thành công
     * @return Label hiển thị số giây còn lại
     */
    public Label attachCountdownBadge(HBox messageRow, String messageId, long durationSeconds,
                                      Pane parentContainer, Runnable onExpired) {
        Objects.requireNonNull(messageRow, "messageRow không được null");

        Label badge = new Label("⏳ " + durationSeconds + "s");
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #E11D48;"
                + " -fx-background-color: #FFE4E6; -fx-background-radius: 10px;"
                + " -fx-padding: 2px 7px; -fx-border-color: #FDA4AF; -fx-border-radius: 10px;");
        badge.setMouseTransparent(true);

        // Tìm bubble hoặc container con trong row để gắn badge đẹp mắt
        runOnFxThread(() -> {
            boolean attached = false;
            for (Node child : messageRow.getChildren()) {
                if (child instanceof HBox innerHBox) {
                    for (Node innerChild : innerHBox.getChildren()) {
                        if (innerChild instanceof VBox bubble) {
                            if (!bubble.getChildren().isEmpty() && bubble.getChildren().get(0) instanceof VBox contentBox) {
                                contentBox.getChildren().add(0, badge);
                            } else {
                                bubble.getChildren().add(0, badge);
                            }
                            attached = true;
                            break;
                        }
                    }
                }
                if (attached) break;
            }
            if (!attached) {
                messageRow.getChildren().add(badge);
            }
        });

        // Lên lịch với callback onTick để cập nhật số giây trên badge
        scheduleWithCountdown(messageId, messageRow, parentContainer, durationSeconds, remaining -> {
            badge.setText("⏳ " + remaining + "s");
            if (remaining <= 3) {
                // Nhấp nháy cảnh báo đỏ khi sắp hết giờ (<= 3s)
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;"
                        + " -fx-background-color: #E11D48; -fx-background-radius: 10px;"
                        + " -fx-padding: 2px 7px;");
            }
        }, onExpired);

        return badge;
    }

    /**
     * Xử lý hiệu ứng và xóa tin nhắn khi hết hạn.
     */
    private void handleMessageExpiration(DisappearingTask task) {
        Node node = task.getMessageNode();
        Pane parent = task.getParentContainer();
        Runnable callback = task.getOnExpired();

        if (node != null && parent != null) {
            runOnFxThread(() -> {
                try {
                    FadeTransition fade = new FadeTransition(Duration.millis(DEFAULT_FADE_DURATION_MS), node);
                    fade.setFromValue(1.0);
                    fade.setToValue(0.0);
                    fade.setOnFinished(e -> {
                        parent.getChildren().remove(node);
                        executeCallback(callback);
                    });
                    fade.play();
                } catch (Throwable t) {
                    // Dự phòng trường hợp lỗi animation hoặc môi trường headless
                    parent.getChildren().remove(node);
                    executeCallback(callback);
                }
            });
        } else {
            executeCallback(callback);
        }
    }

    private void executeCallback(Runnable callback) {
        if (callback != null) {
            try {
                callback.run();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Lỗi khi thực thi onExpired callback", t);
            }
        }
    }

    /**
     * Hủy bỏ đếm ngược tự hủy cho một tin nhắn cụ thể (ví dụ: người dùng thu hồi sớm hoặc ghim tin nhắn).
     *
     * @param messageId Mã định danh tin nhắn
     * @return true nếu tìm thấy và hủy thành công, false nếu không tồn tại
     */
    public boolean cancel(String messageId) {
        if (messageId == null) return false;
        DisappearingTask task = activeTasks.remove(messageId);
        if (task != null) {
            task.cancelFuture();
            return true;
        }
        return false;
    }

    /**
     * Hủy bỏ tất cả các bộ đếm tự hủy đang hoạt động.
     */
    public void cancelAll() {
        for (String id : activeTasks.keySet()) {
            cancel(id);
        }
        activeTasks.clear();
    }

    /**
     * Kiểm tra xem tin nhắn có đang trong hàng đợi tự hủy hay không.
     */
    public boolean isScheduled(String messageId) {
        return messageId != null && activeTasks.containsKey(messageId);
    }

    /**
     * Lấy số giây còn lại trước khi tin nhắn tự hủy.
     *
     * @param messageId Mã tin nhắn
     * @return Số giây còn lại, hoặc -1 nếu không tồn tại
     */
    public long getRemainingSeconds(String messageId) {
        if (messageId == null) return -1;
        DisappearingTask task = activeTasks.get(messageId);
        return task != null ? Math.max(0, task.getRemainingSeconds().get()) : -1;
    }

    /**
     * Lấy tổng số tin nhắn đang được theo dõi tự hủy.
     */
    public int getActiveCount() {
        return activeTasks.size();
    }

    /**
     * Đóng scheduler và giải phóng tài nguyên.
     */
    public void shutdown() {
        cancelAll();
        if (ownsScheduler && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Tiện ích thực thi an toàn trên JavaFX Application Thread.
     * Nếu không ở môi trường GUI JavaFX (như chạy Unit Test), thực thi trực tiếp trên luồng hiện tại.
     */
    public static void runOnFxThread(Runnable action) {
        if (action == null) return;
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException notInitialized) {
            // JavaFX Platform chưa được khởi động (chạy Unit Test tiêu chuẩn)
            action.run();
        }
    }

    /**
     * Thông tin lưu trữ nội bộ về tác vụ đếm ngược của một tin nhắn.
     */
    private static class DisappearingTask {
        private final String messageId;
        private final long totalSeconds;
        private final AtomicLong remainingSeconds;
        private final Node messageNode;
        private final Pane parentContainer;
        private final Consumer<Long> onTick;
        private final Runnable onExpired;
        private volatile ScheduledFuture<?> future;

        public DisappearingTask(String messageId, long totalSeconds, AtomicLong remainingSeconds,
                                Node messageNode, Pane parentContainer,
                                Consumer<Long> onTick, Runnable onExpired) {
            this.messageId = messageId;
            this.totalSeconds = totalSeconds;
            this.remainingSeconds = remainingSeconds;
            this.messageNode = messageNode;
            this.parentContainer = parentContainer;
            this.onTick = onTick;
            this.onExpired = onExpired;
        }

        public void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        public void cancelFuture() {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }

        public Node getMessageNode() { return messageNode; }
        public Pane getParentContainer() { return parentContainer; }
        public Runnable getOnExpired() { return onExpired; }
        public AtomicLong getRemainingSeconds() { return remainingSeconds; }
    }

    /**
     * Factory tạo daemon threads giúp ứng dụng thoát sạch sẽ mà không bị treo tiến trình.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int threadCount = 0;

        public DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + (++threadCount));
            t.setDaemon(true);
            return t;
        }
    }
}
