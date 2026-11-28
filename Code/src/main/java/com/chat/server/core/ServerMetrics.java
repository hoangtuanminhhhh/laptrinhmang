package com.chat.server.core;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ServerMetrics {
    private final AtomicInteger onlineClients = new AtomicInteger(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong rejectedConnections = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong bytesTransferred = new AtomicLong(0);

    // Biến phục vụ đo lưu lượng tức thời (msg/s và bytes/s)
    private volatile long lastSampleTimeMs = System.currentTimeMillis();
    private volatile long lastSampleMessages = 0L;
    private volatile long lastSampleBytes = 0L;
    private volatile double messagesPerSecond = 0.0;
    private volatile double bytesPerSecond = 0.0;

    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    // =========================================================================
    // QUẢN LÝ KẾT NỐI AN TOÀN ĐA LUỒNG (ATOMIC)
    // =========================================================================

    public int incrementOnlineClients() {
        totalConnections.incrementAndGet();
        return onlineClients.incrementAndGet();
    }

    public int decrementOnlineClients() {
        return onlineClients.updateAndGet(curr -> Math.max(0, curr - 1));
    }

    public int getOnlineClients() {
        return onlineClients.get();
    }

    public void setOnlineClients(int count) {
        onlineClients.set(Math.max(0, count));
    }

    public long getTotalConnections() {
        return totalConnections.get();
    }

    public void incrementRejectedConnections() {
        rejectedConnections.incrementAndGet();
    }

    public long getRejectedConnections() {
        return rejectedConnections.get();
    }

    // =========================================================================
    // THỐNG KÊ LƯU LƯỢNG TIN NHẮN & BĂNG THÔNG THỜI GIAN THỰC
    // =========================================================================

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public long getBytesTransferred() {
        return bytesTransferred.get();
    }

    public void addBytesTransferred(long bytes) {
        if (bytes > 0) {
            bytesTransferred.addAndGet(bytes);
        }
    }

    /**
     * Lấy mẫu lưu lượng để tính tốc độ msg/s và bytes/s tại thời điểm hiện tại.
     */
    public synchronized void sampleThroughput() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastSampleTimeMs;
        if (elapsedMs <= 0) {
            return;
        }
        long currentMsgs = messagesSent.get();
        long currentBytes = bytesTransferred.get();

        double elapsedSec = elapsedMs / 1000.0;
        this.messagesPerSecond = (currentMsgs - lastSampleMessages) / elapsedSec;
        this.bytesPerSecond = (currentBytes - lastSampleBytes) / elapsedSec;

        this.lastSampleTimeMs = now;
        this.lastSampleMessages = currentMsgs;
        this.lastSampleBytes = currentBytes;
    }

    public double getMessagesPerSecond() {
        return messagesPerSecond;
    }

    public double getBytesPerSecond() {
        return bytesPerSecond;
    }

    // =========================================================================
    // ĐO TÀI NGUYÊN HỆ THỐNG (CPU & RAM)
    // =========================================================================

    /**
     * Trả về mức sử dụng CPU (%) của tiến trình Server (0.0 - 100.0).
     */
    public double getProcessCpuLoadPercent() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getProcessCpuLoad();
            if (load >= 0.0) {
                return Math.min(100.0, load * 100.0);
            }
        }
        // Fallback nếu JVM không hỗ trợ com.sun.management
        double sysAvg = osBean.getSystemLoadAverage();
        if (sysAvg >= 0.0) {
            int cores = Math.max(1, osBean.getAvailableProcessors());
            return Math.min(100.0, (sysAvg / cores) * 100.0);
        }
        return 0.0;
    }

    /**
     * Trả về mức sử dụng CPU (%) toàn hệ thống (0.0 - 100.0).
     */
    public double getSystemCpuLoadPercent() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getCpuLoad();
            if (load >= 0.0) {
                return Math.min(100.0, load * 100.0);
            }
        }
        return getProcessCpuLoadPercent();
    }

    /**
     * Trả về lượng RAM JVM đang sử dụng (tính theo MB).
     */
    public double getUsedMemoryMB() {
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        return usedBytes / (1024.0 * 1024.0);
    }

    /**
     * Trả về lượng RAM tối đa cấp phát cho JVM (tính theo MB).
     */
    public double getMaxMemoryMB() {
        long maxBytes = Runtime.getRuntime().maxMemory();
        return maxBytes / (1024.0 * 1024.0);
    }

    public String getFormattedSummary() {
        return String.format(
                "Online: %d | Msg: %d (%.1f msg/s) | Traffic: %.1f KB/s | CPU: %.1f%% | RAM: %.1f/%.0f MB | Rejected: %d",
                getOnlineClients(),
                getMessagesSent(),
                getMessagesPerSecond(),
                getBytesPerSecond() / 1024.0,
                getProcessCpuLoadPercent(),
                getUsedMemoryMB(),
                getMaxMemoryMB(),
                getRejectedConnections()
        );
    }
}