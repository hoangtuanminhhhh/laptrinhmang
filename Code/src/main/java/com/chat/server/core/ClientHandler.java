package com.chat.server.core;

import com.chat.common.model.Message;
import com.chat.common.model.User;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import com.chat.server.db.MessageRepository;
import com.chat.server.db.UserRepository;
import com.chat.server.service.AuthService;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientHandler implements Runnable {
    private static final ScheduledExecutorService EPHEMERAL_EXPIRY = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ephemeral-message-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    private final Socket socket;
    private volatile DataOutputStream output;
    private final ServerMetrics metrics;
    private volatile String loggedInUsername;
    private final AuthService authService = new AuthService();
    private final UserRepository userRepository = new UserRepository();
    private final MessageRepository messageRepository = new MessageRepository();

    public ClientHandler(Socket socket) { this(socket, null); }

    public ClientHandler(Socket socket, ServerMetrics metrics) {
        this.socket = socket;
        this.metrics = metrics;
    }

    public String getLoggedInUsername() { return loggedInUsername; }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            output = out;
            while (!socket.isClosed()) {
                Packet request = PacketFramer.readPacket(in);
                if (request == null) break;
                countBytes(request);
                handlePacket(request);
            }
        } catch (Exception e) {
            if (!socket.isClosed()) System.err.println("Client disconnected or error: " + e.getMessage());
        } finally {
            output = null;
            String username = loggedInUsername;
            loggedInUsername = null;
            if (username != null) ConnectionManager.removeConnection(username, this);
            closeSession();
        }
    }

    private void handlePacket(Packet request) throws IOException {
        switch (request.getType()) {
            case ECHO_REQUEST -> {
                Packet response = Packet.createEchoResponse(request.getRequestId(), request.getPayload());
                sendPacket(response);
            }
            case PING -> {
                Packet pong = new Packet(PacketType.PONG, "SERVER", request.getSender());
                pong.setRequestId(request.getRequestId());
                sendPacket(pong);
            }
            case CONNECT_REQUEST -> {
                Packet response = new Packet(PacketType.CONNECT_RESPONSE, "SERVER", request.getSender());
                response.setRequestId(request.getRequestId());
                response.addPayload("success", true);
                sendPacket(response);
            }
            case REGISTER_REQUEST -> handleRegister(request);
            case LOGIN_REQUEST -> handleLogin(request);
            case LOGOUT_REQUEST -> {
                String username = loggedInUsername;
                loggedInUsername = null;
                if (username != null) ConnectionManager.removeConnection(username, this);
            }
            case MESSAGE_SEND, REPLY_SEND, FORWARD_SEND -> MessageRouter.routeMessage(this, request);
            case HISTORY_REQUEST -> handleHistory(request);
            case AVATAR_UPDATE -> handleAvatarUpdate(request);
            case DELETE_EPHEMERAL -> {
                String msgId = stringValue(request, "msgId", "messageId");
                if (msgId != null) messageRepository.deleteByMsgId(msgId);
            }
            default -> sendError(request, "Loại gói tin không được hỗ trợ: " + request.getType());
        }
    }

    private void handleRegister(Packet request) throws IOException {
        String username = stringValue(request, "username");
        String password = stringValue(request, "password");
        String avatar = stringValue(request, "avatar", "avatarBase64");
        boolean success = username != null && !username.isBlank() && password != null && !password.isEmpty()
                && authService.register(username.trim(), password, avatar);
        Packet response = response(PacketType.REGISTER_RESPONSE, request);
        response.addPayload("action", "REGISTER").addPayload("success", success)
                .addPayload("message", success ? "Đăng ký thành công. Vui lòng đăng nhập." : "Tên đăng nhập đã tồn tại hoặc thông tin không hợp lệ.");
        sendPacket(response);
        if (success) ConnectionManager.broadcastUserStatus(username.trim(), false);
    }

    private void handleLogin(Packet request) throws IOException {
        String username = stringValue(request, "username");
        String password = stringValue(request, "password");
        boolean success = username != null && password != null && authService.login(username, password);
        Packet response = response(PacketType.LOGIN_RESPONSE, request);
        if (!success) {
            response.addPayload("success", false).addPayload("message", "Tên đăng nhập hoặc mật khẩu không đúng!");
            sendPacket(response);
            return;
        }

        loggedInUsername = username.trim();
        ConnectionManager.addConnection(loggedInUsername, this);
        User user = userRepository.getUserByUsername(loggedInUsername);
        response.addPayload("success", true).addPayload("username", user.getUsername())
                .addPayload("userId", user.getId()).addPayload("avatar", user.getAvatar());
        List<Map<String, Object>> users = new ArrayList<>();
        for (User item : userRepository.getAllUsers()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", item.getId());
            data.put("userId", item.getId());
            data.put("username", item.getUsername());
            data.put("avatar", item.getAvatar());
            data.put("online", ConnectionManager.isOnline(item.getUsername()));
            users.add(data);
        }
        response.addPayload("users", users);
        sendPacket(response);

        for (Message message : messageRepository.popOfflineMessages(user.getId())) {
            User sender = userRepository.getUserById(message.getSenderId());
            Packet offline = messagePacket(PacketType.MESSAGE_RECEIVE, message,
                    sender == null ? "" : sender.getUsername(), loggedInUsername);
            sendPacket(offline);
        }
    }

    private void handleHistory(Packet request) throws IOException {
        String partner = request.getReceiver();
        if (partner == null) partner = stringValue(request, "partnerUsername", "username");
        Packet response = response(PacketType.HISTORY_RESPONSE, request);
        response.addPayload("partnerUsername", partner);
        List<Map<String, Object>> rows = new ArrayList<>();
        if ("ALL".equalsIgnoreCase(partner)) {
            for (Message message : messageRepository.getRoomHistory(50)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("msgId", message.getMsgId());
                row.put("sender", message.getSenderUsername());
                row.put("content", message.getContent());
                row.put("timestamp", message.getTimestamp());
                rows.add(row);
            }
        } else {
            User self = userRepository.getUserByUsername(loggedInUsername);
            User other = userRepository.getUserByUsername(partner);
            if (self != null && other != null) {
            for (Message message : messageRepository.getRecentMessages(self.getId(), other.getId(), 50)) {
                User sender = userRepository.getUserById(message.getSenderId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("msgId", message.getMsgId());
                row.put("messageId", message.getMsgId());
                row.put("sender", sender == null ? "" : sender.getUsername());
                row.put("senderId", message.getSenderId());
                row.put("receiver", message.getSenderId() == self.getId() ? partner : loggedInUsername);
                row.put("content", message.getContent());
                row.put("replyToMessageId", message.getReplyTo());
                row.put("replyToSender", message.getReplyToSender());
                row.put("replyToSnippet", message.getReplyToSnippet());
                row.put("originalSender", message.getOriginalSender());
                row.put("originalMessageId", message.getOriginalMessageId());
                row.put("timestamp", message.getTimestamp());
                row.put("forwarded", message.isForwarded());
                rows.add(row);
            }
            }
        }
        response.addPayload("messages", rows);
        sendPacket(response);
    }

    private void handleAvatarUpdate(Packet request) throws IOException {
        if (loggedInUsername == null) {
            sendError(request, "Đăng nhập để cập nhật avatar.");
            return;
        }
        String avatar = stringValue(request, "avatarBase64", "avatar");
        if (avatar == null || !userRepository.updateAvatar(loggedInUsername, avatar)) {
            sendError(request, "Không thể cập nhật avatar.");
            return;
        }
        Packet update = new Packet(PacketType.AVATAR_UPDATE, loggedInUsername, "ALL")
                .addPayload("username", loggedInUsername).addPayload("avatarBase64", avatar);
        ConnectionManager.broadcastToAll(update, loggedInUsername);
    }

    private Packet messagePacket(PacketType type, Message message, String sender, String receiver) {
        Packet packet = new Packet(type, sender, receiver);
        packet.setRequestId(message.getMsgId());
        packet.addPayload("msgId", message.getMsgId()).addPayload("messageId", message.getMsgId())
                .addPayload("content", message.getContent()).addPayload("timestamp", message.getTimestamp())
                .addPayload("ttlSeconds", 0L);
        if (message.getReplyTo() != null) packet.addPayload("replyToMessageId", message.getReplyTo());
        if (message.getReplyToSender() != null) packet.addPayload("replyToSender", message.getReplyToSender());
        if (message.getReplyToSnippet() != null) packet.addPayload("replyToSnippet", message.getReplyToSnippet());
        if (message.getOriginalSender() != null) packet.addPayload("originalSender", message.getOriginalSender());
        if (message.getOriginalMessageId() != null) packet.addPayload("originalMessageId", message.getOriginalMessageId());
        return packet;
    }

    private Packet response(PacketType type, Packet request) {
        Packet packet = new Packet(type, "SERVER", request.getSender());
        packet.setRequestId(request.getRequestId());
        return packet;
    }

    private String stringValue(Packet packet, String... names) {
        for (String name : names) {
            Object value = packet.getPayloadValue(name);
            if (value != null) return value.toString();
        }
        return null;
    }

    private void sendError(Packet request, String message) throws IOException {
        sendPacket(response(PacketType.ERROR_RESPONSE, request).addPayload("message", message));
    }

    private void countBytes(Packet packet) {
        if (metrics == null) return;
        try { metrics.addBytesTransferred(packet.toBytes().length); }
        catch (Exception e) { metrics.addBytesTransferred(packet.getPayload() == null ? 0 : packet.getPayload().getBytes(StandardCharsets.UTF_8).length); }
    }

    public static void scheduleEphemeralDeletion(String msgId, long ttlSeconds) {
        if (msgId != null && ttlSeconds > 0) {
            EPHEMERAL_EXPIRY.schedule(() -> new MessageRepository().deleteByMsgId(msgId), ttlSeconds, TimeUnit.SECONDS);
        }
    }

    public synchronized void sendPacket(Packet packet) throws IOException {
        if (packet == null) throw new IllegalArgumentException("Packet không được null.");
        DataOutputStream out = output;
        if (out == null || socket.isClosed()) throw new IOException("Kết nối client chưa sẵn sàng để gửi dữ liệu.");
        PacketFramer.writePacket(out, packet);
        out.flush();
        if (metrics != null) {
            metrics.incrementMessagesSent();
            countBytes(packet);
        }
    }

    public void closeSession() {
        try { if (socket != null && !socket.isClosed()) socket.close(); }
        catch (IOException e) { System.err.println("Error closing socket: " + e.getMessage()); }
    }
}
