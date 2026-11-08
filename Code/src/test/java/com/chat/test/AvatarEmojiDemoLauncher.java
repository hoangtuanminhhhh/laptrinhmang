package com.chat.test;

import javafx.application.Application;

/**
 * Lớp khởi chạy trung gian để chạy ứng dụng JavaFX từ classpath của IDE.
 * Java launcher không tự nhận diện lớp này là JavaFX Application, sau đó
 * Application.launch khởi tạo ứng dụng demo theo cơ chế chuẩn của JavaFX.
 */
public final class AvatarEmojiDemoLauncher {

    private AvatarEmojiDemoLauncher() {
    }

    /** Khởi chạy cửa sổ minh họa avatar và emoji. */
    public static void main(String[] args) {
        Application.launch(AvatarEmojiDemoApp.class, args);
    }
}
