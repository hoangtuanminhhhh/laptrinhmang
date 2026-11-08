package com.chat.client.net;

import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketFramer;
import com.chat.common.protocol.PacketType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dịch vụ quản lý kết nối TCP phía Client.
 * - Hỗ trợ Singleton (NetworkService.getInstance()) để ConfigDialogController và MainChatController
 *   dễ dàng dùng chung một kết nối.
 * - Toàn bộ thao tác I/O (connect, read, write, disconnect) đều chạy trên Worker Thread.
 */
public class NetworkService {

    private static final String CLIENT_CONFIG_FILE = "/client.properties";

    // Singleton instance giúp các Controller của TV4 truy cập thuận tiện
    private static final NetworkService INSTANCE = new NetworkService();

    // Thread pool xử lý các tác vụ kết nối và gửi gói tin (sử dụng Daemon thread để tự tắt khi đóng UI)
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Client-Network-IO-Worker");
        t.setDaemon(true);
        return t;
    });

    // Danh sách các listener đăng ký nhận sự kiện mạng (an toàn đa luồng)
    private final List<NetworkEventListener> listeners = new CopyOnWriteArrayList<>();

    // Lock đồng bộ hóa việc ghi dữ liệu ra DataOutputStream
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private ServerListener serverListener;
    private Thread listenerThread;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    // Cấu hình mặc định nạp từ client.properties
    private String defaultIp = "127.0.0.1";
    private int defaultPort = 8888;
    private int defaultTimeout = 5000;

    public NetworkService() {
        loadClientProperties();
    }

    public static NetworkService getInstance() {
        return INSTANCE;
    }

    /**
     * Đọc cấu hình mặc định từ file src/main/resources/client.properties
     */
    private void loadClientProperties() {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream(CLIENT_CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                defaultIp = props.getProperty("server.ip", "127.0.0.1").trim();
                defaultPort = Integer.parseInt(props.getProperty("server.port", "8888").trim());
                defaultTimeout = Integer.parseInt(props.getProperty("connection.timeout", "5000").trim());
            }
        } catch (Exception e) {
            System.err.println("[NetworkService] Không thể đọc client.properties, sử dụng cấu hình mặc định: " + e.getMessage());
        }
    }

    // =========================================================================
    // QUẢN LÝ LISTENER CHO UI (TV4)
    // =========================================================================

    public void addEventListener(NetworkEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeEventListener(NetworkEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Hàm tiện ích nếu UI chỉ muốn gán 1 listener duy nhất.
     */
    public void setEventListener(NetworkEventListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    // =========================================================================
    // KẾT NỐI BẤT ĐỒNG BỘ (NON-BLOCKING CONNECT)
    // =========================================================================

    /**
     * Kết nối tới Server với thời gian timeout mặc định trong client.properties.
     */
    public void connect(String ip, int port) {
        connect(ip, port, defaultTimeout);
    }

    /**
     * Thực hiện kết nối TCP bất đồng bộ trên Worker Thread.
     * Tuyệt đối không làm đơ (block) giao diện JavaFX trong lúc chờ kết nối.
     *
     * @param ip        Địa chỉ IP hoặc hostname của Server
     * @param port      Cổng lắng nghe của Server (1 - 65535)
     * @param timeoutMs Thời gian chờ tối đa (ms)
     */
    public void connect(String ip, int port, int timeoutMs) {
        if (connecting.get()) {
            notifyError("Đang trong quá trình kết nối, vui lòng đợi...", null);
            return;
        }

        // Nếu đang có kết nối cũ thì ngắt trước khi tạo kết nối mới
        if (isConnected()) {
            disconnect();
        }

        connecting.set(true);
        notifyConnecting(ip, port);

        ioExecutor.submit(() -> {
            try {
                // Validate sơ bộ đầu vào
                if (ip == null || ip.trim().isEmpty()) {
                    throw new UnknownHostException("Địa chỉ IP không được để trống.");
                }
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Số cổng (Port) phải nằm trong khoảng từ 1 đến 65535.");
                }

                Socket newSocket = new Socket();
                // Kết nối với timeout xác định
                newSocket.connect(new InetSocketAddress(ip.trim(), port), Math.max(timeoutMs, 1000));
                newSocket.setKeepAlive(true);
                newSocket.setTcpNoDelay(true);

                this.socket = newSocket;
                this.inputStream = new DataInputStream(newSocket.getInputStream());
                this.outputStream = new DataOutputStream(newSocket.getOutputStream());

                connected.set(true);
                connecting.set(false);

                // Thông báo kết nối thành công cho UI (Đèn Xanh)
                notifyConnected();

                // Khởi chạy luồng chạy ngầm đọc dữ liệu từ Server
                serverListener = new ServerListener(inputStream, this);
                listenerThread = new Thread(serverListener, "Server-Packet-Listener");
                listenerThread.setDaemon(true);
                listenerThread.start();

            } catch (IllegalArgumentException e) {
                connecting.set(false);
                closeResourcesSilently();
                notifyError(e.getMessage(), e);
            } catch (UnknownHostException e) {
                connecting.set(false);
                closeResourcesSilently();
                notifyError("Không tìm thấy địa chỉ máy chủ '" + ip + "'. Vui lòng kiểm tra lại IP/Tên miền.", e);
            } catch (SocketTimeoutException e) {
                connecting.set(false);
                closeResourcesSilently();
                notifyError("Hết thời gian chờ kết nối (" + timeoutMs + "ms) tới " + ip + ":" + port + ". Kiểm tra lại IP hoặc tường lửa.", e);
            } catch (ConnectException e) {
                connecting.set(false);
                closeResourcesSilently();
                notifyError("Máy chủ " + ip + ":" + port + " từ chối kết nối (Server có thể chưa bật hoặc sai Port).", e);
            } catch (IOException e) {
                connecting.set(false);
                closeResourcesSilently();
                notifyError("Lỗi mạng khi kết nối tới máy chủ: " + e.getMessage(), e);
            }
        });
    }

    // =========================================================================
    // GỬI GÓI TIN BẤT ĐỒNG BỘ (NON-BLOCKING SEND)
    // =========================================================================

    /**
     * Gửi một đối tượng Packet bất kỳ tới Server trên Worker Thread.
     *
     * @param packet Gói tin cần gửi
     */
    public void sendPacket(Packet packet) {
        if (!isConnected() || outputStream == null) {
            notifyError("Chưa kết nối tới máy chủ. Không thể gửi tin nhắn.", null);
            return;
        }

        ioExecutor.submit(() -> {
            synchronized (writeLock) {
                try {
                    PacketFramer.writePacket(outputStream, packet);
                    outputStream.flush();
                } catch (IOException e) {
                    notifyError("Không thể gửi dữ liệu tới máy chủ: " + e.getMessage(), e);
                    handleConnectionClosed();
                }
            }
        });
    }

    /**
     * Hàm tiện ích cho TV4 (MainChatController): Gửi nhanh tin nhắn Echo trong Phase 1 (Tuần 1+2).
     *
     * @param message Nội dung văn bản người dùng nhập
     */
    public void sendEchoMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Packet echoPacket = new Packet(PacketType.ECHO_REQUEST, message);
        sendPacket(echoPacket);
    }

    // =========================================================================
    // NGẮT KẾT NỐI & DỌN DẸP TÀI NGUYÊN
    // =========================================================================

    /**
     * Chủ động ngắt kết nối khỏi Server (chạy bất đồng bộ để không block UI).
     */
    public void disconnect() {
        ioExecutor.submit(this::handleConnectionClosed);
    }

    /**
     * Xử lý đóng Socket/Stream và phát sự kiện onDisconnected() (chỉ phát 1 lần).
     */
    synchronized void handleConnectionClosed() {
        boolean wasConnected = connected.getAndSet(false);
        connecting.set(false);

        if (serverListener != null) {
            serverListener.stop();
            serverListener = null;
        }

        if (listenerThread != null && listenerThread != Thread.currentThread()) {
            listenerThread.interrupt();
            listenerThread = null;
        }

        closeResourcesSilently();

        if (wasConnected) {
            notifyDisconnected();
        }
    }

    private void closeResourcesSilently() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (IOException ignored) {}
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        inputStream = null;
        outputStream = null;
        socket = null;
    }

    /**
     * Gọi khi đóng hoàn toàn ứng dụng Client (trong hàm stop() của ClientApp JavaFX).
     */
    public void shutdown() {
        handleConnectionClosed();
        ioExecutor.shutdownNow();
    }

    // =========================================================================
    // ĐIỀU PHỐI SỰ KIỆN TỚI CÁC LISTENER
    // =========================================================================

    void notifyConnecting(String ip, int port) {
        for (NetworkEventListener listener : listeners) {
            listener.onConnecting(ip, port);
        }
    }

    void notifyConnected() {
        for (NetworkEventListener listener : listeners) {
            listener.onConnected();
        }
    }

    void notifyDisconnected() {
        for (NetworkEventListener listener : listeners) {
            listener.onDisconnected();
        }
    }

    void notifyPacketReceived(Packet packet) {
        for (NetworkEventListener listener : listeners) {
            listener.onPacketReceived(packet);
        }
    }

    void notifyError(String friendlyMessage, Throwable exception) {
        for (NetworkEventListener listener : listeners) {
            listener.onError(friendlyMessage, exception);
        }
    }

    // =========================================================================
    // GETTERS TRẠNG THÁI & CẤU HÌNH MẶC ĐỊNH
    // =========================================================================

    public boolean isConnected() {
        return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isConnecting() {
        return connecting.get();
    }

    public String getDefaultIp() {
        return defaultIp;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public int getDefaultTimeout() {
        return defaultTimeout;
    }
}