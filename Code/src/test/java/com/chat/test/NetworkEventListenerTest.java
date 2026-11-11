package com.chat.test;

import com.chat.client.net.FxNetworkEventListener;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử callback an toàn đa luồng của NetworkEventListener & FxNetworkEventListener (TV3).
 */
class NetworkEventListenerTest {

    private static class RecordingListener implements NetworkEventListener {
        final List<String> events = new CopyOnWriteArrayList<>();

        private void record(String name) {
            events.add(name + "@" + Thread.currentThread().getName());
        }

        @Override public void onConnecting(String ip, int port) { record("connecting"); }
        @Override public void onConnected() { record("connected"); }
        @Override public void onDisconnected() { record("disconnected"); }
        @Override public void onConnectionLost() { record("lost"); }
        @Override public void onReconnecting(int attempt, long delayMs) { record("reconnecting"); }
        @Override public void onReconnected() { record("reconnected"); }
        @Override public void onReconnectGaveUp() { record("gaveUp"); }
        @Override public void onPacketReceived(Packet packet) { record("packet"); }
        @Override public void onError(String friendlyMessage, Throwable exception) { record("error"); }
    }

    private ExecutorService fakeFxExecutor() {
        return Executors.newSingleThreadExecutor(r -> new Thread(r, "Fake-FX-Thread"));
    }

    @Test
    void allCallbacksAreMarshaledToUiThreadInStrictOrder() throws Exception {
        ExecutorService fx = fakeFxExecutor();
        RecordingListener target = new RecordingListener();
        NetworkEventListener wrapped = new FxNetworkEventListener(target, fx);

        Thread worker = new Thread(() -> {
            wrapped.onConnecting("127.0.0.1", 8888);
            wrapped.onConnected();
            wrapped.onPacketReceived(new Packet(PacketType.MESSAGE_RECEIVE, "a", "b"));
            wrapped.onConnectionLost();
            wrapped.onDisconnected();
            wrapped.onReconnecting(1, 2000);
            wrapped.onReconnected();
            wrapped.onReconnectGaveUp();
            wrapped.onError("err", null);
        }, "Network-Worker");
        worker.start();
        worker.join();
        fx.shutdown();
        assertTrue(fx.awaitTermination(3, TimeUnit.SECONDS));

        assertEquals(List.of(
                "connecting@Fake-FX-Thread",
                "connected@Fake-FX-Thread",
                "packet@Fake-FX-Thread",
                "lost@Fake-FX-Thread",
                "disconnected@Fake-FX-Thread",
                "reconnecting@Fake-FX-Thread",
                "reconnected@Fake-FX-Thread",
                "gaveUp@Fake-FX-Thread",
                "error@Fake-FX-Thread"
        ), target.events);
    }

    @Test
    void exceptionInOneUiCallbackDoesNotPreventSubsequentEvents() throws Exception {
        ExecutorService fx = fakeFxExecutor();
        RecordingListener healthy = new RecordingListener();
        NetworkEventListener faulty = new NetworkEventListener() {
            @Override public void onConnected() { throw new IllegalStateException("UI crash"); }
            @Override public void onDisconnected() { healthy.onDisconnected(); }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        };
        NetworkEventListener wrapped = new FxNetworkEventListener(faulty, fx);

        wrapped.onConnected();
        wrapped.onDisconnected();
        fx.shutdown();
        assertTrue(fx.awaitTermination(3, TimeUnit.SECONDS));

        assertEquals(List.of("disconnected@Fake-FX-Thread"), healthy.events);
    }

    @Test
    void networkServiceIsolatesFaultyListeners() throws Exception {
        ServerSocket server = new ServerSocket(0);
        NetworkService service = new NetworkService();
        CountDownLatch healthyConnected = new CountDownLatch(1);

        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { throw new RuntimeException("listener bug"); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { healthyConnected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        Socket accepted = null;
        try {
            service.connect("127.0.0.1", server.getLocalPort(), 1000);
            accepted = server.accept();
            assertTrue(healthyConnected.await(3, TimeUnit.SECONDS));
            assertTrue(service.isConnected());
        } finally {
            service.shutdown();
            if (accepted != null) accepted.close();
            server.close();
        }
    }

    @Test
    void onFxThreadFactoryWrapsOnceAndSupportsRemovalByDelegate() {
        RecordingListener target = new RecordingListener();
        NetworkEventListener wrapped = NetworkEventListener.onFxThread(target);
        assertInstanceOf(FxNetworkEventListener.class, wrapped);
        assertSame(wrapped, NetworkEventListener.onFxThread(wrapped));
        assertSame(target, ((FxNetworkEventListener) wrapped).getDelegate());

        NetworkService service = new NetworkService();
        service.addUiEventListener(target);
        service.removeEventListener(target);
        service.shutdown();
    }
}
