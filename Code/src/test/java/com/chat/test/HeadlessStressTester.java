package com.chat.test;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import com.chat.server.core.ChatServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HeadlessStressTester {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8888;
    public static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int SO_TIMEOUT_MS = 8000;

    /**
     * Định nghĩa các mức tải thử nghiệm chuẩn.
     */
    public enum LoadLevel {
        LIGHT("Mức 1: Tải nhẹ (Light Load)", 5, 20, 10),
        MEDIUM("Mức 2: Tải vừa (Medium Load)", 20, 50, 5),
        HIGH("Mức 3: Tải cao (High Load)", 50, 100, 2),
        EXTREME("Mức 4: Tải cực đại (Extreme Burst)", 100, 100, 0);

        private final String description;
        private final int clientCount;
        private final int messagesPerClient;
        private final int sendDelayMs;

        LoadLevel(String description, int clientCount, int messagesPerClient, int sendDelayMs) {
            this.description = description;
            this.clientCount = clientCount;
            this.messagesPerClient = messagesPerClient;
            this.sendDelayMs = sendDelayMs;
        }

        public String getDescription() { return description; }
        public int getClientCount() { return clientCount; }
        public int getMessagesPerClient() { return messagesPerClient; }
        public int getSendDelayMs() { return sendDelayMs; }
        public int getTotalMessages() { return clientCount * messagesPerClient; }
    }

    /**
     * Cấu hình bài test chịu tải.
     */
    public static class TestConfig {
        public String host = DEFAULT_HOST;
        public int port = DEFAULT_PORT;
        public int clientCount = 10;
        public int messagesPerClient = 50;
        public int sendDelayMs = 0;
        public String levelName = "Custom";

        public TestConfig() {}

        public TestConfig(LoadLevel level, String host, int port) {
            this.levelName = level.getDescription();
            this.clientCount = level.getClientCount();
            this.messagesPerClient = level.getMessagesPerClient();
            this.sendDelayMs = level.getSendDelayMs();
            this.host = host;
            this.port = port;
        }

        public int getTotalExpectedMessages() {
            return clientCount * messagesPerClient;
        }
    }

    /**
     * Kết quả đo lường và thống kê sau bài test.
     */
    public static class StressMetrics {
        public String levelName;
        public int concurrentClients;
        public int expectedMessages;
        public int sentCount = 0;
        public int successCount = 0;
        public int failureCount = 0;
        public long totalBytesTransferred = 0;
        public long durationMillis = 0;

        public double throughputMsgPerSec = 0.0;
        public double bandwidthKbPerSec = 0.0;

        public double minLatencyMs = 0.0;
        public double maxLatencyMs = 0.0;
        public double avgLatencyMs = 0.0;
        public double p50LatencyMs = 0.0;
        public double p90LatencyMs = 0.0;
        public double p99LatencyMs = 0.0;

        public final List<Double> latenciesMs = Collections.synchronizedList(new ArrayList<>());

        public void computeStatistics(long durationMs) {
            this.durationMillis = Math.max(1, durationMs);
            this.throughputMsgPerSec = (successCount * 1000.0) / this.durationMillis;
            this.bandwidthKbPerSec = (totalBytesTransferred / 1024.0) / (this.durationMillis / 1000.0);

            if (latenciesMs.isEmpty()) {
                return;
            }

            List<Double> sorted;
            synchronized (latenciesMs) {
                sorted = new ArrayList<>(latenciesMs);
            }
            Collections.sort(sorted);

            this.minLatencyMs = sorted.get(0);
            this.maxLatencyMs = sorted.get(sorted.size() - 1);

            double sum = 0.0;
            for (Double lat : sorted) {
                sum += lat;
            }
            this.avgLatencyMs = sum / sorted.size();
            this.p50LatencyMs = percentile(sorted, 50.0);
            this.p90LatencyMs = percentile(sorted, 90.0);
            this.p99LatencyMs = percentile(sorted, 99.0);
        }

        private static double percentile(List<Double> sorted, double percentile) {
            if (sorted.isEmpty()) return 0.0;
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }

        public double getSuccessRate() {
            return expectedMessages > 0 ? (successCount * 100.0 / expectedMessages) : 0.0;
        }

        public void printSummary() {
            System.out.println("┌────────────────────────────────────────────────────────────────────────┐");
            System.out.printf("│ %-70s │\n", "KẾT QUẢ ĐO ĐẠC: " + levelName);
            System.out.println("├────────────────────────────────────────────────────────────────────────┤");
            System.out.printf("│ Số Client đồng thời (Concurrency)     : %-30d │\n", concurrentClients);
            System.out.printf("│ Tổng tin nhắn kỳ vọng / đã gửi        : %-30s │\n", expectedMessages + " / " + sentCount);
            System.out.printf("│ Số tin phản hồi thành công (Success)  : %-30d │\n", successCount);
            System.out.printf("│ Số tin nhắn lỗi / thất bại (Failures) : %-30d │\n", failureCount);
            System.out.printf("│ Tỷ lệ thành công (Success Rate)       : %-30.2f │\n", getSuccessRate());
            System.out.printf("│ Thời gian chạy tổng cộng (Duration)   : %-30s │\n", durationMillis + " ms (" + String.format("%.2f", durationMillis / 1000.0) + " s)");
            System.out.printf("│ Thông lượng xử lý (Throughput)        : %-30s │\n", String.format("%.2f msgs/sec", throughputMsgPerSec));
            System.out.printf("│ Băng thông truyền tải (Bandwidth)     : %-30s │\n", String.format("%.2f KB/sec", bandwidthKbPerSec));
            System.out.println("├────────────────────────────────────────────────────────────────────────┤");
            System.out.println("│ PHÂN BỐ ĐỘ TRỄ PHẢN HỒI (Round-Trip Latency):                          │");
            System.out.printf("│   Nhỏ nhất (Min)                     : %-30s │\n", String.format("%.3f ms", minLatencyMs));
            System.out.printf("│   Trung bình (Avg)                   : %-30s │\n", String.format("%.3f ms", avgLatencyMs));
            System.out.printf("│   Trung vị (Median P50)              : %-30s │\n", String.format("%.3f ms", p50LatencyMs));
            System.out.printf("│   90th Percentile (P90)              : %-30s │\n", String.format("%.3f ms", p90LatencyMs));
            System.out.printf("│   99th Percentile (P99)              : %-30s │\n", String.format("%.3f ms", p99LatencyMs));
            System.out.printf("│   Lớn nhất (Max)                     : %-30s │\n", String.format("%.3f ms", maxLatencyMs));
            System.out.println("└────────────────────────────────────────────────────────────────────────┘\n");
        }
    }

    /**
     * Thực thi kiểm thử chịu tải theo cấu hình xác định.
     */
    public static StressMetrics runStressTest(TestConfig config) {
        StressMetrics metrics = new StressMetrics();
        metrics.levelName = config.levelName;
        metrics.concurrentClients = config.clientCount;
        metrics.expectedMessages = config.getTotalExpectedMessages();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(config.clientCount);

        AtomicInteger totalSent = new AtomicInteger(0);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFail = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(config.clientCount);

        for (int i = 0; i < config.clientCount; i++) {
            final int clientId = i + 1;
            executor.submit(() -> {
                Socket socket = null;
                DataOutputStream out = null;
                DataInputStream in = null;
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(SO_TIMEOUT_MS);
                    socket.setTcpNoDelay(true);

                    out = new DataOutputStream(socket.getOutputStream());
                    in = new DataInputStream(socket.getInputStream());

                    // Chờ tất cả luồng client kết nối sẵn sàng rồi đồng loạt bắn tải
                    startGate.await();

                    for (int m = 0; m < config.messagesPerClient; m++) {
                        String payloadText = String.format("STRESS_TEST#C%03d#M%04d#%d", clientId, m + 1, System.nanoTime());
                        Packet req = Packet.createEchoRequest("Client-" + clientId, payloadText);

                        long t0 = System.nanoTime();
                        byte[] bytesToSend = req.toBytes();
                        PacketFramer.writePacket(out, req);
                        totalSent.incrementAndGet();

                        Packet resp = PacketFramer.readPacket(in);
                        long t1 = System.nanoTime();

                        double latencyMs = (t1 - t0) / 1_000_000.0;
                        metrics.latenciesMs.add(latencyMs);

                        byte[] bytesReceived = resp.toBytes();
                        totalBytes.addAndGet(bytesToSend.length + bytesReceived.length);

                        if (resp.getType() == PacketType.ECHO_RESPONSE && payloadText.equals(resp.getPayload())) {
                            totalSuccess.incrementAndGet();
                        } else {
                            totalFail.incrementAndGet();
                        }

                        if (config.sendDelayMs > 0) {
                            Thread.sleep(config.sendDelayMs);
                        }
                    }
                } catch (Throwable t) {
                    totalFail.incrementAndGet();
                } finally {
                    try {
                        if (socket != null && !socket.isClosed()) socket.close();
                    } catch (IOException ignored) {}
                    finishGate.countDown();
                }
            });
        }

        // Bắt đầu bấm giờ đo đạc toàn bộ bài test
        long startTime = System.currentTimeMillis();
        startGate.countDown(); // Kích hoạt tất cả client cùng bắn dữ liệu

        try {
            boolean allFinished = finishGate.await(120, TimeUnit.SECONDS);
            if (!allFinished) {
                System.err.println("[CẢNH BÁO] Bài test vượt quá timeout 120 giây!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        executor.shutdownNow();

        metrics.sentCount = totalSent.get();
        metrics.successCount = totalSuccess.get();
        metrics.failureCount = totalFail.get();
        metrics.totalBytesTransferred = totalBytes.get();
        metrics.computeStatistics(endTime - startTime);

        return metrics;
    }

    /**
     * Chạy chuỗi benchmark đa mức tải tự động (Light -> Medium -> High -> Extreme).
     */
    public static List<StressMetrics> runMultiLevelBenchmark(String host, int port) {
        System.out.println("==========================================================================");
        System.out.println("   KHỞI CHẠY BỘ BENCHMARK HIỆU NĂNG ĐA MỨC TẢI (HEADLESS STRESS TESTER)   ");
        System.out.println("   Mục tiêu: Đánh giá khả năng chịu tải của Server Chat TCP UDM_08        ");
        System.out.println("   Máy chủ đích: " + host + ":" + port);
        System.out.println("==========================================================================\n");

        List<StressMetrics> suiteResults = new ArrayList<>();
        LoadLevel[] levels = LoadLevel.values();

        for (int i = 0; i < levels.length; i++) {
            LoadLevel level = levels[i];
            System.out.println(">>> Đang chạy " + level.getDescription() + "...");
            System.out.printf("    - Số Client: %d | Số tin/Client: %d | Tổng tin: %d | Delay: %d ms\n",
                    level.getClientCount(), level.getMessagesPerClient(), level.getTotalMessages(), level.getSendDelayMs());

            TestConfig config = new TestConfig(level, host, port);
            StressMetrics result = runStressTest(config);
            suiteResults.add(result);
            result.printSummary();

            // Nghỉ 1 giây giữa các bài test để Server giải phóng socket và ổn định CPU
            if (i < levels.length - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }

        printComparisonTable(suiteResults);
        return suiteResults;
    }

    /**
     * In bảng tổng kết so sánh các mức tải theo 
     */
    public static void printComparisonTable(List<StressMetrics> results) {
        System.out.println("\n=================================================================================================================");
        System.out.println("                      BẢNG TỔNG KẾT ĐO ĐẠC HIỆU NĂNG ĐA MỨC TẢI (STRESS TEST SUMMARY)                            ");
        System.out.println("=================================================================================================================");
        System.out.printf("| %-22s | %-7s | %-10s | %-9s | %-12s | %-10s | %-10s | %-9s |\n",
                "Mức Tải", "Clients", "Tổng Gói", "Thành công", "Thông lượng", "Avg Latency", "P99 Latency", "Tỷ Lệ Đạt");
        System.out.println("|------------------------|---------|------------|-----------|--------------|------------|------------|-----------|");

        for (StressMetrics m : results) {
            String shortName = m.levelName.replaceAll("Mức \\d+: ", "");
            System.out.printf("| %-22s | %-7d | %-10d | %-9d | %-12s | %-10s | %-10s | %-8.1f%% |\n",
                    shortName,
                    m.concurrentClients,
                    m.sentCount,
                    m.successCount,
                    String.format("%.1f msg/s", m.throughputMsgPerSec),
                    String.format("%.2f ms", m.avgLatencyMs),
                    String.format("%.2f ms", m.p99LatencyMs),
                    m.getSuccessRate());
        }
        System.out.println("=================================================================================================================\n");
    }

    /**
     * Kiểm tra xem cổng Server có đang mở hay không.
     */
    public static boolean isServerRunning(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Điểm nhập chính của chương trình CLI.
     */
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String levelChoice = "ALL";
        boolean autoStartServer = true;

        // Xử lý tham số dòng lệnh tùy biến
        for (int i = 0; i < args.length; i++) {
            if ("--host".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--level".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                levelChoice = args[++i].toUpperCase();
            } else if ("--no-server".equalsIgnoreCase(args[i])) {
                autoStartServer = false;
            }
        }

        ChatServer embeddedServer = null;
        Thread serverThread = null;

        // Nếu Server chưa chạy và cho phép tự khởi chạy
        if (!isServerRunning(host, port) && autoStartServer) {
            System.out.println("[HeadlessStressTester] Không phát hiện Server đang chạy trên cổng " + port);
            System.out.println("[HeadlessStressTester] Tự động khởi động Embedded ChatServer (maxThreads = 100) để phục vụ test...");
            final int serverPort = port;
            embeddedServer = new ChatServer(serverPort, 100);
            final ChatServer s = embeddedServer;
            serverThread = new Thread(s::start, "EmbeddedStressServer");
            serverThread.setDaemon(true);
            serverThread.start();

            // Chờ server khởi động hoàn tất
            for (int wait = 0; wait < 10; wait++) {
                if (isServerRunning(host, port)) break;
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }

        try {
            if (!isServerRunning(host, port)) {
                System.err.println("[LỖI] Không thể kết nối tới Server Chat tại " + host + ":" + port);
                System.err.println("Vui lòng khởi động Server trước hoặc kiểm tra cấu hình cổng.");
                System.exit(1);
            }

            if ("LIGHT".equals(levelChoice)) {
                StressMetrics res = runStressTest(new TestConfig(LoadLevel.LIGHT, host, port));
                res.printSummary();
            } else if ("MEDIUM".equals(levelChoice)) {
                StressMetrics res = runStressTest(new TestConfig(LoadLevel.MEDIUM, host, port));
                res.printSummary();
            } else if ("HIGH".equals(levelChoice)) {
                StressMetrics res = runStressTest(new TestConfig(LoadLevel.HIGH, host, port));
                res.printSummary();
            } else if ("EXTREME".equals(levelChoice)) {
                StressMetrics res = runStressTest(new TestConfig(LoadLevel.EXTREME, host, port));
                res.printSummary();
            } else {
                // Mặc định chạy toàn bộ chuỗi đa mức tải
                runMultiLevelBenchmark(host, port);
            }
        } finally {
            if (embeddedServer != null) {
                System.out.println("[HeadlessStressTester] Dọn dẹp và dừng Embedded Server.");
                embeddedServer.stop();
            }
        }
    }
}
