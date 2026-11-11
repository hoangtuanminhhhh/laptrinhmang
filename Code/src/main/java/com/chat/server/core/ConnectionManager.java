package com.chat.server.core;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    private static final ConcurrentHashMap<String, ClientHandler> activeConnections = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Thêm client mới vào danh sách online & thông báo cho các client khác
    public static void addConnection(String userId, ClientHandler handler) {
        activeConnections.put(userId, handler);
        System.out.println("[ConnectionManager] User connected: " + userId + " (Total online: " + activeConnections.size() + ")");
        broadcastUserStatus(userId, true);
    }

    // Xóa client khi ngắt kết nối & broadcast trạng thái Offline
    public static void removeConnection(String userId) {
        if (userId != null && activeConnections.remove(userId) != null) {
            System.out.println("[ConnectionManager] User disconnected: " + userId + " (Total online: " + activeConnections.size() + ")");
            broadcastUserStatus(userId, false);
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
            Map<String, Object> statusData = Map.of(
                    "userId", userId,
                    "status", isOnline ? "ONLINE" : "OFFLINE"
            );
            String payload = objectMapper.writeValueAsString(statusData);
            Packet statusPacket = new Packet(PacketType.USER_STATUS_CHANGE, payload);

            for (Map.Entry<String, ClientHandler> entry : activeConnections.entrySet()) {
                if (!entry.getKey().equals(userId)) {
                    entry.getValue().sendPacket(statusPacket);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi broadcast trạng thái: " + e.getMessage());
        }
    }
}