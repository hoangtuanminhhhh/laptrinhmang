package com.chat.server;

import com.chat.server.core.ChatServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerApp {
    public static void main(String[] args) {
        int port = 5000;
        int maxThreads = 50;
        String dbPath = "data/chat.db";

        // Đọc cấu hình từ server.properties
        try (InputStream input = ServerApp.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);

                port = Integer.parseInt(prop.getProperty("server.port", "5000"));
                dbPath = prop.getProperty("database.path", "data/chat.db");

                System.out.println("[Config] Loaded Port: " + port);
                System.out.println("[Config] Loaded DB Path: " + dbPath + " (Sẵn sàng cho Thành viên 2)");
            } else {
                System.out.println("[Config] Không tìm thấy server.properties ở src/main/resources, dùng cấu hình mặc định.");
            }
        } catch (IOException | NumberFormatException ex) {
            System.err.println("Lỗi đọc cấu hình, dùng giá trị mặc định. Chi tiết: " + ex.getMessage());
        }

        ChatServer server = new ChatServer(port, maxThreads);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            server.stop();
        }));

        server.start();
    }
}