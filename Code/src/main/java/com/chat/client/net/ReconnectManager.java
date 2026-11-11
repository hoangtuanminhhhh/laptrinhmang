package com.chat.client.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ quản lý tự động kết nối lại (Auto-reconnect) cho Client (Thành viên 3).
 *
 * Khi kết nối TCP bị mất ngoài ý muốn (Server sập, đứt cáp, hoặc Heartbeat quá 3 chu kỳ
 * không nhận được PONG), ReconnectManager chạy trên luồng nền Daemon "Client-Reconnect-Worker"
 * để thử kết nối lại theo chiến lược Exponential Backoff (chờ tăng gấp đôi sau mỗi lần thất bại,
 * mặc định từ 2s lên tối đa 30s).
 *
 * Tuyệt đối không chạy trên JavaFX Application Thread.
 */
public class ReconnectManager {

    public static final boolean DEFAULT_ENABLED = true;
    public static final long DEFAULT_INITIAL_DELAY_MS = 2_000L;
    public static final long DEFAULT_MAX_DELAY_MS = 30_000L;
    public static final int DEFAULT_MAX_ATTEMPTS = 0; // 0 = thử lại không giới hạn

    private final NetworkService service;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger attempt = new AtomicInteger(0);

    private volatile boolean enabled = DEFAULT_ENABLED;
    private volatile long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
    private volatile long maxDelayMs = DEFAULT_MAX_DELAY_MS;
    private volatile int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingTask;

    public ReconnectManager(NetworkService service) {
        this.service = service;
    }

    public synchronized void configure(boolean enabled, long initialDelayMs, long maxDelayMs, int maxAttempts) {
        this.enabled = enabled;
        this.initialDelayMs = Math.max(1L, initialDelayMs);
        this.maxDelayMs = Math.max(this.initialDelayMs, maxDelayMs);
        this.maxAttempts = Math.max(0, maxAttempts);
    }

    public synchronized void start() {
        if (!enabled || !active.compareAndSet(false, true)) {
            return;
        }
        attempt.set(0);
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Client-Reconnect-Worker");
                t.setDaemon(true);
                return t;
            });
        }
        scheduleNext();
    }

    public synchronized void cancel() {
        active.set(false);
        if (pendingTask != null) {
            pendingTask.cancel(false);
            pendingTask = null;
        }
    }

    public synchronized void shutdown() {
        cancel();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public long delayFor(int attemptNumber) {
        long delay = initialDelayMs;
        for (int i = 1; i < attemptNumber && delay < maxDelayMs; i++) {
            delay = (delay > maxDelayMs / 2) ? maxDelayMs : delay * 2;
        }
        return Math.min(delay, maxDelayMs);
    }

    private synchronized void scheduleNext() {
        if (!active.get() || scheduler == null || scheduler.isShutdown()) {
            return;
        }
        int currentAttempt = attempt.incrementAndGet();
        if (maxAttempts > 0 && currentAttempt > maxAttempts) {
            active.set(false);
            service.notifyReconnectGaveUp();
            return;
        }
        long delay = delayFor(currentAttempt);
        service.notifyReconnecting(currentAttempt, delay);
        pendingTask = scheduler.schedule(this::attemptOnce, delay, TimeUnit.MILLISECONDS);
    }

    private void attemptOnce() {
        if (!active.get()) {
            return;
        }
        boolean done;
        try {
            done = service.reconnectNow();
        } catch (RuntimeException e) {
            System.err.println("[ReconnectManager] Lỗi khi thử kết nối lại: " + e.getMessage());
            done = false;
        }

        synchronized (this) {
            if (!active.get()) {
                return;
            }
            if (done) {
                active.set(false);
                service.startReconnectIfNeeded();
            } else {
                scheduleNext();
            }
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
}
