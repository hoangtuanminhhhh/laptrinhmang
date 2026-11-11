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

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    // Username của phiên đăng nhập hiện tại (dùng làm trường "sender" cho các gói tin gửi đi)
    private volatile String currentUsername = GUEST_USER;

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

        notifyConnecting(ip, port);

        ioExecutor.submit(() -> {
            if (isConnected()) {
                handleConnectionClosed();
            }
            Socket newSocket = null;
            try {
                if (isBlank(ip)) {
                    throw new UnknownHostException("Địa chỉ IP không được để trống.");
                }
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Số cổng (Port) phải nằm trong khoảng từ 1 đến 65535.");
                }

                newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(ip.trim(), port), Math.max(timeoutMs, 1000));
                newSocket.setKeepAlive(true);
                newSocket.setTcpNoDelay(true);

                DataInputStream in = new DataInputStream(newSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(newSocket.getOutputStream());

                synchronized (this) {
                    this.socket = newSocket;
                    this.inputStream = in;
                    this.outputStream = out;
                    connected.set(true);
                    connecting.set(false);
                }

                notifyConnected();

                synchronized (this) {
                    if (!connected.get()) {
                        return;
                    }
                    serverListener = new ServerListener(in, this);
                    listenerThread = new Thread(serverListener, "Server-Packet-Listener");
                    listenerThread.setDaemon(true);
                    listenerThread.start();
                }

            } catch (IllegalArgumentException e) {
                handleConnectFailure(newSocket, e.getMessage(), e);
            } catch (UnknownHostException e) {
                handleConnectFailure(newSocket, "Không tìm thấy địa chỉ máy chủ '" + ip + "'. Vui lòng kiểm tra lại IP/Tên miền.", e);
            } catch (SocketTimeoutException e) {
                handleConnectFailure(newSocket, "Hết thời gian chờ kết nối (" + timeoutMs + "ms) tới " + ip + ":" + port + ". Kiểm tra lại IP hoặc tường lửa.", e);
            } catch (ConnectException e) {
                handleConnectFailure(newSocket, "Máy chủ " + ip + ":" + port + " từ chối kết nối (Server có thể chưa bật hoặc sai Port).", e);
            } catch (IOException e) {
                handleConnectFailure(newSocket, "Lỗi mạng khi kết nối tới máy chủ: " + e.getMessage(), e);
            }
        });
    }

    private void handleConnectFailure(Socket failedSocket, String message, Throwable cause) {
        connecting.set(false);
        if (failedSocket != null) {
            try {
                failedSocket.close();
            } catch (IOException ignored) {}
        }
        closeResourcesSilently();
        notifyError(message, cause);
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
                    handleConnectionClosed();
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

    /**
     * Được ServerListener gọi khi luồng đọc kết thúc.
     * Chỉ dọn dẹp kết nối nếu listener vừa dừng vẫn là listener hiện hành (tránh race condition khi nối lại).
     */
    synchronized void onListenerStopped(ServerListener stoppedListener) {
        if (stoppedListener == this.serverListener) {
            handleConnectionClosed();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
        Socket s = this.socket;
        return connected.get() && s != null && s.isConnected() && !s.isClosed();
    }

    public boolean isConnecting() {
        return connecting.get();
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
