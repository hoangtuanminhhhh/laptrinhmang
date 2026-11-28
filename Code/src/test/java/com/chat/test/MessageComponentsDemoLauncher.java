package com.chat.test;

import javafx.application.Application;

/**
 * Lớp khởi chạy trung gian để chạy MessageComponentsDemoApp từ classpath của IDE.
 * Dùng class này thay vì chạy trực tiếp MessageComponentsDemoApp để tránh lỗi
 * "JavaFX runtime components are missing" khi chạy từ IntelliJ.
 */
public final class MessageComponentsDemoLauncher {

    private MessageComponentsDemoLauncher() { }

    public static void main(String[] args) {
        Application.launch(MessageComponentsDemoApp.class, args);
    }
}
