package com.chat.server.service;

import com.chat.server.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DatabaseCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCleanupService.class);
    private static final int MESSAGE_RETENTION_DAYS = 30;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "db-cleanup-worker");
        t.setDaemon(true);
        return t;
    });

    public void start() {
        scheduler.scheduleAtFixedRate(this::runCleanup, 0, 24, TimeUnit.HOURS);
        logger.info("Đã khởi động DatabaseCleanupService (chu kỳ: 24h/lần, lưu trữ tin nhắn: {} ngày).", MESSAGE_RETENTION_DAYS);
    }

    public void runCleanup() {
        logger.info("Bắt đầu tiến trình dọn dẹp rác CSDL SQLite...");
        long startTime = System.currentTimeMillis();

        try (Connection conn = DatabaseManager.getConnection()) {
            String deleteOldMessagesSql = "DELETE FROM messages WHERE created_at < datetime('now', ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteOldMessagesSql)) {
                pstmt.setString(1, "-" + MESSAGE_RETENTION_DAYS + " days");
                int deletedMsgs = pstmt.executeUpdate();
                logger.info("Đã xóa {} tin nhắn cũ quá {} ngày.", deletedMsgs, MESSAGE_RETENTION_DAYS);
            }

            String deleteOrphanQueueSql = "DELETE FROM offline_queue WHERE message_id NOT IN (SELECT id FROM messages) " +
                                          "OR user_id NOT IN (SELECT id FROM users)";
            try (Statement stmt = conn.createStatement()) {
                int deletedQueue = stmt.executeUpdate(deleteOrphanQueueSql);
                logger.info("Đã dọn dẹp {} bản ghi rác trong bảng offline_queue.", deletedQueue);
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA optimize;");
                stmt.execute("VACUUM;");
                logger.info("Đã thực thi PRAGMA optimize và VACUUM giải phóng dung lượng SQLite.");
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Hoàn tất dọn dẹp CSDL SQLite sau {} ms.", elapsed);

        } catch (Exception e) {
            logger.error("Lỗi trong quá trình dọn dẹp CSDL SQLite: {}", e.getMessage(), e);
        }
    }

    public void stop() {
        scheduler.shutdown();
        logger.info("Đã dừng DatabaseCleanupService.");
    }
}