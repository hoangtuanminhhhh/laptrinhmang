package com.chat.server.service;

import com.chat.common.model.User;
import com.chat.server.db.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthService {
    private final UserRepository userRepository;

    public AuthService() {
        this.userRepository = new UserRepository();
    }

    // Hàm băm mật khẩu bằng SHA-256 (Tích hợp sẵn trong JDK, không cần thư viện ngoài)
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi mã hóa mật khẩu", e);
        }
    }

    // Hàm xử lý Đăng Ký
    public boolean register(String username, String rawPassword, String avatar) {
        if (userRepository.getUserByUsername(username) != null) {
            System.err.println("Tên đăng nhập đã tồn tại!");
            return false;
        }

        String hashedPassword = hashPassword(rawPassword);
        User newUser = new User(username, hashedPassword, avatar);
        return userRepository.insertUser(newUser);
    }

    // Hàm xử lý Đăng Nhập
    public boolean login(String username, String rawPassword) {
        User user = userRepository.getUserByUsername(username);
        
        if (user == null) {
            System.err.println("Tài khoản không tồn tại!");
            return false;
        }

        String hashedPassword = hashPassword(rawPassword);
        if (hashedPassword.equals(user.getPasswordHash())) {
            System.out.println("Đăng nhập thành công cho user: " + username);
            return true;
        } else {
            System.err.println("Sai mật khẩu!");
            return false;
        }
    }
}