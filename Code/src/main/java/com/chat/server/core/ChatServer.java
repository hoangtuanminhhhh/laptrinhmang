package com.chat.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private final int port;
    private final ExecutorService threadPool;
    private final ServerMetrics metrics; // <-- THÊM
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public ChatServer(int port, int maxThreads) {
        this(port, maxThreads, new ServerMetrics()); // <-- SỬA: gọi constructor có metrics
    }

    // <-- THÊM constructor này để nhận metrics từ bên ngoài (nếu có)
    public ChatServer(int port, int maxThreads, ServerMetrics metrics) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(maxThreads);
        this.metrics = (metrics != null) ? metrics : new ServerMetrics();
    }

    // <-- THÊM getter để lấy metrics nếu cần
    public ServerMetrics getMetrics() {
        return metrics;
    }

    /**
     * Phương thức main để chạy Server trực tiếp qua dòng lệnh Maven.
     */
    public static void main(String[] args) {
        int port = 8888; // Cổng mặc định khớp với cấu hình client
        int maxThreads = 10; // Số lượng luồng tối đa trong pool
        
        System.out.println("[ChatServer] Đang khởi động Server trên cổng " + port + "...");
        ChatServer server = new ChatServer(port, maxThreads);
        server.start();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("Server started on port " + port);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                metrics.setOnlineClients(metrics.getOnlineClients() + 1); // <-- THÊM
                threadPool.execute(() -> { // <-- SỬA: bọc ClientHandler để giảm số client khi thoát
                    try {
                        new ClientHandler(clientSocket, metrics).run();
                    } finally {
                        metrics.setOnlineClients(Math.max(0, metrics.getOnlineClients() - 1));
                    }
                });
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Server exception: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        metrics.setOnlineClients(0); // <-- THÊM
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
}