package com.chat.client.net;

import com.chat.common.model.Message;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Dịch vụ quản lý kết nối TCP và các hàm gọi mạng phía Client (Thành viên 3).
 * - Hỗ trợ Singleton (NetworkService.getInstance()) để các Controller (LoginController, MainChatController)
 *   dễ dàng dùng chung một kết nối.
 * - Toàn bộ thao tác Socket I/O (connect, read, write, disconnect) đều chạy trên Worker Thread,
 *   tuyệt đối không chạy trên JavaFX Application Thread.
 */
public class NetworkService {

    private static final String CLIENT_CONFIG_FILE = "/client.properties";
    public static final String SERVER_ID = "SERVER";
    public static final String GUEST_USER = "guest";

    /** Giới hạn kích thước chuỗi Base64 của ảnh đại diện (5MB, nhỏ hơn ngưỡng 10MB của PacketFramer). */
    public static final int MAX_AVATAR_BASE64_LENGTH = 5 * 1024 * 1024;

    // Singleton instance giúp các Controller của TV4 truy cập thuận tiện
    private static final NetworkService INSTANCE = new NetworkService();

    // Thread pool xử lý các tác vụ kết nối và gửi gói tin (Daemon thread để tự tắt khi đóng UI)
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Client-Network-IO-Worker");
        t.setDaemon(true);
        return t;
    });

    // Danh sách các listener đăng ký nhận sự kiện mạng (an toàn đa luồng)
    private final List<NetworkEventListener> listeners = new CopyOnWriteArrayList<>();

    // Lock đồng bộ hóa việc ghi dữ liệu ra DataOutputStream giữa các luồng
    private final Object writeLock = new Object();

    private volatile Socket socket;
    private volatile DataInputStream inputStream;
    private volatile DataOutputStream outputStream;
    private volatile ServerListener serverListener;
    private volatile Thread listenerThread;
    private volatile HeartbeatService heartbeatService;
    private final ReconnectManager reconnectManager = new ReconnectManager(this);

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicBoolean networkLost = new AtomicBoolean(false);
    private final AtomicLong connectGeneration = new AtomicLong(0);

    // true khi người dùng chủ động ngắt kết nối hoặc tắt ứng dụng -> không kích hoạt Auto-reconnect
    private volatile boolean manualDisconnect = false;

    // Thông số kết nối thành công gần nhất (dùng cho Auto-reconnect)
    private volatile String lastIp;
    private volatile int lastPort;
    private volatile int lastTimeoutMs;

    // Username của phiên đăng nhập hiện tại (dùng làm trường "sender" cho các gói tin gửi đi)
    private volatile String currentUsername = GUEST_USER;

    // Mật khẩu tạm giữ trong RAM để tự đăng nhập lại sau khi Auto-reconnect; xóa khi logout/disconnect
    private volatile String lastPassword;

    // Cấu hình mặc định nạp từ client.properties
    private String defaultIp = "127.0.0.1";
    private int defaultPort = 8888;
    private int defaultTimeout = 5000;
    private long heartbeatIntervalMs = HeartbeatService.DEFAULT_INTERVAL_MS;
    private int heartbeatMaxMissed = HeartbeatService.DEFAULT_MAX_MISSED;

    public NetworkService() {
        loadClientProperties();
    }

    /**
     * Constructor cho phép tùy chỉnh thông số Heartbeat (phục vụ kiểm thử tự động).
     */
    public NetworkService(long heartbeatIntervalMs, int heartbeatMaxMissed) {
        loadClientProperties();
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatMaxMissed = heartbeatMaxMissed;
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
                heartbeatIntervalMs = Long.parseLong(props.getProperty("heartbeat.interval",
                        String.valueOf(HeartbeatService.DEFAULT_INTERVAL_MS)).trim());
                heartbeatMaxMissed = Integer.parseInt(props.getProperty("heartbeat.max.missed",
                        String.valueOf(HeartbeatService.DEFAULT_MAX_MISSED)).trim());
                reconnectManager.configure(
                        Boolean.parseBoolean(props.getProperty("reconnect.enabled",
                                String.valueOf(ReconnectManager.DEFAULT_ENABLED)).trim()),
                        Long.parseLong(props.getProperty("reconnect.initial.delay",
                                String.valueOf(ReconnectManager.DEFAULT_INITIAL_DELAY_MS)).trim()),
                        Long.parseLong(props.getProperty("reconnect.max.delay",
                                String.valueOf(ReconnectManager.DEFAULT_MAX_DELAY_MS)).trim()),
                        Integer.parseInt(props.getProperty("reconnect.max.attempts",
                                String.valueOf(ReconnectManager.DEFAULT_MAX_ATTEMPTS)).trim())
                );
            }
        } catch (Exception e) {
            System.err.println("[NetworkService] Không thể đọc client.properties, sử dụng cấu hình mặc định: " + e.getMessage());
        }
    }

    // =========================================================================
    // QUẢN LÝ LISTENER CHO UI (TV4)
    // =========================================================================

    public void addEventListener(NetworkEventListener listener) {
        if (listener == null) {
            return;
        }
        for (NetworkEventListener existing : listeners) {
            if (existing == listener) {
                return;
            }
        }
        listeners.add(listener);
    }

    /**
     * Đăng ký listener UI an toàn luồng: mọi callback tự động chạy trên JavaFX Thread
     * thông qua {@link javafx.application.Platform#runLater(Runnable)}.
     */
    public void addUiEventListener(NetworkEventListener uiListener) {
        if (uiListener == null) {
            return;
        }
        NetworkEventListener wrapped = NetworkEventListener.onFxThread(uiListener);
        if (!containsListenerOrDelegate(uiListener)) {
            listeners.add(wrapped);
        }
    }

    public void removeEventListener(NetworkEventListener listener) {
        if (listener == null) {
            return;
        }
        listeners.removeIf(item -> item == listener
                || (item instanceof FxNetworkEventListener fx && fx.getDelegate() == listener));
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

    /**
     * Thay thế toàn bộ listener bằng một listener UI duy nhất tự động chạy trên JavaFX Thread.
     */
    public void setUiEventListener(NetworkEventListener uiListener) {
        listeners.clear();
        if (uiListener != null) {
            listeners.add(NetworkEventListener.onFxThread(uiListener));
        }
    }

    private boolean containsListenerOrDelegate(NetworkEventListener target) {
        for (NetworkEventListener existing : listeners) {
            if (existing == target) {
                return true;
            }
            if (existing instanceof FxNetworkEventListener fx && fx.getDelegate() == target) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // KẾT NỐI BẤT ĐỒNG BỘ (NON-BLOCKING CONNECT)
    // =========================================================================

    /**
     * Kết nối tới Server với cấu hình IP/Port và timeout mặc định trong client.properties.
     */
    public void connect() {
        connect(defaultIp, defaultPort, defaultTimeout);
    }

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
        if (!connecting.compareAndSet(false, true)) {
            notifyError("Đang trong quá trình kết nối, vui lòng đợi...", null);
            return;
        }

        final long gen = connectGeneration.incrementAndGet();
        reconnectManager.cancel();
        manualDisconnect = false;
        notifyConnecting(ip, port);

        ioExecutor.submit(() -> {
            if (isConnected()) {
                handleConnectionClosed(false);
            }
            establishConnection(ip, port, timeoutMs, true, gen);
        });
    }

    boolean establishConnection(String ip, int port, int timeoutMs, boolean reportErrors, long expectedGen) {
        connecting.set(true);
        Socket newSocket = null;
        try {
            if (isBlank(ip)) {
                throw new UnknownHostException("Địa chỉ IP không được để trống.");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Số cổng (Port) phải nằm trong khoảng từ 1 đến 65535.");
            }

            String trimmedIp = ip.trim();
            newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(trimmedIp, port), Math.max(timeoutMs, 1000));
            newSocket.setKeepAlive(true);
            newSocket.setTcpNoDelay(true);

            DataInputStream in = new DataInputStream(newSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(newSocket.getOutputStream());

            synchronized (this) {
                if (expectedGen != connectGeneration.get() || manualDisconnect) {
                    closeSocketQuietly(newSocket);
                    connecting.set(false);
                    return false;
                }
                this.socket = newSocket;
                this.inputStream = in;
                this.outputStream = out;

                this.lastIp = trimmedIp;
                this.lastPort = port;
                this.lastTimeoutMs = timeoutMs;

                connected.set(true);
                connecting.set(false);
                networkLost.set(false);
            }

            notifyConnected();

            synchronized (this) {
                if (expectedGen != connectGeneration.get() || !connected.get()) {
                    return false;
                }
                serverListener = new ServerListener(in, this);
                listenerThread = new Thread(serverListener, "Server-Packet-Listener");
                listenerThread.setDaemon(true);
                listenerThread.start();

                heartbeatService = new HeartbeatService(heartbeatIntervalMs, heartbeatMaxMissed,
                        this::sendPing, this::onHeartbeatTimeout);
                heartbeatService.start();
            }
            return true;

        } catch (IllegalArgumentException e) {
            handleConnectFailure(newSocket, reportErrors, e.getMessage(), e);
        } catch (UnknownHostException e) {
            handleConnectFailure(newSocket, reportErrors,
                    "Không tìm thấy địa chỉ máy chủ '" + ip + "'. Vui lòng kiểm tra lại IP/Tên miền.", e);
        } catch (SocketTimeoutException e) {
            handleConnectFailure(newSocket, reportErrors,
                    "Hết thời gian chờ kết nối (" + timeoutMs + "ms) tới " + ip + ":" + port + ". Kiểm tra lại IP hoặc tường lửa.", e);
        } catch (ConnectException e) {
            handleConnectFailure(newSocket, reportErrors,
                    "Máy chủ " + ip + ":" + port + " từ chối kết nối (Server có thể chưa bật hoặc sai Port).", e);
        } catch (IOException e) {
            handleConnectFailure(newSocket, reportErrors,
                    "Lỗi mạng khi kết nối tới máy chủ: " + e.getMessage(), e);
        }
        return false;
    }

    private void handleConnectFailure(Socket failedSocket, boolean reportErrors, String message, Throwable cause) {
        connecting.set(false);
        closeSocketQuietly(failedSocket);
        if (reportErrors) {
            notifyError(message, cause);
        }
    }

    private static void closeSocketQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {}
        }
    }

    // =========================================================================
    // TỰ ĐỘNG KẾT NỐI LẠI (AUTO-RECONNECT)
    // =========================================================================

    public void configureReconnect(boolean enabled, long initialDelayMs, long maxDelayMs, int maxAttempts) {
        reconnectManager.configure(enabled, initialDelayMs, maxDelayMs, maxAttempts);
    }

    boolean reconnectNow() {
        if (manualDisconnect || lastIp == null) {
            return true;
        }
        if (isConnected()) {
            return true;
        }
        long gen = connectGeneration.get();
        boolean ok = establishConnection(lastIp, lastPort, lastTimeoutMs, false, gen);
        if (ok) {
            notifyReconnected();
            String user = this.currentUsername;
            String pass = this.lastPassword;
            if (pass != null && !GUEST_USER.equals(user)) {
                sendLogin(user, pass);
            }
        }
        return ok;
    }

    void startReconnectIfNeeded() {
        if (!manualDisconnect && lastIp != null && !isConnected()) {
            reconnectManager.start();
        }
    }

    // =========================================================================
    // CÁC HÀM GỌI MẠNG (SEND LOGIN, REGISTER, MESSAGE, REPLY, FORWARD, AVATAR)
    // =========================================================================

    /**
     * Gửi một đối tượng Packet bất kỳ tới Server trên Worker Thread.
     * Việc ghi socket luôn nằm ngoài JavaFX Thread và được đồng bộ bằng writeLock.
     *
     * @param packet Gói tin cần gửi
     */
    public void sendPacket(Packet packet) {
        if (packet == null) {
            return;
        }
        final DataOutputStream out = this.outputStream;
        if (!isConnected() || out == null) {
            notifyError("Chưa kết nối tới máy chủ. Không thể gửi dữ liệu.", null);
            return;
        }

        ioExecutor.submit(() -> {
            synchronized (writeLock) {
                try {
                    PacketFramer.writePacket(out, packet);
                    out.flush();
                } catch (IOException e) {
                    notifyError("Không thể gửi dữ liệu tới máy chủ: " + e.getMessage(), e);
                    handleConnectionClosed(true);
                } catch (RuntimeException e) {
                    notifyError("Gói tin không hợp lệ, không thể gửi: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Gửi gói tin bắt tay thiết lập phiên (CONNECT_REQUEST).
     *
     * @return requestId của gói tin
     */
    public String sendConnectRequest() {
        Packet packet = new Packet(PacketType.CONNECT_REQUEST, currentUsername, SERVER_ID)
                .addPayload("clientVersion", "1.0-SNAPSHOT")
                .addPayload("os", System.getProperty("os.name", "Unknown"));
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi yêu cầu đăng nhập (LOGIN_REQUEST) tới Server.
     * Server kiểm tra với BCrypt hash và trả về LOGIN_RESPONSE qua ServerListener.
     *
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @return requestId của gói tin, hoặc null nếu đầu vào rỗng
     */
    public String sendLogin(String username, String password) {
        if (isBlank(username) || password == null || password.isEmpty()) {
            notifyError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.", null);
            return null;
        }
        currentUsername = username.trim();
        lastPassword = password;
        Packet packet = new Packet(PacketType.LOGIN_REQUEST, currentUsername, SERVER_ID)
                .addPayload("username", currentUsername)
                .addPayload("password", password);
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi yêu cầu đăng ký tài khoản mới (REGISTER_REQUEST) tới Server.
     *
     * @param username Tên đăng nhập mới
     * @param password Mật khẩu
     * @return requestId của gói tin, hoặc null nếu đầu vào không hợp lệ
     */
    public String sendRegister(String username, String password) {
        return sendRegister(username, password, null);
    }

    /**
     * Gửi yêu cầu đăng ký tài khoản mới kèm ảnh đại diện (REGISTER_REQUEST).
     *
     * @param username     Tên đăng nhập mới
     * @param password     Mật khẩu
     * @param avatarBase64 Ảnh đại diện mã hóa Base64 (có thể null để dùng mặc định)
     * @return requestId của gói tin, hoặc null nếu đầu vào không hợp lệ
     */
    public String sendRegister(String username, String password, String avatarBase64) {
        if (isBlank(username) || password == null || password.isEmpty()) {
            notifyError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu để đăng ký.", null);
            return null;
        }
        String trimmedUser = username.trim();
        Packet packet = new Packet(PacketType.REGISTER_REQUEST, trimmedUser, SERVER_ID)
                .addPayload("username", trimmedUser)
                .addPayload("password", password);
        if (!isBlank(avatarBase64)) {
            packet.addPayload("avatar", avatarBase64);
        }
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi yêu cầu đăng xuất (LOGOUT_REQUEST) và đặt lại username phiên về mặc định.
     */
    public void sendLogout() {
        if (isConnected()) {
            Packet packet = new Packet(PacketType.LOGOUT_REQUEST, currentUsername, SERVER_ID)
                    .addPayload("username", currentUsername);
            sendPacket(packet);
        }
        lastPassword = null;
        currentUsername = GUEST_USER;
    }

    /**
     * Gửi tin nhắn văn bản 1-1 (MESSAGE_SEND) tới người nhận.
     *
     * @param receiverId Username người nhận
     * @param content    Nội dung tin nhắn
     * @return requestId của gói tin (để đối chiếu với ACK từ Server), hoặc null nếu không hợp lệ
     */
    public String sendMessage(String receiverId, String content) {
        return sendMessage(receiverId, content, (List<String>) null);
    }

    /**
     * Gửi tin nhắn văn bản 1-1 kèm danh sách emoji (MESSAGE_SEND).
     *
     * @param receiverId Username người nhận
     * @param content    Nội dung tin nhắn
     * @param emojis     Danh sách emoji đính kèm (có thể null)
     * @return requestId của gói tin, hoặc null nếu không hợp lệ
     */
    public String sendMessage(String receiverId, String content, List<String> emojis) {
        if (isBlank(receiverId) || isBlank(content)) {
            return null;
        }
        Packet packet = new Packet(PacketType.MESSAGE_SEND, currentUsername, receiverId.trim())
                .addPayload("content", content);
        if (emojis != null && !emojis.isEmpty()) {
            packet.addPayload("emojis", emojis);
        }
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi đối tượng Message (hỗ trợ tin thường, Reply hoặc Forward từ model chung).
     *
     * @param receiverId Username người nhận
     * @param message    Đối tượng Message cần gửi
     * @return requestId của gói tin, hoặc null nếu không hợp lệ
     */
    public String sendMessage(String receiverId, Message message) {
        if (message == null || isBlank(receiverId) || isBlank(message.getContent())) {
            return null;
        }
        if (message.isForwarded()) {
            return sendForward(receiverId, message.getContent(), String.valueOf(message.getSenderId()),
                    message.getMsgId() != null ? message.getMsgId() : String.valueOf(message.getId()));
        }
        if (!isBlank(message.getReplyTo())) {
            return sendReply(receiverId, message.getContent(), message.getReplyTo(), null, null);
        }
        return sendMessage(receiverId, message.getContent());
    }

    /**
     * Gửi tin nhắn trả lời (REPLY_SEND) kèm thông tin trích dẫn.
     */
    public String sendReply(String receiverId, String content, long replyToMessageId,
                            String replyToSender, String replyToSnippet) {
        if (isBlank(receiverId) || isBlank(content)) {
            return null;
        }
        Packet packet = new Packet(PacketType.REPLY_SEND, currentUsername, receiverId.trim())
                .addPayload("content", content)
                .addPayload("replyToMessageId", replyToMessageId)
                .addPayload("replyToSender", replyToSender)
                .addPayload("replyToSnippet", replyToSnippet);
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi tin nhắn trả lời (REPLY_SEND) với ID chuỗi (UUID/msgId).
     */
    public String sendReply(String receiverId, String content, String replyToMessageId,
                            String replyToSender, String replyToSnippet) {
        if (isBlank(receiverId) || isBlank(content)) {
            return null;
        }
        Packet packet = new Packet(PacketType.REPLY_SEND, currentUsername, receiverId.trim())
                .addPayload("content", content)
                .addPayload("replyToMessageId", replyToMessageId)
                .addPayload("replyToSender", replyToSender)
                .addPayload("replyToSnippet", replyToSnippet);
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Chuyển tiếp tin nhắn (FORWARD_SEND) tới người nhận khác.
     */
    public String sendForward(String receiverId, String content, String originalSender, long originalMessageId) {
        if (isBlank(receiverId) || isBlank(content)) {
            return null;
        }
        Packet packet = new Packet(PacketType.FORWARD_SEND, currentUsername, receiverId.trim())
                .addPayload("content", content)
                .addPayload("originalSender", originalSender)
                .addPayload("originalMessageId", originalMessageId);
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Chuyển tiếp tin nhắn (FORWARD_SEND) với ID chuỗi (UUID/msgId).
     */
    public String sendForward(String receiverId, String content, String originalSender, String originalMessageId) {
        if (isBlank(receiverId) || isBlank(content)) {
            return null;
        }
        Packet packet = new Packet(PacketType.FORWARD_SEND, currentUsername, receiverId.trim())
                .addPayload("content", content)
                .addPayload("originalSender", originalSender)
                .addPayload("originalMessageId", originalMessageId);
        sendPacket(packet);
        return packet.getRequestId();
    }

    /**
     * Gửi ảnh đại diện mới dạng Base64 (mặc định PNG).
     */
    public String sendAvatar(String avatarBase64) {
        return sendAvatar(avatarBase64, "png");
    }

    /**
     * Gửi ảnh đại diện mới dạng Base64 (AVATAR_UPDATE) qua Socket.
     *
     * @param avatarBase64 Chuỗi Base64 của ảnh (tạo bởi AvatarHelper của TV5)
     * @param format       Định dạng ảnh ("png", "jpg"...)
     * @return requestId của gói tin, hoặc null nếu dữ liệu không hợp lệ / vượt 5MB
     */
    public String sendAvatar(String avatarBase64, String format) {
        if (isBlank(avatarBase64)) {
            notifyError("Dữ liệu ảnh đại diện không hợp lệ.", null);
            return null;
        }
        if (avatarBase64.length() > MAX_AVATAR_BASE64_LENGTH) {
            notifyError("Ảnh đại diện quá lớn (tối đa 5MB). Vui lòng chọn ảnh nhỏ hơn.", null);
            return null;
        }
        Packet packet = new Packet(PacketType.AVATAR_UPDATE, currentUsername, SERVER_ID)
                .addPayload("avatarBase64", avatarBase64)
                .addPayload("format", isBlank(format) ? "png" : format.trim().toLowerCase());
        sendPacket(packet);
        return packet.getRequestId();
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
        Packet echoPacket = Packet.createEchoRequest(currentUsername, message);
        sendPacket(echoPacket);
    }

    /**
     * Gửi gói PING giữ kết nối (được HeartbeatService gọi định kỳ mỗi 15 giây).
     */
    public void sendPing() {
        if (isConnected()) {
            sendPacket(new Packet(PacketType.PING, currentUsername, SERVER_ID));
        }
    }

    /**
     * Phản hồi gói PING từ Server bằng gói PONG (kèm requestId gốc).
     *
     * @param requestId ID của gói PING nhận được từ Server
     */
    void sendPong(String requestId) {
        Packet pong = new Packet(PacketType.PONG, currentUsername, SERVER_ID);
        if (requestId != null) {
            pong.setRequestId(requestId);
        }
        sendPacket(pong);
    }

    void onPongReceived(Packet pongPacket) {
        networkLost.set(false);
        HeartbeatService hb = this.heartbeatService;
        if (hb != null) {
            hb.onPongReceived();
        }
    }

    void onServerActivity() {
        networkLost.set(false);
        HeartbeatService hb = this.heartbeatService;
        if (hb != null) {
            hb.onServerActivity();
        }
    }

    void onHeartbeatTimeout() {
        if (!connected.get()) {
            return;
        }
        networkLost.set(true);
        notifyError("Mất kết nối tới máy chủ (quá " + heartbeatMaxMissed + " chu kỳ Heartbeat không nhận được phản hồi PONG).", null);
        notifyConnectionLost();
        handleConnectionClosed(true);
    }

    void onListenerStopped(ServerListener stoppedListener) {
        closeConnectionIf(stoppedListener, true);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // =========================================================================
    // NGẮT KẾT NỐI & DỌN DẸP TÀI NGUYÊN
    // =========================================================================

    public void disconnect() {
        manualDisconnect = true;
        connectGeneration.incrementAndGet();
        lastPassword = null;
        connecting.set(false);
        reconnectManager.cancel();
        ioExecutor.submit(() -> handleConnectionClosed(false));
    }

    void handleConnectionClosed(boolean allowReconnect) {
        closeConnectionIf(null, allowReconnect);
    }

    private void closeConnectionIf(ServerListener expectedListener, boolean allowReconnect) {
        boolean wasConnected;
        synchronized (this) {
            if (expectedListener != null && expectedListener != this.serverListener) {
                return;
            }
            wasConnected = connected.getAndSet(false);

            if (heartbeatService != null) {
                heartbeatService.stop();
                heartbeatService = null;
            }

            if (serverListener != null) {
                serverListener.stop();
                serverListener = null;
            }

            if (listenerThread != null && listenerThread != Thread.currentThread()) {
                listenerThread.interrupt();
                listenerThread = null;
            }

            closeResourcesSilently();
        }

        if (wasConnected) {
            notifyDisconnected();
            if (allowReconnect) {
                startReconnectIfNeeded();
            }
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

    public void shutdown() {
        manualDisconnect = true;
        connectGeneration.incrementAndGet();
        lastPassword = null;
        connecting.set(false);
        reconnectManager.shutdown();
        handleConnectionClosed(false);
        ioExecutor.shutdownNow();
    }

    // =========================================================================
    // ĐIỀU PHỐI SỰ KIỆN TỚI CÁC LISTENER
    // =========================================================================

    private void dispatchEvent(Consumer<NetworkEventListener> dispatcher) {
        for (NetworkEventListener listener : listeners) {
            try {
                dispatcher.accept(listener);
            } catch (RuntimeException e) {
                System.err.println("[NetworkService] Listener " + listener.getClass().getSimpleName()
                        + " ném ngoại lệ: " + e.getMessage());
            }
        }
    }

    void notifyConnecting(String ip, int port) {
        dispatchEvent(l -> l.onConnecting(ip, port));
    }

    void notifyConnected() {
        dispatchEvent(NetworkEventListener::onConnected);
    }

    void notifyDisconnected() {
        dispatchEvent(NetworkEventListener::onDisconnected);
    }

    void notifyConnectionLost() {
        dispatchEvent(NetworkEventListener::onConnectionLost);
    }

    void notifyReconnecting(int attempt, long delayMs) {
        dispatchEvent(l -> l.onReconnecting(attempt, delayMs));
    }

    void notifyReconnected() {
        dispatchEvent(NetworkEventListener::onReconnected);
    }

    void notifyReconnectGaveUp() {
        dispatchEvent(NetworkEventListener::onReconnectGaveUp);
    }

    void notifyPacketReceived(Packet packet) {
        dispatchEvent(l -> l.onPacketReceived(packet));
    }

    void notifyError(String friendlyMessage, Throwable exception) {
        dispatchEvent(l -> l.onError(friendlyMessage, exception));
    }

    // =========================================================================
    // GETTERS TRẠNG THÁI & CẤU HÌNH MẶC ĐỊNH
    // =========================================================================

    public boolean isConnected() {
        Socket s = this.socket;
        return connected.get() && s != null && s.isConnected() && !s.isClosed();
    }

    public boolean isConnecting() {
        return connecting.get();
    }

    public boolean isNetworkLost() {
        return networkLost.get();
    }

    public boolean isReconnecting() {
        return reconnectManager.isActive();
    }

    public HeartbeatService getHeartbeatService() {
        return heartbeatService;
    }

    public long getLastRttMs() {
        HeartbeatService hb = this.heartbeatService;
        return hb != null ? hb.getLastRttMs() : -1L;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = isBlank(username) ? GUEST_USER : username.trim();
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
