package com.chat.client.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ quản lý tự động kết nối lại (Auto-reconnect) cho Client (Thành viên 3).
 *
 * Khi kết nối TCP bị mất ngoài ý muốn (Server sập, đứt cáp, hoặc Heartbeat quá 3 chu kỳ
 * không nhận được PONG), ReconnectManager chạy trên luồng nền Daemon "Client-Reconnect-Worker"
 * để thử kết nối lại theo chiến lược Exponential Backoff có Jitter (hệ số nhân mặc định 2.0,
 * dao động ngẫu nhiên mặc định ±20%, từ 2s lên tối đa 30s).
 *
 * Tuyệt đối không chạy trên JavaFX Application Thread.
 */
public class ReconnectManager {

    public static final boolean DEFAULT_ENABLED = true;
    public static final long DEFAULT_INITIAL_DELAY_MS = 2_000L;
    public static final long DEFAULT_MAX_DELAY_MS = 30_000L;
    public static final int DEFAULT_MAX_ATTEMPTS = 0; // 0 = thử lại không giới hạn
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER_RATIO = 0.2;
    public static final double MAX_JITTER_RATIO = 0.5;

    private final NetworkService service;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger attempt = new AtomicInteger(0);

    private volatile boolean enabled = DEFAULT_ENABLED;
    private volatile long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
    private volatile long maxDelayMs = DEFAULT_MAX_DELAY_MS;
    private volatile int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private volatile double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
    private volatile double jitterRatio = DEFAULT_JITTER_RATIO;
    private volatile boolean shutDown = false;
    private volatile long lastScheduledDelayMs = 0L;
    private volatile long nextAttemptAtMs = 0L;

    private long sessionId = 0L;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingTask;

    public ReconnectManager(NetworkService service) {
        this.service = service;
    }

    /**
     * Cấu hình tham số reconnect với hệ số nhân mặc định (2.0) và không jitter
     * để giữ tương thích cho các caller/test xác định thời gian chờ cố định.
     */
    public synchronized void configure(boolean enabled, long initialDelayMs, long maxDelayMs, int maxAttempts) {
        configure(enabled, initialDelayMs, maxDelayMs, maxAttempts, DEFAULT_BACKOFF_MULTIPLIER, 0.0);
    }

    /**
     * Cấu hình đầy đủ tham số Exponential Backoff kèm hệ số nhân và tỷ lệ Jitter.
     */
    public synchronized void configure(boolean enabled, long initialDelayMs, long maxDelayMs, int maxAttempts,
                                       double backoffMultiplier, double jitterRatio) {
        this.enabled = enabled;
        this.initialDelayMs = Math.max(1L, initialDelayMs);
        this.maxDelayMs = Math.max(this.initialDelayMs, maxDelayMs);
        this.maxAttempts = Math.max(0, maxAttempts);
        this.backoffMultiplier = Double.isFinite(backoffMultiplier)
                ? Math.max(1.0, backoffMultiplier)
                : DEFAULT_BACKOFF_MULTIPLIER;
        this.jitterRatio = Double.isFinite(jitterRatio)
                ? Math.min(MAX_JITTER_RATIO, Math.max(0.0, jitterRatio))
                : DEFAULT_JITTER_RATIO;
    }

    /**
     * Bắt đầu vòng lặp tự động kết nối lại theo Exponential Backoff nếu đang rảnh.
     */
    public void start() {
        long mySession;
        synchronized (this) {
            if (!enabled || shutDown || !active.compareAndSet(false, true)) {
                return;
            }
            attempt.set(0);
            lastScheduledDelayMs = 0L;
            nextAttemptAtMs = 0L;
            mySession = ++sessionId;
            ensureScheduler();
        }
        scheduleNext(mySession, false);
    }

    /**
     * Hủy lịch chờ hiện tại và kích hoạt một chu kỳ thử kết nối lại ngay lập tức (delay = 0ms).
     *
     * @return true nếu đã lên lịch thử lại, false nếu bị tắt hoặc đã shutdown
     */
    public boolean retryNow() {
        long mySession;
        synchronized (this) {
            if (!enabled || shutDown) {
                return false;
            }
            if (pendingTask != null) {
                pendingTask.cancel(false);
                pendingTask = null;
            }
            active.set(true);
            attempt.set(0);
            lastScheduledDelayMs = 0L;
            nextAttemptAtMs = 0L;
            mySession = ++sessionId;
            ensureScheduler();
        }
        scheduleNext(mySession, true);
        return true;
    }

    /**
     * Hủy mọi tác vụ reconnect đang chờ và vô hiệu hóa session hiện tại.
     */
    public synchronized void cancel() {
        active.set(false);
        sessionId++;
        lastScheduledDelayMs = 0L;
        nextAttemptAtMs = 0L;
        if (pendingTask != null) {
            pendingTask.cancel(false);
            pendingTask = null;
        }
    }

