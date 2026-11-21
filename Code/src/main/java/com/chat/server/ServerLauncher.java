package com.chat.server;

import com.chat.server.core.ChatServer; // <-- THÊM
import com.chat.server.core.ServerMetrics;
import com.chat.server.ui.ServerMonitorApp;
import javafx.application.Application;

public class ServerLauncher {
    public static void main(String[] args) {
        ServerMetrics metrics = new ServerMetrics();
        ServerMonitorApp.attachMetrics(metrics);

        // <-- THÊM: chạy ChatServer trên luồng nền dùng chung metrics
        ChatServer server = new ChatServer(8888, 10, metrics);
        Thread serverThread = new Thread(server::start, "chat-server-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        Application.launch(ServerMonitorApp.class, args);
    }
}