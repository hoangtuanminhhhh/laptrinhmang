package com.chat.server.core;

import com.chat.common.model.Message;
import com.chat.common.model.User;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import com.chat.server.db.MessageRepository;
import com.chat.server.db.UserRepository;

import java.util.UUID;

public final class MessageRouter {
    private MessageRouter() { }

    public static void routeMessage(ClientHandler senderHandler, Packet request) {
        String sender = senderHandler.getLoggedInUsername() != null
                ? senderHandler.getLoggedInUsername() : request.getSender();
        String receiver = request.getReceiver();
        Object rawContent = request.getPayloadValue("content");
        String content = rawContent == null ? "" : rawContent.toString();
        if (sender == null || receiver == null || content.isBlank()) {
            sendAck(senderHandler, request, "FAILED", "Người gửi, người nhận và nội dung không được để trống.");
            return;
        }

        String messageId = request.getRequestId();
        if (messageId == null || messageId.isBlank()) messageId = UUID.randomUUID().toString();
        long ttlSeconds = longValue(request.getPayloadValue("ttlSeconds"), 0L);
        PacketType receiveType = switch (request.getType()) {
            case REPLY_SEND -> PacketType.REPLY_RECEIVE;
            case FORWARD_SEND -> PacketType.FORWARD_RECEIVE;
            default -> PacketType.MESSAGE_RECEIVE;
        };
        Packet deliver = new Packet(receiveType, sender, receiver);
        deliver.setRequestId(messageId);
        deliver.setTimestamp(request.getTimestamp() > 0 ? request.getTimestamp() : System.currentTimeMillis());
        deliver.addPayload("msgId", messageId).addPayload("messageId", messageId)
                .addPayload("content", content).addPayload("timestamp", deliver.getTimestamp())
                .addPayload("ttlSeconds", ttlSeconds);
        copy(request, deliver, "replyToMessageId", "replyToSender", "replyToSnippet", "originalSender", "originalMessageId");

        if ("ALL".equalsIgnoreCase(receiver)) {
            Message roomMessage = new Message();
            roomMessage.setMsgId(messageId);
            roomMessage.setSenderUsername(sender);
            roomMessage.setContent(content);
            roomMessage.setTimestamp(deliver.getTimestamp());
            if (ttlSeconds == 0) new MessageRepository().saveRoomMessage(roomMessage);
            ConnectionManager.broadcastToAll(deliver, sender);
            if (ttlSeconds > 0) ClientHandler.scheduleEphemeralDeletion(messageId, ttlSeconds);
            sendAck(senderHandler, request, "DELIVERED", null);
            return;
        }

        UserRepository users = new UserRepository();
        User senderUser = users.getUserByUsername(sender);
        User recipient = users.getUserByUsername(receiver);
        if (recipient == null) {
            sendAck(senderHandler, request, "FAILED", "Không tìm thấy người nhận.");
            return;
        }

        Message message = new Message();
        message.setMsgId(messageId);
        message.setSenderId(senderUser == null ? 0 : senderUser.getId());
        message.setReceiverId(recipient.getId());
        message.setSenderUsername(sender);
        message.setReceiverUsername(receiver);
        message.setContent(content);
        message.setTimestamp(deliver.getTimestamp());
        message.setReplyTo(stringValue(request, "replyToMessageId"));
        message.setReplyToSender(stringValue(request, "replyToSender"));
        message.setReplyToSnippet(stringValue(request, "replyToSnippet"));
        message.setForwarded(request.getType() == PacketType.FORWARD_SEND);
        message.setOriginalSender(stringValue(request, "originalSender"));
        message.setOriginalMessageId(stringValue(request, "originalMessageId"));

        MessageRepository repository = new MessageRepository();
        boolean saved = repository.saveMessage(message);
        if (!saved) {
            sendAck(senderHandler, request, "FAILED", "Không thể lưu tin nhắn vào cơ sở dữ liệu.");
            return;
        }
        if (ttlSeconds > 0) ClientHandler.scheduleEphemeralDeletion(messageId, ttlSeconds);

        ClientHandler target = ConnectionManager.getHandler(receiver);
        if (target != null && ConnectionManager.isOnline(receiver)) {
            try {
                target.sendPacket(deliver);
                sendAck(senderHandler, request, "DELIVERED", null);
            } catch (Exception e) {
                if (ttlSeconds == 0) repository.saveOfflineMessage(message.getId(), recipient.getId());
                sendAck(senderHandler, request, ttlSeconds == 0 ? "SAVED_OFFLINE" : "FAILED", e.getMessage());
            }
        } else {
            if (ttlSeconds == 0) {
                repository.saveOfflineMessage(message.getId(), recipient.getId());
                sendAck(senderHandler, request, "SAVED_OFFLINE", null);
            } else {
                sendAck(senderHandler, request, "FAILED", "Người nhận đang ngoại tuyến; tin nhắn tự hủy không được lưu offline.");
            }
        }
    }

    private static void sendAck(ClientHandler handler, Packet request, String status, String message) {
        try {
            Packet ack = new Packet(PacketType.CHAT_ACK, "SERVER", request.getSender());
            ack.setRequestId(request.getRequestId());
            ack.addPayload("messageId", request.getRequestId()).addPayload("receiverId", request.getReceiver())
                    .addPayload("status", status).addPayload("timestamp", System.currentTimeMillis());
            if (message != null) ack.addPayload("message", message);
            handler.sendPacket(ack);
        } catch (Exception e) {
            System.err.println("Lỗi gửi CHAT_ACK: " + e.getMessage());
        }
    }

    private static void copy(Packet source, Packet destination, String... names) {
        for (String name : names) {
            Object value = source.getPayloadValue(name);
            if (value != null) destination.addPayload(name, value);
        }
    }

    private static String stringValue(Packet packet, String name) {
        Object value = packet.getPayloadValue(name);
        return value == null ? null : value.toString();
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        if (value != null) try { return Math.max(0L, Long.parseLong(value.toString())); }
        catch (NumberFormatException ignored) { }
        return fallback;
    }
}
