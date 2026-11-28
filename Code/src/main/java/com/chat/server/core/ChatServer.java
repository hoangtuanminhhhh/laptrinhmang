package com.chat.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatServer {
    private static final int DEFAULT_QUEUE_CAPACITY = 200;
    private static final int SOCKET_READ_TIMEOUT_MS = 60_000; // Ngắt kết nối treo quá 60s không hoạt động

    private final int port;
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService monitorScheduler;
    private final ServerMetrics metrics;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public ChatServer(int port, int maxThreads) {
        this(port, maxThreads, new ServerMetrics());
    }

    public ChatServer(int port, int maxThreads, ServerMetrics metrics) {
        this.port = port;
        this.metrics = (metrics != null) ? metrics : new ServerMetrics();

        int safeMaxThreads = Math.max(4, maxThreads);
        int coreThreads = Math.max(2, safeMaxThreads / 2);

        ThreadFactory workerFactory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ChatServer-Worker-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        // Bounded ThreadPoolExecutor: Giới hạn luồng + hàng đợi có chặn trên để chống tràn RAM khi bị spam
        this.threadPool = new ThreadPoolExecutor(
                coreThreads,
                safeMaxThreads,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                workerFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.threadPool.allowCoreThreadTimeOut(true);

        // Luồng nền định kỳ mỗi 1 giây cập nhật mẫu lưu lượng & in thông số chịu tải
        this.monitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatServer-Metrics-Sampler");
            t.setDaemon(true);
            return t;
        });
    }

    public ServerMetrics getMetrics() {
        return metrics;
    }

    public int getActiveWorkerCount() {
        return threadPool.getActiveCount();
    }

    public int getQueueSize() {
        return threadPool.getQueue().size();
    }

    public static void main(String[] args) {
        int port = 8888;
        int maxThreads = 64; // Nâng sức chịu tải đồng thời lên 64 luồng + 200 hàng đợi

        System.out.println("[ChatServer] Đang khởi động Server tối ưu chịu tải trên cổng " + port + "...");
        ChatServer server = new ChatServer(port, maxThreads);
        server.start();
    }

    public void start() {
        try {
            // Backlog = 256 giúp OS kernel không rớt gói SYN khi nhiều client kết nối cùng lúc
            serverSocket = new ServerSocket(port, 256);
            isRunning = true;
            System.out.println("Server started on port " + port + " (MaxThreads=" + threadPool.getMaximumPoolSize()
                    + ", QueueCapacity=" + DEFAULT_QUEUE_CAPACITY + ")");

            // Lấy mẫu lưu lượng và CPU/RAM mỗi 1 giây
            monitorScheduler.scheduleAtFixedRate(() -> {
                try {
                    metrics.sampleThroughput();
                    if (metrics.getOnlineClients() > 0 || metrics.getMessagesPerSecond() > 0) {
                        System.out.println("[Monitor] " + metrics.getFormattedSummary()
                                + " | ActiveThreads: " + threadPool.getActiveCount()
                                + " | Queue: " + threadPool.getQueue().size());
                    }
                } catch (Exception ignored) {
                }
            }, 1, 1, TimeUnit.SECONDS);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                configureClientSocket(clientSocket);

                try {
                    threadPool.execute(() -> {
                        metrics.incrementOnlineClients();
                        try {
                            new ClientHandler(clientSocket, metrics).run();
                        } finally {
                            metrics.decrementOnlineClients();
                        }
                    });
                } catch (RejectedExecutionException rejected) {
                    // Server đang quá tải (đầy pool + đầy queue) -> từ chối kết nối an toàn, không crash
                    metrics.incrementRejectedConnections();
                    System.err.println("[ChatServer] Quá tải kết nối! Đã từ chối client: "
                            + clientSocket.getRemoteSocketAddress());
                    closeQuietly(clientSocket);
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Server exception: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void configureClientSocket(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void stop() {
        isRunning = false;
        metrics.setOnlineClients(0);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }

        if (monitorScheduler != null && !monitorScheduler.isShutdown()) {
            monitorScheduler.shutdownNow();
        }

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}