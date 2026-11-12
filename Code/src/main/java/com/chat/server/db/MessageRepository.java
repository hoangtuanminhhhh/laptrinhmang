package com.chat.server.db;

import com.chat.common.model.Message;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageRepository {

    // 1. Lưu tin nhắn vào bảng messages (khớp với schema.sql của main)
    public boolean saveMessage(Message msg) {
        String sql = "INSERT INTO messages(msg_id, sender_id, receiver_id, content, reply_to, is_forwarded) VALUES(?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, msg.getMsgId());
            pstmt.setInt(2, msg.getSenderId());
            pstmt.setInt(3, msg.getReceiverId());
            pstmt.setString(4, msg.getContent());
            pstmt.setString(5, msg.getReplyTo());
            pstmt.setInt(6, msg.isForwarded() ? 1 : 0);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        msg.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lưu tin nhắn: " + e.getMessage());
        }
        return false;
    }

    // 2. Truy vấn 50 tin nhắn gần nhất giữa 2 người dùng
    public List<Message> getRecentMessages(int user1Id, int user2Id, int limit) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE (sender_id = ? AND receiver_id = ?) " +
                     "OR (sender_id = ? AND receiver_id = ?) ORDER BY created_at DESC, id DESC LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, user1Id);
            pstmt.setInt(2, user2Id);
            pstmt.setInt(3, user2Id);
            pstmt.setInt(4, user1Id);
            pstmt.setInt(5, limit > 0 ? limit : 50);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Message msg = new Message();
                msg.setId(rs.getInt("id"));
                msg.setMsgId(rs.getString("msg_id"));
                msg.setSenderId(rs.getInt("sender_id"));
                msg.setReceiverId(rs.getInt("receiver_id"));
                msg.setContent(rs.getString("content"));
                msg.setReplyTo(rs.getString("reply_to"));
                msg.setForwarded(rs.getInt("is_forwarded") == 1);
                list.add(0, msg);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy lịch sử tin nhắn: " + e.getMessage());
        }
        return list;
    }

    // 3. Lưu tin nhắn vào hàng chờ Offline (bảng offline_queue)
    public boolean saveOfflineMessage(int messageId, int userId) {
        String sql = "INSERT INTO offline_queue(user_id, message_id) VALUES(?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, messageId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu tin nhắn offline: " + e.getMessage());
        }
        return false;
    }

    // 4. Lấy và xóa tin nhắn chờ khi người dùng online trở lại
    public List<Message> popOfflineMessages(int userId) {
        List<Message> list = new ArrayList<>();
        String selectSql = "SELECT m.* FROM messages m " +
                           "JOIN offline_queue oq ON m.id = oq.message_id " +
                           "WHERE oq.user_id = ? ORDER BY oq.id ASC";
        String deleteSql = "DELETE FROM offline_queue WHERE user_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {

            selectStmt.setInt(1, userId);
            ResultSet rs = selectStmt.executeQuery();
            while (rs.next()) {
                Message msg = new Message();
                msg.setId(rs.getInt("id"));
                msg.setMsgId(rs.getString("msg_id"));
                msg.setSenderId(rs.getInt("sender_id"));
                msg.setReceiverId(rs.getInt("receiver_id"));
                msg.setContent(rs.getString("content"));
                msg.setReplyTo(rs.getString("reply_to"));
                msg.setForwarded(rs.getInt("is_forwarded") == 1);
                list.add(msg);
            }

            if (!list.isEmpty()) {
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, userId);
                    deleteStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy/xóa tin nhắn offline: " + e.getMessage());
        }
        return list;
    }
}