    /**
     * Hủy lịch hiện tại và đặt lại bộ đếm số lần thử về 0.
     */
    public synchronized void reset() {
        cancel();
        attempt.set(0);
    }

    /**
     * Dừng hẳn ReconnectManager và giải phóng luồng nền khi tắt ứng dụng.
     */
    public synchronized void shutdown() {
        shutDown = true;
        cancel();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Tính độ trễ cơ sở theo cấp số nhân (chưa cộng jitter) cho lần thử thứ {@code attemptNumber}.
     */
    public long delayFor(int attemptNumber) {
        if (attemptNumber <= 1) {
            return Math.min(initialDelayMs, maxDelayMs);
        }
        double delay = initialDelayMs;
        double multiplier = backoffMultiplier;
        long max = maxDelayMs;
        for (int i = 1; i < attemptNumber && delay < max; i++) {
            delay *= multiplier;
        }
        return Math.min(max, Math.max(1L, Math.round(delay)));
    }

    /**
     * Tính độ trễ thực tế cho lần thử thứ {@code attemptNumber} sau khi áp dụng jitter ngẫu nhiên.
     */
    public long nextDelayFor(int attemptNumber) {
        long baseDelay = delayFor(attemptNumber);
        return applyJitter(baseDelay, ThreadLocalRandom.current().nextDouble());
    }

    long applyJitter(long baseDelayMs, double randomUnit) {
        double ratio = this.jitterRatio;
        if (ratio <= 0.0 || baseDelayMs <= 1L) {
            return Math.min(maxDelayMs, Math.max(1L, baseDelayMs));
        }
        double clampedUnit = Double.isFinite(randomUnit) ? Math.min(1.0, Math.max(0.0, randomUnit)) : 0.5;
        double delta = (2.0 * clampedUnit - 1.0) * ratio;
        long jittered = Math.round(baseDelayMs * (1.0 + delta));
        return Math.min(maxDelayMs, Math.max(1L, jittered));
    }

    private void ensureScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Client-Reconnect-Worker");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private void scheduleNext(long expectedSession, boolean immediate) {
        int currentAttempt;
        long delay = 0L;
        boolean gaveUp = false;

        synchronized (this) {
            if (!active.get() || shutDown || expectedSession != sessionId
                    || scheduler == null || scheduler.isShutdown()) {
                return;
            }
            currentAttempt = attempt.incrementAndGet();
            if (maxAttempts > 0 && currentAttempt > maxAttempts) {
                active.set(false);
                pendingTask = null;
                lastScheduledDelayMs = 0L;
                nextAttemptAtMs = 0L;
                gaveUp = true;
            } else {
                delay = immediate ? 0L : nextDelayFor(currentAttempt);
                lastScheduledDelayMs = delay;
                nextAttemptAtMs = System.currentTimeMillis() + delay;
            }
        }

        if (gaveUp) {
            service.notifyReconnectGaveUp();
            return;
        }

        // Bắn sự kiện trước khi schedule để onReconnecting luôn đi trước onReconnected kể cả khi delay == 0.
        service.notifyReconnecting(currentAttempt, delay);

        synchronized (this) {
            if (!active.get() || shutDown || expectedSession != sessionId
                    || scheduler == null || scheduler.isShutdown()) {
                return;
            }
            pendingTask = scheduler.schedule(() -> attemptOnce(expectedSession), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void attemptOnce(long expectedSession) {
        synchronized (this) {
            if (!active.get() || shutDown || expectedSession != sessionId) {
                return;
            }
            pendingTask = null;
        }

        boolean done;
        try {
            done = service.reconnectNow();
        } catch (RuntimeException e) {
            System.err.println("[ReconnectManager] Lỗi khi thử kết nối lại: " + e.getMessage());
            done = false;
        }

        boolean shouldRestartIfNeeded = false;
        boolean shouldScheduleNext = false;
        synchronized (this) {
            if (!active.get() || shutDown || expectedSession != sessionId) {
                return;
            }
            if (done) {
                active.set(false);
                attempt.set(0);
                lastScheduledDelayMs = 0L;
                nextAttemptAtMs = 0L;
                shouldRestartIfNeeded = true;
            } else {
                shouldScheduleNext = true;
            }
        }

        if (shouldRestartIfNeeded) {
            service.startReconnectIfNeeded();
        } else if (shouldScheduleNext) {
            scheduleNext(expectedSession, false);
        }
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCurrentAttempt() {
        return attempt.get();
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public long getLastScheduledDelayMs() {
        return lastScheduledDelayMs;
    }

    public long getNextAttemptAtMs() {
        return nextAttemptAtMs;
    }
}
