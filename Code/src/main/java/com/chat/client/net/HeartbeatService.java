package com.chat.client.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bộ đếm thời gian Heartbeat Ping/Pong (Thành viên 3).
 * - Tự động gửi gói PING định kỳ mỗi 15 giây (theo PROTOCOL_SPEC) trên luồng nền Daemon.
 * - Giám sát phản hồi PONG từ Server: nếu quá 3 chu kỳ liên tiếp không nhận được PONG
 *   thì kích hoạt cờ mất mạng (networkLost = true) và gọi callback báo mất kết nối.
 * - Tuyệt đối không chạy trên JavaFX Application Thread.
 */
public class HeartbeatService {

    /** Chu kỳ gửi gói PING mặc định: 15 giây (15.000 ms). */
    public static final long DEFAULT_INTERVAL_MS = 15_000L;

    /** Số chu kỳ PING liên tiếp tối đa không nhận được PONG trước khi kích hoạt cờ mất mạng. */
    public static final int DEFAULT_MAX_MISSED = 3;

    private final long intervalMs;
    private final int maxMissedPongs;
    private final Runnable pingSender;
    private final Runnable onConnectionLost;

    private final AtomicInteger unansweredPings = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean networkLost = new AtomicBoolean(false);
    private final AtomicBoolean lostNotified = new AtomicBoolean(false);

    private final AtomicLong lastPingSentAt = new AtomicLong(0L);
    private final AtomicLong lastPongReceivedAt = new AtomicLong(0L);
    private final AtomicLong lastRttMs = new AtomicLong(-1L);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;

    public HeartbeatService(Runnable pingSender, Runnable onConnectionLost) {
        this(DEFAULT_INTERVAL_MS, DEFAULT_MAX_MISSED, pingSender, onConnectionLost);
    }

    public HeartbeatService(long intervalMs, int maxMissedPongs, Runnable pingSender, Runnable onConnectionLost) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs phải lớn hơn 0");
        }
        if (maxMissedPongs <= 0) {
            throw new IllegalArgumentException("maxMissedPongs phải lớn hơn 0");
        }
        this.intervalMs = intervalMs;
        this.maxMissedPongs = maxMissedPongs;
        this.pingSender = pingSender;
        this.onConnectionLost = onConnectionLost;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        unansweredPings.set(0);
        networkLost.set(false);
        lostNotified.set(false);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Client-Heartbeat-Timer");
            t.setDaemon(true);
            return t;
        });
        heartbeatTask = scheduler.scheduleAtFixedRate(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running.set(false);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    public void onPongReceived() {
        long now = System.currentTimeMillis();
        lastPongReceivedAt.set(now);
        long sentAt = lastPingSentAt.get();
        if (sentAt > 0 && now >= sentAt) {
            lastRttMs.set(now - sentAt);
        }
        unansweredPings.set(0);
        networkLost.set(false);
    }

    public void onServerActivity() {
        unansweredPings.set(0);
        networkLost.set(false);
    }

    private void tick() {
        if (!running.get()) {
            return;
        }
        try {
            if (unansweredPings.get() >= maxMissedPongs) {
                networkLost.set(true);
                if (lostNotified.compareAndSet(false, true)) {
                    stop();
                    if (onConnectionLost != null) {
                        onConnectionLost.run();
                    }
                }
                return;
            }
            unansweredPings.incrementAndGet();
            lastPingSentAt.set(System.currentTimeMillis());
            if (pingSender != null) {
                pingSender.run();
            }
        } catch (RuntimeException e) {
            System.err.println("[HeartbeatService] Lỗi trong chu kỳ Heartbeat: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isNetworkLost() {
        return networkLost.get();
    }

    public int getUnansweredPings() {
        return unansweredPings.get();
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public int getMaxMissedPongs() {
        return maxMissedPongs;
    }

    public long getLastRttMs() {
        return lastRttMs.get();
    }

    public long getLastPongReceivedAt() {
        return lastPongReceivedAt.get();
    }
}
