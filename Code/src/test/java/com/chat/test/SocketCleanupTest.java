package com.chat.test;

import com.chat.client.net.HeartbeatService;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử giải phóng Socket, Stream, Heartbeat và Worker Thread an toàn (TV3).
 */
class SocketCleanupTest {

    private ServerSocket fakeServer;
    private NetworkService service;

    @AfterEach
    void tearDown() throws Exception {
        if (service != null) {
            service.shutdown();
        }
        if (fakeServer != null && !fakeServer.isClosed()) {
            fakeServer.close();
        }
    }

    @Test
    void disconnectClosesSocketStreamsAndSendsCleanEofWithoutSpuriousErrors() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        List<String> errors = new CopyOnWriteArrayList<>();

        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { disconnected.countDown(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { errors.add(friendlyMessage); }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 2000);
        try (Socket serverSide = fakeServer.accept()) {
            assertTrue(connected.await(3, TimeUnit.SECONDS));

            service.sendEchoMessage("ping-before-close");
            serverSide.setSoTimeout(2000);
            Packet echo = PacketFramer.readPacket(new DataInputStream(serverSide.getInputStream()));
            assertEquals("ping-before-close", echo.getPayload());

            service.disconnect();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));
            assertEquals(-1, serverSide.getInputStream().read(), "Server phải nhận được EOF sạch khi Client disconnect");
            assertFalse(service.isConnected());
            assertFalse(service.isReconnecting());
            assertNull(service.getHeartbeatService());
            assertTrue(errors.isEmpty(), "Ngắt kết nối chủ động không được phát lỗi giả: " + errors);
        }
    }

    @Test
    void repeatedDisconnectAndShutdownAreIdempotentAndRejectFurtherOperationsSafely() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();
        int port = fakeServer.getLocalPort();

        CountDownLatch connected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        service.connect("127.0.0.1", port, 2000);
        try (Socket serverSide = fakeServer.accept()) {
            assertTrue(connected.await(3, TimeUnit.SECONDS));

            assertDoesNotThrow(() -> {
                service.disconnect();
                service.disconnect();
                service.disconnect();
                service.shutdown();
                service.shutdown();
                service.shutdown();
            });

            assertTrue(service.isShutdown());
            assertFalse(service.isConnected());
            assertFalse(service.isConnecting());
            assertFalse(service.isReconnecting());

            assertDoesNotThrow(() -> {
                service.connect("127.0.0.1", port, 500);
                service.sendEchoMessage("x");
                assertFalse(service.retryReconnectNow());
            });
            assertFalse(service.isConnected());
            serverSide.setSoTimeout(1000);
            assertEquals(-1, serverSide.getInputStream().read());
        }
    }

    @Test
    void concurrentConnectAndImmediateDisconnectLeavesNoOpenServerSockets() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();
        List<Socket> acceptedSockets = new CopyOnWriteArrayList<>();
        AtomicBoolean acceptRunning = new AtomicBoolean(true);

        Thread acceptThread = new Thread(() -> {
            while (acceptRunning.get() && !fakeServer.isClosed()) {
                try {
                    acceptedSockets.add(fakeServer.accept());
                } catch (IOException ignored) {
                    break;
                }
            }
        }, "Test-Accept-Loop");
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            for (int i = 0; i < 8; i++) {
                service.connect("127.0.0.1", fakeServer.getLocalPort(), 1500);
                Thread.sleep(i % 4);
                service.disconnect();
            }

            long deadline = System.currentTimeMillis() + 1500;
            while ((service.isConnected() || service.isConnecting()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(service.isConnected());
            assertFalse(service.isConnecting());
        } finally {
            acceptRunning.set(false);
            fakeServer.close();
            acceptThread.join(1000);
            for (Socket accepted : acceptedSockets) {
                try {
                    accepted.setSoTimeout(1500);
                    assertEquals(-1, accepted.getInputStream().read(),
                            "Mọi socket đã bắt tay trong lúc race đều phải được đóng sạch");
                } finally {
                    accepted.close();
                }
            }
        }
    }

    @Test
    void multipleDropAndReconnectCyclesCleanOldResourcesAndWorkReliably() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService();
        service.configureReconnect(true, 35, 120, 0);

        AtomicInteger connectedCount = new AtomicInteger();
        AtomicInteger reconnectedCount = new AtomicInteger();
        CountDownLatch firstConnected = new CountDownLatch(1);
        CountDownLatch firstReconnected = new CountDownLatch(1);
        CountDownLatch secondReconnected = new CountDownLatch(1);

        service.addEventListener(new NetworkEventListener() {
            @Override
            public void onConnected() {
                if (connectedCount.incrementAndGet() == 1) {
                    firstConnected.countDown();
                }
            }
            @Override public void onDisconnected() { }
            @Override
            public void onReconnected() {
                int count = reconnectedCount.incrementAndGet();
                if (count == 1) {
                    firstReconnected.countDown();
                } else if (count == 2) {
                    secondReconnected.countDown();
                }
            }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 1500);
        fakeServer.setSoTimeout(3000);

        Socket s1 = fakeServer.accept();
        assertTrue(firstConnected.await(3, TimeUnit.SECONDS));
        HeartbeatService hb1 = awaitHeartbeatRunning(service);
        assertNotNull(hb1);
        assertTrue(hb1.isRunning());
        s1.close();

        Socket s2 = fakeServer.accept();
        assertTrue(firstReconnected.await(3, TimeUnit.SECONDS));
        HeartbeatService hb2 = awaitHeartbeatRunning(service);
        assertNotNull(hb2);
        assertTrue(hb2.isRunning());
        assertNotSame(hb1, hb2);
        assertFalse(hb1.isRunning());
        s2.close();

        Socket s3 = fakeServer.accept();
        try {
            assertTrue(secondReconnected.await(3, TimeUnit.SECONDS));
            HeartbeatService hb3 = awaitHeartbeatRunning(service);
            assertNotNull(hb3);
            assertTrue(hb3.isRunning());
            assertNotSame(hb2, hb3);
            assertFalse(hb2.isRunning());

            service.sendEchoMessage("cycle-3");
            s3.setSoTimeout(2000);
            Packet p = PacketFramer.readPacket(new DataInputStream(s3.getInputStream()));
            assertEquals("cycle-3", p.getPayload());

            service.shutdown();
            assertEquals(-1, s3.getInputStream().read());
            assertNull(service.getHeartbeatService());
            assertFalse(service.isConnected());
            assertFalse(service.isReconnecting());
        } finally {
            s3.close();
        }
    }

    private static HeartbeatService awaitHeartbeatRunning(NetworkService svc) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline) {
            HeartbeatService hb = svc.getHeartbeatService();
            if (hb != null && hb.isRunning()) {
                return hb;
            }
            Thread.sleep(10);
        }
        return svc.getHeartbeatService();
    }
}
