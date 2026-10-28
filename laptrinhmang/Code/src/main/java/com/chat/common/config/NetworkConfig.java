package com.chat.common.config;

import java.io.InputStream;
import java.util.Properties;

public class NetworkConfig {
    private static final Properties properties = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try (InputStream is = NetworkConfig.class.getResourceAsStream("/server.properties")) {
            if (is != null) {
                properties.load(is);
                System.out.println("Đã tải cấu hình server.properties thành công.");
            } else {
                System.err.println("Không tìm thấy server.properties, sử dụng cấu hình mặc định.");
            }
        } catch (Exception e) {
            System.err.println("Lỗi đọc file cấu hình: " + e.getMessage());
        }
    }

    public static int getServerPort() {
        String portStr = properties.getProperty("server.port", "8888");
        return Integer.parseInt(portStr);
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url", "jdbc:sqlite:chat.db");
    }
}