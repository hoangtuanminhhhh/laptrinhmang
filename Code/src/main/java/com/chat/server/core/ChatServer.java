package com.chat.server.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private final int port;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    public ChatServer(int port, int maxThreads) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(maxThreads);
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
                threadPool.execute(new ClientHandler(clientSocket));
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