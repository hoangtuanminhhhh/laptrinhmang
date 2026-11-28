package com.chat.server.db;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";
    public static void initialize() {
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement();
             var journalMode = stmt.executeQuery("PRAGMA journal_mode = WAL")) {
            if (journalMode.next()) System.out.println("SQLite journal mode: " + journalMode.getString(1));
        } catch (Exception e) {
            System.err.println("Lỗi bật SQLite WAL: " + e.getMessage());
            throw new IllegalStateException("Không thể bật WAL cho SQLite", e);
        }
        try (Connection connection = getConnection()) {
            executeSchema(connection);
            ensureMessageColumns(connection);
            System.out.println("Đã khởi tạo SQLite database (WAL).");
        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo database: " + e.getMessage());
            throw new IllegalStateException("Không thể khởi tạo SQLite database", e);
        }
    }

    public static Connection getConnection() {
        try {
            Connection connection = DriverManager.getConnection(DB_URL);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA busy_timeout = 5000");
            }
            return connection;
        } catch (Exception e) {
            throw new IllegalStateException("Không thể mở kết nối SQLite", e);
        }
    }

    private static void executeSchema(Connection connection) throws Exception {
        try (InputStream is = DatabaseManager.class.getResourceAsStream("/schema.sql");
             BufferedReader reader = is == null ? null : new BufferedReader(new InputStreamReader(is));
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
        }
    }

    private static void ensureMessageColumns(Connection connection) throws Exception {
        java.util.Set<String> columns = new java.util.HashSet<>();
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(messages)")) {
            while (result.next()) columns.add(result.getString("name"));
        }
        try (Statement statement = connection.createStatement()) {
            if (!columns.contains("reply_to_sender")) statement.execute("ALTER TABLE messages ADD COLUMN reply_to_sender TEXT");
            if (!columns.contains("reply_to_snippet")) statement.execute("ALTER TABLE messages ADD COLUMN reply_to_snippet TEXT");
            if (!columns.contains("original_sender")) statement.execute("ALTER TABLE messages ADD COLUMN original_sender TEXT");
            if (!columns.contains("original_message_id")) statement.execute("ALTER TABLE messages ADD COLUMN original_message_id TEXT");
        }
    }
}