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
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tiến trình tự động kết nối lại (Auto-reconnect) và Heartbeat mất mạng của TV3.
 */
class AutoReconnectTest {

    private ServerSocket fakeServer;
    private NetworkService service;

    private final CountDownLatch connectedFirst = new CountDownLatch(1);
    private final CountDownLatch disconnected = new CountDownLatch(1);
    private final CountDownLatch lost = new CountDownLatch(1);
    private final CountDownLatch reconnected = new CountDownLatch(1);
    private final CountDownLatch gaveUp = new CountDownLatch(1);
    private final AtomicInteger connectedCount = new AtomicInteger();
    private final List<Long> backoffDelays = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fakeServer = new ServerSocket(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (service != null) service.shutdown();
        if (fakeServer != null && !fakeServer.isClosed()) fakeServer.close();
    }

    private void startClient(NetworkService svc) {
        service = svc;
        service.addEventListener(new NetworkEventListener() {
            @Override
            public void onConnected() {
                if (connectedCount.incrementAndGet() == 1) {
                    connectedFirst.countDown();
                }
            }
            @Override public void onDisconnected() { disconnected.countDown(); }
            @Override public void onConnectionLost() { lost.countDown(); }
            @Override public void onReconnecting(int attempt, long delayMs) { backoffDelays.add(delayMs); }
            @Override public void onReconnected() { reconnected.countDown(); }
            @Override public void onReconnectGaveUp() { gaveUp.countDown(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });
        service.connect("127.0.0.1", fakeServer.getLocalPort(), 1000);
    }

    @Test
    void reconnectsAutomaticallyAfterServerDropsConnection() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 50, 200, 0);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));

        first.close();
        assertTrue(disconnected.await(3, TimeUnit.SECONDS));

        fakeServer.setSoTimeout(3000);
        Socket second = fakeServer.accept();
        assertTrue(reconnected.await(3, TimeUnit.SECONDS));
        assertTrue(service.isConnected());
        assertFalse(service.isNetworkLost());
        assertFalse(backoffDelays.isEmpty());
        second.close();
    }

    @Test
    void reLoginsAutomaticallyAfterReconnect() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 50, 200, 0);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));
        service.sendLogin("minh", "pw123");
        first.setSoTimeout(3000);
        Packet firstLogin = PacketFramer.readPacket(new DataInputStream(first.getInputStream()));
        assertEquals(PacketType.LOGIN_REQUEST, firstLogin.getType());
        first.close();

        fakeServer.setSoTimeout(3000);
        Socket second = fakeServer.accept();
        second.setSoTimeout(3000);
        Packet relogin = PacketFramer.readPacket(new DataInputStream(second.getInputStream()));
        assertEquals(PacketType.LOGIN_REQUEST, relogin.getType());
        assertEquals("minh", relogin.getPayloadValue("username"));
        assertEquals("pw123", relogin.getPayloadValue("password"));
        second.close();
    }

    @Test
    void manualDisconnectDoesNotTriggerReconnect() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 50, 200, 0);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));

        service.disconnect();
        assertTrue(disconnected.await(3, TimeUnit.SECONDS));

        fakeServer.setSoTimeout(600);
        assertThrows(SocketTimeoutException.class, () -> fakeServer.accept());
        assertFalse(service.isReconnecting());
        first.close();
    }

    @Test
    void backoffDoublesAndGivesUpAfterMaxAttempts() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 40, 100, 4);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));
        fakeServer.close();
        first.close();

        assertTrue(gaveUp.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(40L, 80L, 100L, 100L), backoffDelays);
        assertFalse(service.isReconnecting());
    }

    @Test
    void heartbeatTimeoutTriggersNetworkLostAndReconnect() throws Exception {
        NetworkService svc = new NetworkService(60, 3);
        svc.configureReconnect(true, 50, 200, 0);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));
        assertTrue(lost.await(3, TimeUnit.SECONDS));

        fakeServer.setSoTimeout(4000);
        Socket second = fakeServer.accept();
        assertTrue(reconnected.await(3, TimeUnit.SECONDS));
        assertFalse(service.isNetworkLost());
        first.close();
        second.close();
    }
}
