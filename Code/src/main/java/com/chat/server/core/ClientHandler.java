package com.chat.server.core;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String authenticatedUserId = null;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            System.out.println("Client connected: " + socket.getRemoteSocketAddress());

            while (!socket.isClosed()) {
                Packet packet = PacketFramer.readPacket(in);
                handlePacket(packet);
            }
        } catch (IOException e) {
            System.out.println("Client " + (authenticatedUserId != null ? authenticatedUserId : socket.getRemoteSocketAddress()) + " ngắt kết nối.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            cleanUp();
        }
    }

    private void handlePacket(Packet packet) {
        try {
            switch (packet.getType()) {
                case LOGIN_REQUEST:
                    handleLogin(packet);
                    break;
                case CHAT_MESSAGE:
                    MessageRouter.routeMessage(this, packet);
                    break;
                case AVATAR_UPDATE:
                    handleAvatarUpdate(packet);
                    break;
                case PING:
                    sendPacket(new Packet(PacketType.PONG, "{}"));
                    break;
                case ECHO_REQUEST:
                    sendPacket(new Packet(PacketType.ECHO_RESPONSE, packet.getPayload().toString()));
                    break;
                default:
                    System.out.println("Chưa hỗ trợ loại gói tin: " + packet.getType());
                    break;
            }
        } catch (Exception e) {
            System.err.println("Lỗi xử lý gói tin: " + e.getMessage());
        }
    }

    private void handleLogin(Packet packet) throws IOException {
        JsonNode node = objectMapper.readTree(packet.getPayload().toString());
        String username = node.get("username").asText();

        // Giả định đăng nhập thành công và lưu Session kết nối
        this.authenticatedUserId = username;
        ConnectionManager.addConnection(username, this);

        Map<String, Object> resp = Map.of("status", "SUCCESS", "userId", username);
        sendPacket(new Packet(PacketType.LOGIN_RESPONSE, objectMapper.writeValueAsString(resp)));
    }

    private void handleAvatarUpdate(Packet packet) {
        if (authenticatedUserId != null) {
            System.out.println("Nhận cập nhật Avatar từ user: " + authenticatedUserId);
        }
    }

    public synchronized void sendPacket(Packet packet) {
        try {
            if (socket != null && !socket.isClosed()) {
                PacketFramer.writePacket(out, packet);
            }
        } catch (IOException e) {
            System.err.println("Lỗi gửi gói tin tới " + authenticatedUserId + ": " + e.getMessage());
        }
    }

    private void cleanUp() {
        if (authenticatedUserId != null) {
            ConnectionManager.removeConnection(authenticatedUserId);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Lỗi đóng socket: " + e.getMessage());
        }
        System.out.println("Đã giải phóng tài nguyên socket sạch sẽ.");
    }
}