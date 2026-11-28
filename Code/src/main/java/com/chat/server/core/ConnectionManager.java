package com.chat.server.core;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import com.chat.common.model.User;
import com.chat.server.db.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private static final ConcurrentHashMap<String, ClientHandler> activeConnections = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Thêm client mới vào danh sách online & thông báo cho các client khác
    public static void addConnection(String userId, ClientHandler handler) {
        if (userId == null || handler == null) return;
        ClientHandler old = activeConnections.put(userId, handler);
        if (old != null && old != handler) old.closeSession();
        System.out.println("[ConnectionManager] User connected: " + userId + " (Total online: " + activeConnections.size() + ")");
        User user = new UserRepository().getUserByUsername(userId);
        broadcastUserStatus(userId, user == null ? 0 : user.getId(), user == null ? null : user.getAvatar(), true);
    }

    // Xóa client khi ngắt kết nối & broadcast trạng thái Offline
    public static void removeConnection(String userId) {
        if (userId != null && activeConnections.remove(userId) != null) {
            System.out.println("[ConnectionManager] User disconnected: " + userId + " (Total online: " + activeConnections.size() + ")");
            broadcastUserStatus(userId, false);
        }
    }

    public static void removeConnection(String userId, ClientHandler handler) {
        if (userId != null && handler != null && activeConnections.remove(userId, handler)) {
            System.out.println("[ConnectionManager] User disconnected: " + userId + " (Total online: " + activeConnections.size() + ")");
            User user = new UserRepository().getUserByUsername(userId);
            broadcastUserStatus(userId, user == null ? 0 : user.getId(), user == null ? null : user.getAvatar(), false);
        }
    }

    public static ClientHandler getHandler(String userId) {
        return activeConnections.get(userId);
    }

    public static boolean isOnline(String userId) {
        return activeConnections.containsKey(userId);
    }

    public static Set<String> getOnlineUsers() {
        return activeConnections.keySet();
    }

    // Broadcast trạng thái ngắt/kết nối của một user cho tất cả người dùng khác
    public static void broadcastUserStatus(String userId, boolean isOnline) {
        try {
            User user = new UserRepository().getUserByUsername(userId);
            broadcastUserStatus(userId, user == null ? 0 : user.getId(), user == null ? null : user.getAvatar(), isOnline);
        } catch (Exception e) {
            System.err.println("Lỗi broadcast trạng thái: " + e.getMessage());
        }
    }

    private static void broadcastUserStatus(String username, int userId, String avatar, boolean online) {
        Packet packet = new Packet(PacketType.USER_STATUS_CHANGE, "SERVER", "ALL")
                .addPayload("username", username).addPayload("userId", userId)
                .addPayload("status", online ? "ONLINE" : "OFFLINE").addPayload("online", online);
        if (avatar != null) packet.addPayload("avatar", avatar);
        broadcastToAll(packet, username);
    }

    public static void broadcastToAll(Packet packet, String excludeUsername) {
        for (Map.Entry<String, ClientHandler> entry : activeConnections.entrySet()) {
            if (excludeUsername == null || !entry.getKey().equalsIgnoreCase(excludeUsername)) {
                try { entry.getValue().sendPacket(packet); }
                catch (Exception e) { System.err.println("Lỗi broadcast tới " + entry.getKey() + ": " + e.getMessage()); }
            }
        }
    }
}