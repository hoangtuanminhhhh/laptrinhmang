package com.chat.test;

import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử NetworkService (TV3): sendLogin, sendRegister, sendMessage, sendReply, sendForward, sendAvatar.
 */
class NetworkServiceTest {

    private ServerSocket fakeServer;
    private Socket serverSide;
    private DataInputStream serverIn;
    private NetworkService service;

    @BeforeEach
    void setUp() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();

        CountDownLatch connected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 2000);
        serverSide = fakeServer.accept();
        serverIn = new DataInputStream(serverSide.getInputStream());
        assertTrue(connected.await(3, TimeUnit.SECONDS), "Client phải kết nối tới server giả lập");
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        if (serverSide != null) serverSide.close();
        if (fakeServer != null) fakeServer.close();
    }

    private Packet readOnServer() throws Exception {
        serverSide.setSoTimeout(3000);
        return PacketFramer.readPacket(serverIn);
    }

    @Test
    void sendLoginAndRegisterProduceStructuredJsonPackets() throws Exception {
        service.sendRegister("minh", "secret123", "avatarBase64==");
        Packet reg = readOnServer();
        assertEquals(PacketType.REGISTER_REQUEST, reg.getType());
        assertEquals("minh", reg.getPayloadValue("username"));
        assertEquals("secret123", reg.getPayloadValue("password"));
        assertEquals("avatarBase64==", reg.getPayloadValue("avatar"));

        service.sendLogin("minh", "secret123");
        Packet login = readOnServer();
        assertEquals(PacketType.LOGIN_REQUEST, login.getType());
        assertEquals("minh", login.getSender());
        assertEquals("SERVER", login.getReceiver());
        assertEquals("minh", login.getPayloadValue("username"));
        assertEquals("secret123", login.getPayloadValue("password"));
        assertEquals("minh", service.getCurrentUsername());
    }

    @Test
    void sendMessageRoutesToReceiverWithEmojisAndRequestId() throws Exception {
        service.sendLogin("minh", "pw");
        readOnServer();

        String requestId = service.sendMessage("tuan", "Xin chào 😊", List.of("😊", "🎉"));

        Packet msg = readOnServer();
        assertEquals(PacketType.MESSAGE_SEND, msg.getType());
        assertEquals("minh", msg.getSender());
        assertEquals("tuan", msg.getReceiver());
        assertEquals("Xin chào 😊", msg.getPayloadValue("content"));
        assertEquals(requestId, msg.getRequestId());
    }

    @Test
    void sendMessageRejectsBlankInput() {
        assertNull(service.sendMessage("", "abc"));
        assertNull(service.sendMessage("tuan", "   "));
    }

    @Test
    void sendReplyAndForwardCarryMetadata() throws Exception {
        service.sendLogin("minh", "pw");
        readOnServer();

        service.sendReply("tuan", "Ok bạn", 12L, "minh", "Chiều nay học không?");
        Packet reply = readOnServer();
        assertEquals(PacketType.REPLY_SEND, reply.getType());
        assertEquals(12, ((Number) reply.getPayloadValue("replyToMessageId")).intValue());
        assertEquals("minh", reply.getPayloadValue("replyToSender"));
        assertEquals("Chiều nay học không?", reply.getPayloadValue("replyToSnippet"));

        service.sendForward("anh", "Nội dung gốc", "tuan", 13L);
        Packet fwd = readOnServer();
        assertEquals(PacketType.FORWARD_SEND, fwd.getType());
        assertEquals("anh", fwd.getReceiver());
        assertEquals("tuan", fwd.getPayloadValue("originalSender"));
    }

    @Test
    void sendAvatarValidatesSizeAndProducesAvatarUpdatePacket() throws Exception {
        service.sendAvatar("aGVsbG8=", "png");
        Packet p = readOnServer();
        assertEquals(PacketType.AVATAR_UPDATE, p.getType());
        assertEquals("aGVsbG8=", p.getPayloadValue("avatarBase64"));
        assertEquals("png", p.getPayloadValue("format"));

        AtomicReference<String> error = new AtomicReference<>();
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { error.set(friendlyMessage); }
        });

        assertNull(service.sendAvatar("A".repeat(NetworkService.MAX_AVATAR_BASE64_LENGTH + 1), "png"));
        assertNotNull(error.get());
        assertTrue(error.get().contains("quá lớn"));
    }
}
