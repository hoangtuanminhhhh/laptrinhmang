package com.chat.server.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    private static Connection connection;

    public static void initialize() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("Đã kết nối thành công tới SQLite database.");
            executeSchema();
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo database: " + e.getMessage());
        }
    }

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL);
            }
        } catch (Exception e) {
            System.err.println("Lỗi lấy connection: " + e.getMessage());
        }
        return connection;
    }

    private static void executeSchema() {
        try (InputStream is = DatabaseManager.class.getResourceAsStream("/schema.sql");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is));
             Statement stmt = connection.createStatement()) {

            if (is == null) {
                System.err.println("Không tìm thấy file schema.sql trong resources!");
                return;
            }

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("--")) continue;
                sqlBuilder.append(line).append("\n");
                if (line.trim().endsWith(";")) {
                    stmt.execute(sqlBuilder.toString());
                    sqlBuilder.setLength(0);
                }
            }
            System.out.println("Đã khởi tạo/kiểm tra cấu trúc các bảng thành công.");
        } catch (Exception e) {
            System.err.println("Lỗi khi thực thi schema.sql: " + e.getMessage());
        }
    }
}