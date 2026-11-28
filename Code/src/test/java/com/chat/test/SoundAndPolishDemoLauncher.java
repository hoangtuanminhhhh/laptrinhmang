package com.chat.test;

import javafx.application.Application;

/**
 * Lớp khởi chạy trung gian để chạy SoundAndPolishDemoApp từ classpath của IDE.
 * Dùng class này thay vì chạy trực tiếp SoundAndPolishDemoApp để tránh lỗi
 * "JavaFX runtime components are missing" khi chạy từ IntelliJ.
 */
public final class SoundAndPolishDemoLauncher {

    private SoundAndPolishDemoLauncher() { }

    public static void main(String[] args) {
        Application.launch(SoundAndPolishDemoApp.class, args);
    }
}
