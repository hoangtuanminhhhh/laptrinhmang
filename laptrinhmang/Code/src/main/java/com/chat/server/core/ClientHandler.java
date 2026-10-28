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

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Client connected: " + socket.getRemoteSocketAddress());

            while (!socket.isClosed()) {
                Packet request = PacketFramer.readPacket(in);

                if (request.getType() == PacketType.ECHO_REQUEST) {
                    System.out.println("Received: " + request.getPayload());
                    Packet response = new Packet(PacketType.ECHO_RESPONSE, request.getPayload());
                    PacketFramer.writePacket(out, response);
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected or error: " + e.getMessage());
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }
}