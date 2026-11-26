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

    @Test
    void rejectedOrShutDownUiExecutorDoesNotCrashNetworkWorkerThread() {
        ExecutorService fx = fakeFxExecutor();
        fx.shutdown();

        RecordingListener target = new RecordingListener();
        NetworkEventListener wrapped = NetworkEventListener.onExecutor(target, fx);
        assertNotNull(wrapped);
        assertNull(NetworkEventListener.onExecutor(null, fx));

        assertDoesNotThrow(() -> {
            wrapped.onConnectionLost();
            wrapped.onDisconnected();
            wrapped.onReconnecting(1, 1000);
            wrapped.onReconnectGaveUp();
            wrapped.onError("err", null);
        });
    }

    @Test
    void endToEndReconnectLifecycleEventsArriveOnUiExecutorInOrder() throws Exception {
        ServerSocket server = new ServerSocket(0);
        ExecutorService fx = fakeFxExecutor();
        NetworkService service = new NetworkService();
        service.configureReconnect(true, 40, 150, 0);

        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch reconnectedLatch = new CountDownLatch(1);
        RecordingListener target = new RecordingListener() {
            @Override
            public void onConnected() {
                super.onConnected();
                connectedLatch.countDown();
            }

            @Override
            public void onReconnected() {
                super.onReconnected();
                reconnectedLatch.countDown();
            }
        };
        service.addEventListener(NetworkEventListener.onExecutor(target, fx));

        Socket first = null;
        Socket second = null;
        try {
            service.connect("127.0.0.1", server.getLocalPort(), 1000);
            first = server.accept();
            assertTrue(connectedLatch.await(3, TimeUnit.SECONDS));

            first.close();
            server.setSoTimeout(3000);
            second = server.accept();
            assertTrue(reconnectedLatch.await(4, TimeUnit.SECONDS));
        } finally {
            service.shutdown();
            fx.shutdown();
            assertTrue(fx.awaitTermination(3, TimeUnit.SECONDS));
            if (first != null) first.close();
            if (second != null) second.close();
            server.close();
        }

        assertFalse(target.events.isEmpty());
        for (String event : target.events) {
            assertTrue(event.endsWith("@Fake-FX-Thread"), "Event phải chạy trên UI Executor: " + event);
        }
        int iConn = target.events.indexOf("connected@Fake-FX-Thread");
        int iLost = target.events.indexOf("lost@Fake-FX-Thread");
        int iDisc = target.events.indexOf("disconnected@Fake-FX-Thread");
        int iReconn = target.events.indexOf("reconnecting@Fake-FX-Thread");
        int iRecd = target.events.indexOf("reconnected@Fake-FX-Thread");
        assertTrue(target.events.contains("connecting@Fake-FX-Thread"));
        assertTrue(iConn >= 0 && iConn < iLost && iLost < iDisc && iDisc < iReconn && iReconn < iRecd,
                "Thứ tự callback UI không hợp lệ: " + target.events);
    }

    @Test
    void networkServiceCallsReturnImmediatelyWithoutBlockingCallerThread() throws Exception {
        ServerSocket server = new ServerSocket(0);
        NetworkService service = new NetworkService();
        CountDownLatch connected = new CountDownLatch(1);
        service.addEventListener(new NetworkEventListener() {
            @Override public void onConnected() { connected.countDown(); }
            @Override public void onDisconnected() { }
            @Override public void onPacketReceived(Packet packet) { }
            @Override public void onError(String friendlyMessage, Throwable exception) { }
        });

        Socket accepted = null;
        try {
            long startConnectNs = System.nanoTime();
            service.connect("127.0.0.1", server.getLocalPort(), 2000);
            long connectCallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startConnectNs);
            assertTrue(connectCallMs < 500, "connect() không được block caller thread: " + connectCallMs + "ms");

            accepted = server.accept();
            assertTrue(connected.await(3, TimeUnit.SECONDS));

            long startSendNs = System.nanoTime();
            service.sendEchoMessage("hi");
            long sendCallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startSendNs);
            assertTrue(sendCallMs < 500, "sendEchoMessage() không được block caller thread: " + sendCallMs + "ms");

            long startDiscNs = System.nanoTime();
            service.disconnect();
            long discCallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startDiscNs);
            assertTrue(discCallMs < 500, "disconnect() không được block caller thread: " + discCallMs + "ms");

            long startRetryNs = System.nanoTime();
            service.retryReconnectNow();
            long retryCallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startRetryNs);
            assertTrue(retryCallMs < 500, "retryReconnectNow() không được block caller thread: " + retryCallMs + "ms");

            long startShutdownNs = System.nanoTime();
            service.shutdown();
            long shutdownCallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startShutdownNs);
            assertTrue(shutdownCallMs < 500, "shutdown() không được block caller thread: " + shutdownCallMs + "ms");
        } finally {
            service.shutdown();
            if (accepted != null) accepted.close();
            server.close();
        }
    }
}
