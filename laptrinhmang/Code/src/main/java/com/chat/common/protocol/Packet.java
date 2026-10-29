package com.chat.common.protocol;

<<<<<<< HEAD
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cấu trúc gói tin JSON Envelope chuẩn cho giao thức TCP Chat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Packet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PacketType type;
    private String requestId;
    private String sender;
    private String receiver;
    private long timestamp;
    private Map<String, Object> payload;

    public Packet() {
        this.timestamp = System.currentTimeMillis();
        this.payload = new HashMap<>();
    }

    public Packet(PacketType type, String sender, String receiver) {
        this();
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.requestId = UUID.randomUUID().toString();
    }

    public static Packet createEchoRequest(String sender, String message) {
        Packet packet = new Packet(PacketType.ECHO_REQUEST, sender, "SERVER");
        packet.addPayload("message", message);
        return packet;
    }

    public static Packet createEchoResponse(String requestId, String originalMessage) {
        Packet packet = new Packet(PacketType.ECHO_RESPONSE, "SERVER", "CLIENT");
        packet.setRequestId(requestId);
        packet.addPayload("originalMessage", originalMessage);
        packet.addPayload("receivedAt", System.currentTimeMillis());
        packet.addPayload("status", "SUCCESS");
        return packet;
    }

    public Packet addPayload(String key, Object value) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        this.payload.put(key, value);
        return this;
    }

    public Object getPayloadValue(String key) {
        return this.payload != null ? this.payload.get(key) : null;
    }

    public byte[] toBytes() throws JsonProcessingException {
        String json = MAPPER.writeValueAsString(this);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static Packet fromBytes(byte[] bytes) throws Exception {
        return MAPPER.readValue(bytes, Packet.class);
    }

    public static Packet fromJson(String json) throws Exception {
        return MAPPER.readValue(json, Packet.class);
    }

    public String toJson() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }

    // Getters and Setters
    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "type=" + type +
                ", requestId='" + requestId + '\'' +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
=======
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
>>>>>>> 8baad60d68f0886283b7ccda1ca4376fd03e9b80
