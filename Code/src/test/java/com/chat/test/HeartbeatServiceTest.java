package com.chat.test;

import com.chat.client.net.HeartbeatService;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử cơ chế Heartbeat Ping/Pong (TV3).
 */
class HeartbeatServiceTest {

    private ServerSocket fakeServer;
    private Socket serverSide;
    private NetworkService service;

    @AfterEach
    void tearDown() throws Exception {
        if (service != null) service.shutdown();
        if (serverSide != null) serverSide.close();
        if (fakeServer != null) fakeServer.close();
    }

    @Test
    void raisesLostFlagAfterMaxMissedPongs() throws Exception {
        AtomicInteger pings = new AtomicInteger();
        CountDownLatch lost = new CountDownLatch(1);
        HeartbeatService hb = new HeartbeatService(50, 3, pings::incrementAndGet, lost::countDown);

        hb.start();
        assertTrue(lost.await(2, TimeUnit.SECONDS));
        assertEquals(3, pings.get());
        assertTrue(hb.isNetworkLost());
        assertFalse(hb.isRunning());
    }

    @Test
    void pongReceivedResetsMissedCounterAndComputesRtt() throws Exception {
        AtomicInteger lostCount = new AtomicInteger();
        HeartbeatService[] holder = new HeartbeatService[1];
        holder[0] = new HeartbeatService(30, 3, () -> holder[0].onPongReceived(), lostCount::incrementAndGet);

        holder[0].start();
        Thread.sleep(300);
        holder[0].stop();

        assertEquals(0, lostCount.get());
        assertFalse(holder[0].isNetworkLost());
        assertTrue(holder[0].getLastRttMs() >= 0);
    }

    @Test
    void rejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new HeartbeatService(0, 3, () -> { }, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> new HeartbeatService(10, 0, () -> { }, () -> { }));
    }

    @Test
    void silentServerTriggersNetworkLostFlagAndDisconnects() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService(60, 3);

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch lost = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { disconnected.countDown(); }
            @Override public void onConnectionLost() { lost.countDown(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 2000);
        serverSide = fakeServer.accept();
        assertTrue(connected.await(3, TimeUnit.SECONDS));

        assertTrue(lost.await(3, TimeUnit.SECONDS));
        assertTrue(disconnected.await(3, TimeUnit.SECONDS));
        assertTrue(service.isNetworkLost());
        assertFalse(service.isConnected());
    }

    @Test
    void networkServiceKeepsAliveWhenServerRepliesPong() throws Exception {
        fakeServer = new ServerSocket(0);
        service = new NetworkService(80, 3);

        CountDownLatch connected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        service.connect("127.0.0.1", fakeServer.getLocalPort(), 2000);
        serverSide = fakeServer.accept();
        assertTrue(connected.await(3, TimeUnit.SECONDS));

        DataInputStream in = new DataInputStream(serverSide.getInputStream());
        DataOutputStream out = new DataOutputStream(serverSide.getOutputStream());
        serverSide.setSoTimeout(2000);

        for (int i = 0; i < 2; i++) {
            Packet ping = PacketFramer.readPacket(in);
            assertEquals(PacketType.PING, ping.getType());
            PacketFramer.writePacket(out, new Packet(PacketType.PONG, "SERVER", "guest"));
        }
        assertTrue(service.isConnected());
        assertFalse(service.isNetworkLost());
    }
}
