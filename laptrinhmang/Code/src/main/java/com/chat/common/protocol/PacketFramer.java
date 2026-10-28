package com.chat.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PacketFramer {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void writePacket(DataOutputStream out, Packet packet) throws IOException {
        String jsonPayload = objectMapper.writeValueAsString(packet.getPayload());
        byte[] payloadBytes = jsonPayload.getBytes("UTF-8");
        int length = payloadBytes.length + 4; // 4 bytes cho PacketType

        out.writeInt(length);
        out.writeInt(packet.getType().ordinal());
        out.write(payloadBytes);
        out.flush();
    }

    public static Packet readPacket(DataInputStream in) throws IOException {
        int length = in.readInt();
        int typeOrdinal = in.readInt();
        PacketType type = PacketType.values()[typeOrdinal];

        int payloadLength = length - 4;
        byte[] payloadBytes = new byte[payloadLength];
        in.readFully(payloadBytes);

        String jsonPayload = new String(payloadBytes, "UTF-8");
        String payload = objectMapper.readValue(jsonPayload, String.class);

        Packet packet = new Packet(type, payload);
        packet.setLength(length);
        return packet;
    }
}