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
        
        // 1. Test Đăng ký (Truyền đủ 3 tham số: username, password, avatar)
        boolean regSuccess = authService.register("admin_test", "password123", "default_avatar.png");
        System.out.println("Đăng ký 'admin_test': " + (regSuccess ? "Thành công" : "Tài khoản đã tồn tại/Thất bại"));

        // 2. Test Đăng nhập đúng mật khẩu (Nhận kiểu boolean)
        boolean isLoginSuccess = authService.login("admin_test", "password123");
        if (isLoginSuccess) {
            System.out.println("Test Đăng nhập đúng MK: PASS");
        } else {
            System.out.println("Test Đăng nhập đúng MK: FAIL");
        }

        // 3. Test Đăng nhập sai mật khẩu (Kỳ vọng trả về false)
        boolean isLoginFail = authService.login("admin_test", "wrongpass");
        if (!isLoginFail) {
            System.out.println("Test Đăng nhập sai MK (kỳ vọng báo lỗi): PASS");
        } else {
            System.out.println("Test Đăng nhập sai MK: FAIL");
        }

        System.out.println("\n--- TEST MESSAGE REPOSITORY (TUẦN 3-4) ---");
        MessageRepository msgRepo = new MessageRepository();
        Message msg = new Message();
        msg.setMsgId("MSG_" + System.currentTimeMillis());
        msg.setSenderId(1);
        msg.setReceiverId(2);
        msg.setContent("Test ket noi CSDL Tuần 3-4");
        
        boolean saveMsgSuccess = msgRepo.saveMessage(msg);
        System.out.println("Lưu tin nhắn mới: " + (saveMsgSuccess ? "PASS" : "FAIL"));
    }
}