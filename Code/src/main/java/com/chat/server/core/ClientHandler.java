package com.chat.server.core;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataOutputStream output;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            synchronized (this) {
                output = out;
            }

            System.out.println("Client connected: " + socket.getRemoteSocketAddress());

            while (!socket.isClosed()) {
                Packet request = PacketFramer.readPacket(in);

                if (request.getType() == PacketType.ECHO_REQUEST) {
                    System.out.println("Received: " + request.getPayload());
                    Packet response = new Packet(PacketType.ECHO_RESPONSE, request.getPayload());
                    sendPacket(response);
                }
            }
        } catch (Exception e) {
            System.err.println("Client disconnected or error: " + e.getMessage());
        } finally {
            synchronized (this) {
                output = null;
            }
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }

    public synchronized void sendPacket(Packet packet) throws IOException {
        if (packet == null) {
            throw new IllegalArgumentException("Packet không được null.");
        }
        if (output == null || socket.isClosed()) {
            throw new IOException("Kết nối client chưa sẵn sàng để gửi dữ liệu.");
        }
        PacketFramer.writePacket(output, packet);
    }
}