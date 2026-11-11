package com.chat.server.db;

import com.chat.common.config.NetworkConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    public static Connection getConnection() throws SQLException {
        String url = NetworkConfig.getDbUrl();
        Connection conn = DriverManager.getConnection(url);
        
        // Cấu hình SQLite thread-safe chống lỗi database locked
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

    public static void initialize() {
        try (Connection conn = getConnection()) {
            System.out.println("Đã kết nối thành công tới SQLite database.");
            
            // Tự động đọc và chạy file schema.sql để tạo các bảng nếu chưa tồn tại
            try (InputStream is = DatabaseManager.class.getResourceAsStream("/schema.sql")) {
                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sqlBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Bỏ qua dòng comment
                        if (line.trim().startsWith("--") || line.trim().isEmpty()) {
                            continue;
                        }
                        sqlBuilder.append(line).append(" ");
                        if (line.trim().endsWith(";")) {
                            try (Statement stmt = conn.createStatement()) {
                                stmt.execute(sqlBuilder.toString());
                            }
                            sqlBuilder.setLength(0);
                        }
                    }
                    System.out.println("Đã khởi tạo/kiểm tra cấu trúc các bảng thành công.");
                } else {
                    System.err.println("Không tìm thấy file schema.sql trong resources!");
                }
            } catch (Exception e) {
                System.err.println("Lỗi đọc file schema.sql: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khởi tạo CSDL: " + e.getMessage());
        }
    }
}