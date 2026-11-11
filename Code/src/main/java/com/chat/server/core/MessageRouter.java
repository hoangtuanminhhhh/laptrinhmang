package com.chat.server.core;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class MessageRouter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void routeMessage(ClientHandler senderHandler, Packet chatPacket) {
        try {
            JsonNode node = objectMapper.readTree(chatPacket.getPayload().toString());
            String senderId = node.get("senderId").asText();
            String receiverId = node.get("receiverId").asText();
            String content = node.get("content").asText();
            String messageId = node.has("messageId") ? node.get("messageId").asText() : String.valueOf(System.currentTimeMillis());

            ClientHandler receiverHandler = ConnectionManager.getHandler(receiverId);

            if (receiverHandler != null && ConnectionManager.isOnline(receiverId)) {
                // Người nhận ONLINE: Truyền trực tiếp qua Socket người nhận
                receiverHandler.sendPacket(chatPacket);
                sendAck(senderHandler, messageId, receiverId, "DELIVERED");
                System.out.println("[MessageRouter] Routed 1-1 message from " + senderId + " to " + receiverId);
            } else {
                // Người nhận OFFLINE: Nhờ Thành viên 2 lưu SQLite
                saveOfflineMessage(senderId, receiverId, content);
                sendAck(senderHandler, messageId, receiverId, "SAVED_OFFLINE");
                System.out.println("[MessageRouter] Receiver " + receiverId + " offline. Message queued.");
            }
        } catch (Exception e) {
            System.err.println("Lỗi định tuyến tin nhắn: " + e.getMessage());
        }
    }

    private static void sendAck(ClientHandler senderHandler, String messageId, String receiverId, String status) {
        try {
            Map<String, Object> ackData = Map.of(
                    "messageId", messageId,
                    "receiverId", receiverId,
                    "status", status,
                    "timestamp", System.currentTimeMillis()
            );
            String payload = objectMapper.writeValueAsString(ackData);
            senderHandler.sendPacket(new Packet(PacketType.CHAT_ACK, payload));
        } catch (Exception e) {
            System.err.println("Lỗi gửi gói CHAT_ACK: " + e.getMessage());
        }
    }

    private static void saveOfflineMessage(String senderId, String receiverId, String content) {
        // Điểm nối với Thành viên 2 (Server Logic & DB)
        // Ví dụ call: DatabaseManager.saveOfflineMessage(senderId, receiverId, content);
    }
}