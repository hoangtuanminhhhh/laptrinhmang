package com.chat.test;

import com.chat.server.db.DatabaseManager;
import com.chat.server.service.AuthService;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("--- ĐANG KHỞI TẠO DB ---");
        DatabaseManager.initialize();

        System.out.println("\n--- TEST AUTH SERVICE ---");
        AuthService authService = new AuthService();
        
        boolean isRegistered = authService.register("admin_test", "123456", "default_avatar.png");
        System.out.println("Đăng ký 'admin_test': " + (isRegistered ? "Thành công" : "Thất bại/Đã tồn tại"));

        boolean isLoginSuccess = authService.login("admin_test", "123456");
        System.out.println("Test Đăng nhập đúng MK: " + (isLoginSuccess ? "PASS" : "FAIL"));

        boolean isLoginFail = authService.login("admin_test", "wrongpassword");
        System.out.println("Test Đăng nhập sai MK (kỳ vọng báo lỗi): " + (isLoginFail ? "FAIL" : "PASS"));
    }
}