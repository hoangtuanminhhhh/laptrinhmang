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
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử ServerListener (TV3): đọc socket trên luồng nền, giải mã packet, phản hồi PING và chịu lỗi JSON.
 */
class ServerListenerTest {

    private ServerSocket fakeServer;
    private Socket serverSide;
    private DataInputStream serverIn;
    private DataOutputStream serverOut;
    private NetworkService service;

    private final BlockingQueue<Packet> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final CountDownLatch disconnected = new CountDownLatch(1);
    private volatile String callbackThreadName;

    @BeforeEach
    void setUp() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();

        CountDownLatch connected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { disconnected.countDown(); }
            @Override public void onPacketReceived(Packet packet) {
                callbackThreadName = Thread.currentThread().getName();
                received.add(packet);
            }
            @Override public void onError(String friendlyMessage, Throwable exception) { errors.add(friendlyMessage); }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 2000);
        serverSide = fakeServer.accept();
        serverIn = new DataInputStream(serverSide.getInputStream());
        serverOut = new DataOutputStream(serverSide.getOutputStream());
        assertTrue(connected.await(3, TimeUnit.SECONDS));
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        if (serverSide != null) serverSide.close();
        if (fakeServer != null) fakeServer.close();
    }

    @Test
    void packetFromServerIsDeliveredOnBackgroundThread() throws Exception {
        Packet incoming = new Packet(PacketType.MESSAGE_RECEIVE, "tuan", "minh")
                .addPayload("content", "hello");
        PacketFramer.writePacket(serverOut, incoming);

        Packet p = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(p);
        assertEquals(PacketType.MESSAGE_RECEIVE, p.getType());
        assertEquals("hello", p.getPayloadValue("content"));
        assertEquals("Server-Packet-Listener", callbackThreadName);
    }

    @Test
    void serverPingIsAnsweredWithPongAndNotForwardedToUi() throws Exception {
        Packet ping = new Packet(PacketType.PING, "SERVER", "minh");
        PacketFramer.writePacket(serverOut, ping);

        serverSide.setSoTimeout(3000);
        Packet pong = PacketFramer.readPacket(serverIn);
        assertEquals(PacketType.PONG, pong.getType());
        assertEquals(ping.getRequestId(), pong.getRequestId());
        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void malformedJsonFrameIsSkippedAndConnectionSurvives() throws Exception {
        PacketFramer.writeFrame(serverOut, "{ malformed json".getBytes(StandardCharsets.UTF_8));
        PacketFramer.writePacket(serverOut,
                new Packet(PacketType.MESSAGE_RECEIVE, "tuan", "minh").addPayload("content", "ok-after-bad"));

        Packet p = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(p);
        assertEquals("ok-after-bad", p.getPayloadValue("content"));
        assertNotNull(errors.poll(1, TimeUnit.SECONDS));
        assertTrue(service.isConnected());
    }

    @Test
    void serverClosingSocketTriggersDisconnected() throws Exception {
        serverSide.close();
        assertTrue(disconnected.await(3, TimeUnit.SECONDS));
        assertFalse(service.isConnected());
    }
}
