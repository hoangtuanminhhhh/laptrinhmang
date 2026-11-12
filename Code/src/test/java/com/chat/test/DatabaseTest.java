package com.chat.test;

import com.chat.server.db.DatabaseManager;
import com.chat.server.db.MessageRepository;
import com.chat.server.service.AuthService;
import com.chat.common.model.Message;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("--- ĐANG KHỞI TẠO DB ---");
        DatabaseManager.initialize();

        System.out.println("\n--- TEST AUTH SERVICE ---");
        AuthService authService = new AuthService();
        
        boolean regSuccess = authService.register("admin_test", "password123", "default_avatar.png");
        System.out.println("Đăng ký 'admin_test': " + (regSuccess ? "Thành công" : "Tài khoản đã tồn tại"));

        boolean isLoginSuccess = authService.login("admin_test", "password123");
        System.out.println("Test Đăng nhập đúng MK: " + (isLoginSuccess ? "PASS" : "FAIL"));

        boolean isLoginFail = authService.login("admin_test", "wrongpass");
        System.out.println("Test Đăng nhập sai MK (kỳ vọng báo lỗi): " + (!isLoginFail ? "PASS" : "FAIL"));

        System.out.println("\n--- TEST MESSAGE REPOSITORY ---");
        MessageRepository msgRepo = new MessageRepository();
        Message msg = new Message();
        msg.setMsgId("MSG_" + System.currentTimeMillis());
        msg.setSenderId(1);
        msg.setReceiverId(1);
        msg.setContent("Xin chao tu DatabaseTest!");
        
        boolean saveMsgSuccess = msgRepo.saveMessage(msg);
        System.out.println("Lưu tin nhắn mới: " + (saveMsgSuccess ? "PASS" : "FAIL"));
    }
}