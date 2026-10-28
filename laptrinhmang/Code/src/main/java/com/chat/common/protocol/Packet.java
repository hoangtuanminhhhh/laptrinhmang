package com.chat.common.protocol;

public class Packet {
    private int length;
    private PacketType type;
    private String payload;

    public Packet() {} // Dành cho Jackson

    public Packet(PacketType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public PacketType getType() { return type; }
    public void setType(PacketType type) { this.type = type; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}