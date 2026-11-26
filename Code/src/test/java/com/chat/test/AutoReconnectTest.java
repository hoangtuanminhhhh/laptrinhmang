package com.chat.test;

import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.client.net.ReconnectManager;
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

    @Test
    void exponentialBackoffWithCustomMultiplierAndJitterStaysWithinBounds() {
        NetworkService svc = new NetworkService();
        service = svc;
        svc.configureReconnect(true, 1_000, 10_000, 0, 1.5, 0.2);

        ReconnectManager rm = svc.getReconnectManager();
        assertEquals(1_000L, rm.delayFor(1));
        assertEquals(1_500L, rm.delayFor(2));
        assertEquals(2_250L, rm.delayFor(3));
        assertEquals(3_375L, rm.delayFor(4));
        assertEquals(5_063L, rm.delayFor(5));
        assertEquals(7_594L, rm.delayFor(6));
        assertEquals(10_000L, rm.delayFor(10));

        for (int i = 0; i < 50; i++) {
            long firstJittered = rm.nextDelayFor(1);
            assertTrue(firstJittered >= 800L && firstJittered <= 1_200L,
                    "Jitter lần 1 phải nằm trong [800, 1200], nhận được: " + firstJittered);

            long cappedJittered = rm.nextDelayFor(10);
            assertTrue(cappedJittered >= 8_000L && cappedJittered <= 10_000L,
                    "Jitter ở ngưỡng maxDelay không được vượt quá 10000ms, nhận được: " + cappedJittered);
        }
    }

    @Test
    void retryReconnectNowBypassesLongBackoffAndRestoresConnectionImmediately() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 30_000, 60_000, 0);
        CountDownLatch scheduledFirst = new CountDownLatch(1);
        startClient(svc);
        svc.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { }
            @Override public void onDisconnected() { }
            @Override public void onReconnecting(int attempt, long delayMs) { scheduledFirst.countDown(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));

        first.close();
        assertTrue(scheduledFirst.await(3, TimeUnit.SECONDS));
        assertTrue(svc.getReconnectRemainingMs() > 5_000L);

        assertTrue(svc.retryReconnectNow());

        fakeServer.setSoTimeout(3000);
        Socket second = fakeServer.accept();
        assertTrue(reconnected.await(3, TimeUnit.SECONDS));
        assertTrue(svc.isConnected());
        assertEquals(0, svc.getReconnectAttempt());
        second.close();
    }

    @Test
    void retryReconnectNowWorksAfterReconnectGaveUp() throws Exception {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        NetworkService svc = new NetworkService();
        service = svc;
        svc.configureReconnect(true, 30, 60, 2);
        CountDownLatch initialError = new CountDownLatch(1);
        svc.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connectedFirst.countDown(); }
            @Override public void onDisconnected() { disconnected.countDown(); }
            @Override public void onReconnected() { reconnected.countDown(); }
            @Override public void onReconnectGaveUp() { gaveUp.countDown(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { initialError.countDown(); }
        });

        svc.connect("127.0.0.1", freePort, 500);
        assertTrue(initialError.await(3, TimeUnit.SECONDS));

        assertTrue(svc.retryReconnectNow());
        assertTrue(gaveUp.await(5, TimeUnit.SECONDS));
        assertFalse(svc.isReconnecting());

        try (ServerSocket reopened = new ServerSocket(freePort)) {
            assertTrue(svc.retryReconnectNow());
            reopened.setSoTimeout(3000);
            Socket accepted = reopened.accept();
            assertTrue(reconnected.await(3, TimeUnit.SECONDS));
            assertTrue(svc.isConnected());
            accepted.close();
        }
    }

    @Test
    void unexpectedDisconnectFiresConnectionLostExactlyOnceAndPreservesLifecycleOrder() throws Exception {
        NetworkService svc = new NetworkService();
        service = svc;
        svc.configureReconnect(true, 40, 200, 0);

        List<String> lifecycle = new CopyOnWriteArrayList<>();
        svc.addEventListener(new NetworkEventListener() {
            @Override
            public void onConnected() {
                lifecycle.add("connected");
                if (connectedCount.incrementAndGet() == 1) {
                    connectedFirst.countDown();
                }
            }
            @Override public void onConnectionLost() { lifecycle.add("lost"); }
            @Override public void onDisconnected() { lifecycle.add("disconnected"); }
            @Override public void onReconnecting(int attempt, long delayMs) { lifecycle.add("reconnecting"); }
            @Override
            public void onReconnected() {
                lifecycle.add("reconnected");
                reconnected.countDown();
            }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        svc.connect("127.0.0.1", fakeServer.getLocalPort(), 1000);
        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));

        first.close();
        fakeServer.setSoTimeout(3000);
        Socket second = fakeServer.accept();
        assertTrue(reconnected.await(3, TimeUnit.SECONDS));

        assertEquals(1L, lifecycle.stream().filter("lost"::equals).count());
        int iConn = lifecycle.indexOf("connected");
        int iLost = lifecycle.indexOf("lost");
        int iDisc = lifecycle.indexOf("disconnected");
        int iReconn = lifecycle.indexOf("reconnecting");
        int iRecd = lifecycle.indexOf("reconnected");
        assertTrue(iConn >= 0 && iConn < iLost && iLost < iDisc && iDisc < iReconn && iReconn < iRecd,
                "Thứ tự vòng đời phải chuẩn: " + lifecycle);
        second.close();
    }

    @Test
    void logoutClearsCredentialsSoSubsequentReconnectDoesNotAutoLogin() throws Exception {
        NetworkService svc = new NetworkService();
        svc.configureReconnect(true, 40, 200, 0);
        startClient(svc);

        Socket first = fakeServer.accept();
        assertTrue(connectedFirst.await(3, TimeUnit.SECONDS));
        DataInputStream firstIn = new DataInputStream(first.getInputStream());
        first.setSoTimeout(3000);

        svc.sendLogin("minh", "pw123");
        Packet login = PacketFramer.readPacket(firstIn);
        assertEquals(PacketType.LOGIN_REQUEST, login.getType());

        svc.sendLogout();
        Packet logout = PacketFramer.readPacket(firstIn);
        assertEquals(PacketType.LOGOUT_REQUEST, logout.getType());
        first.close();

        fakeServer.setSoTimeout(3000);
        Socket second = fakeServer.accept();
        assertTrue(reconnected.await(3, TimeUnit.SECONDS));

        svc.sendEchoMessage("after-logout");
        second.setSoTimeout(3000);
        Packet firstAfterReconnect = PacketFramer.readPacket(new DataInputStream(second.getInputStream()));
        assertEquals(PacketType.ECHO_REQUEST, firstAfterReconnect.getType());
        second.close();
    }
}
