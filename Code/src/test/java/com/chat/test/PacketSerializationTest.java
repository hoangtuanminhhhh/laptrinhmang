package com.chat.test;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketSerializationTest {

    @Test
    void roundTripsNestedMessagePayloadAndUnicode() throws Exception {
        Map<String, Object> quotedMessage = Map.of(
                "messageId", "msg-41",
                "sender", "lan",
                "content", "Chào buổi sáng 🌅"
        );
        Map<String, Object> message = Map.of(
                "messageId", "msg-42",
                "senderId", 7,
                "receiverId", 9,
                "content", "Xin chào Việt Nam 🇻🇳 — hẹn gặp lại 👋",
                "replyTo", quotedMessage,
                "forwarded", true
        );
        Packet original = new Packet(PacketType.MESSAGE_SEND, "lan", "minh")
                .addPayload("message", message)
                .addPayload("attachments", List.of(
                        Map.of("name", "emoji.png", "size", 2048),
                        Map.of("name", "avatar.jpg", "size", 4096)
                ));

        Packet decoded = Packet.fromBytes(original.toBytes());

        assertEquals(original.getType(), decoded.getType());
        assertEquals(original.getRequestId(), decoded.getRequestId());
        assertEquals(original.getSender(), decoded.getSender());
        assertEquals(original.getReceiver(), decoded.getReceiver());
        assertEquals(original.getTimestamp(), decoded.getTimestamp());

        Object decodedMessage = decoded.getPayloadValue("message");
        assertTrue(decodedMessage instanceof Map<?, ?>, "message payload must remain a JSON object");
        Map<?, ?> decodedMessageMap = (Map<?, ?>) decodedMessage;
        assertEquals(message.get("content"), decodedMessageMap.get("content"));
        assertEquals(message.get("senderId"), decodedMessageMap.get("senderId"));
        assertEquals(Boolean.TRUE, decodedMessageMap.get("forwarded"));

        Object decodedQuote = decodedMessageMap.get("replyTo");
        assertTrue(decodedQuote instanceof Map<?, ?>, "replyTo must remain a nested JSON object");
        assertEquals(quotedMessage.get("content"), ((Map<?, ?>) decodedQuote).get("content"));

        Object decodedAttachments = decoded.getPayloadValue("attachments");
        assertTrue(decodedAttachments instanceof List<?>, "attachments must remain a JSON array");
        List<?> attachments = (List<?>) decodedAttachments;
        assertEquals(2, attachments.size());
        assertTrue(attachments.get(0) instanceof Map<?, ?>);
        assertEquals("emoji.png", ((Map<?, ?>) attachments.get(0)).get("name"));
    }
}
