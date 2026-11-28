package com.chat.client;

import com.chat.client.component.AvatarHelper;
import com.chat.client.component.AvatarView;
import com.chat.client.component.DisappearingMessageTimer;
import com.chat.client.component.EmojiPickerPopup;
import com.chat.client.component.ForwardDialog;
import com.chat.client.component.MessageCellFactory;
import com.chat.client.component.ReplyPreviewBar;
import com.chat.client.component.SoundManager;
import com.chat.client.net.NetworkEventListener;
import com.chat.client.net.NetworkService;
import com.chat.common.model.Message;
import com.chat.common.protocol.Packet;
import com.chat.common.protocol.PacketType;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainChatController implements NetworkEventListener {
    private static final String ROOM = "ALL";
    private static final String ROOM_LABEL = "🌐 Phòng chat chung";

    @FXML private StackPane ownAvatarSlot;
    @FXML private Label currentUserLabel;
    @FXML private Button changeAvatarButton;
    @FXML private Label conversationTitle;
    @FXML private Label ephemeralCountLabel;
    @FXML private Button muteButton;
    @FXML private ListView<ChatUser> userListView;
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;
    @FXML private VBox replyHolder;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private Button emojiButton;
    @FXML private ComboBox<String> ttlComboBox;
    @FXML private Circle statusCircle;
    @FXML private Label statusLabel;
    @FXML private Button reconnectButton;

    private final NetworkService service = NetworkService.getInstance();
    private final NetworkEventListener fxListener = NetworkEventListener.onFxThread(this);
    private final ObservableList<ChatUser> contacts = FXCollections.observableArrayList();
    private final Map<String, ChatUser> usersByName = new LinkedHashMap<>();
    private final Map<String, List<ChatEntry>> conversations = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private final Map<String, HBox> rowsByMessageId = new HashMap<>();
    private final AvatarView ownAvatar = new AvatarView();
    private final ReplyPreviewBar replyPreviewBar = new ReplyPreviewBar();
    private final EmojiPickerPopup emojiPicker = new EmojiPickerPopup();
    private final Timeline activeMessageTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshEphemeralCount()));
    private String currentUsername = NetworkService.GUEST_USER;
    private String currentAvatar;
    private String activePeer = ROOM;
    private String replyToId;
    private String replyToSender;
    private String replyToSnippet;
    private Timeline backoffCountdownTimeline;
    private long countdownRemainingSec;
    private int reconnectAttempt;
    private boolean reconnectingCycle;

    @FXML public void initialize() {
        ownAvatar.setSize(40);
        ownAvatarSlot.getChildren().setAll(ownAvatar);
        replyHolder.getChildren().setAll(replyPreviewBar);
        service.addEventListener(fxListener);
        userListView.setItems(contacts);
        userListView.setCellFactory(view -> new ContactCell());
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) openConversation(selected);
        });
        sendButton.setOnAction(event -> handleSendMessage());
        messageField.setOnAction(event -> handleSendMessage());
        emojiButton.setOnAction(event -> emojiPicker.show(emojiButton, messageField));
        muteButton.setOnAction(event -> {
            SoundManager sound = SoundManager.getInstance();
            sound.setMuted(!sound.isMuted());
            updateMuteButton();
        });
        changeAvatarButton.setOnAction(event -> chooseAvatar());
        reconnectButton.setOnAction(event -> handleManualReconnect());
        ttlComboBox.setItems(FXCollections.observableArrayList(
                "⏳ Không tự hủy", "🔥 Tự hủy 5s", "⏳ Tự hủy 10s", "⏳ Tự hủy 30s"));
        ttlComboBox.getSelectionModel().selectFirst();
        activeMessageTimeline.setCycleCount(Timeline.INDEFINITE);
        activeMessageTimeline.play();
        updateMuteButton();
        syncInitialConnectionState();
        contacts.setAll(new ChatUser(ROOM, ROOM_LABEL, true, null, true));
        userListView.getSelectionModel().selectFirst();
    }

    public void setSession(String username, String avatar, List<Map<String, Object>> users) {
        currentUsername = username == null ? NetworkService.GUEST_USER : username;
        currentAvatar = avatar;
        service.setCurrentUsername(currentUsername);
        currentUserLabel.setText(currentUsername);
        ownAvatar.setName(currentUsername);
        ownAvatar.setImage(AvatarHelper.base64ToImage(avatar));
        updateUsers(users);
        userListView.getSelectionModel().selectFirst();
    }

    private void updateUsers(List<Map<String, Object>> users) {
        usersByName.clear();
        if (users != null) for (Map<String, Object> data : users) addOrUpdateUser(data);
        rebuildContacts();
    }

    private void addOrUpdateUser(Map<String, Object> data) {
        String username = text(data.get("username"));
        if (username == null || username.equalsIgnoreCase(currentUsername)) return;
        boolean online = Boolean.TRUE.equals(data.get("online")) || "ONLINE".equalsIgnoreCase(text(data.get("status")));
        String avatar = text(data.get("avatar"));
        ChatUser existing = usersByName.get(username.toLowerCase());
        if (existing != null && avatar == null) avatar = existing.avatar;
        usersByName.put(username.toLowerCase(), new ChatUser(username, username, online, avatar, false));
    }

    private void rebuildContacts() {
        List<ChatUser> list = new ArrayList<>();
        list.add(new ChatUser(ROOM, ROOM_LABEL, true, null, true));
        usersByName.values().stream().sorted((a, b) -> a.username.compareToIgnoreCase(b.username)).forEach(list::add);
        contacts.setAll(list);
        ChatUser selected = list.stream().filter(user -> user.room ? ROOM.equals(activePeer)
                : user.username.equalsIgnoreCase(activePeer)).findFirst().orElse(list.get(0));
        userListView.getSelectionModel().select(selected);
    }

    private void openConversation(ChatUser user) {
        activePeer = user.room ? ROOM : user.username;
        replyPreviewBar.clear();
        replyToId = replyToSender = replyToSnippet = null;
        conversationTitle.setText(user.room ? "🌐 Phòng chat chung (Tất cả mọi người)"
                : "💬 Đang trò chuyện với: " + user.username + (user.online ? " (Online)" : " (Offline)"));
        unreadCounts.remove(user.username.toLowerCase());
        userListView.refresh();
        displayConversation(activePeer);
        if (service.isConnected()) service.sendHistoryRequest(user.room ? ROOM : user.username);
    }

    private void displayConversation(String peer) {
        messageContainer.getChildren().clear();
        rowsByMessageId.clear();
        for (ChatEntry entry : conversations.getOrDefault(conversationKey(peer), List.of())) renderEntry(entry);
        scrollToBottom();
    }

    private String conversationKey(String peer) {
        if (ROOM.equalsIgnoreCase(peer)) return ROOM;
        return currentUsername.compareToIgnoreCase(peer) <= 0
                ? currentUsername.toLowerCase() + "|" + peer.toLowerCase()
                : peer.toLowerCase() + "|" + currentUsername.toLowerCase();
    }

    private void handleSendMessage() {
        String content = messageField.getText() == null ? "" : messageField.getText().trim();
        if (content.isEmpty() || !service.isConnected()) return;
        long ttl = selectedTtl();
        String id;
        if (replyToId != null) {
            id = service.sendReply(activePeer, content, replyToId, replyToSender, replyToSnippet, ttl);
        } else {
            id = service.sendMessage(activePeer, content, ttl);
        }
        if (id == null) return;
        ChatEntry entry = new ChatEntry(newMessage(id, currentUsername, content, ttl, replyToId,
                replyToSender, replyToSnippet, null, null, false), currentUsername, currentAvatar, activePeer, ttl);
        addEntry(entry, true);
        if (ttl > 0) attachTimer(entry);
        messageField.clear();
        replyPreviewBar.clear();
        replyToId = replyToSender = replyToSnippet = null;
        updateStatus("GREEN", "Đã gửi tới " + activePeer);
    }

    private long selectedTtl() {
        String selection = ttlComboBox.getValue();
        if (selection == null) return 0;
        if (selection.contains("5s")) return 5;
        if (selection.contains("10s")) return 10;
        if (selection.contains("30s")) return 30;
        return 0;
    }

    private Message newMessage(String id, String sender, String content, long ttl, String replyId,
                               String replySender, String replySnippet, String originalSender,
                               String originalId, boolean forwarded) {
        Message message = new Message();
        message.setMsgId(id);
        message.setSenderUsername(sender);
        message.setContent(content);
        message.setTimestamp(System.currentTimeMillis());
        message.setReplyTo(replyId);
        message.setReplyToSender(replySender);
        message.setReplyToSnippet(replySnippet);
        message.setOriginalSender(originalSender);
        message.setOriginalMessageId(originalId);
        message.setForwarded(forwarded);
        return message;
    }

    private void addEntry(ChatEntry entry, boolean own) {
        String key = conversationKey(entry.peer);
        conversations.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        if (entry.peer.equalsIgnoreCase(activePeer)) renderEntry(entry);
        else if (!own && !entry.sender.equalsIgnoreCase(currentUsername)) {
            unreadCounts.merge(entry.sender.toLowerCase(), 1, Integer::sum);
            userListView.refresh();
        }
        if (entry.peer.equalsIgnoreCase(activePeer)) scrollToBottom();
    }

    private void renderEntry(ChatEntry entry) {
        boolean own = entry.sender.equalsIgnoreCase(currentUsername);
        ChatUser sender = usersByName.get(entry.sender.toLowerCase());
        String senderName = own ? currentUsername : entry.sender;
        Image avatar = AvatarHelper.base64ToImage(own ? currentAvatar : entry.avatar);
        String replySnippet = entry.message.getReplyToSnippet();
        LocalDateTime sentAt = entry.message.getTimestamp() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.message.getTimestamp()), ZoneId.systemDefault()) : LocalDateTime.now();
        HBox row = MessageCellFactory.createMessageRow(entry.message, own,
                (action, message) -> handleMessageAction(action, message), senderName,
                replySnippet, entry.message.getOriginalSender(), sentAt, avatar);
        if (entry.message.getMsgId() != null) rowsByMessageId.put(entry.message.getMsgId(), row);
        messageContainer.getChildren().add(row);
        if (entry.ttlSeconds > 0) attachTimer(entry, row);
    }

    private void attachTimer(ChatEntry entry) { HBox row = rowsByMessageId.get(entry.message.getMsgId()); if (row != null) attachTimer(entry, row); }

    private void attachTimer(ChatEntry entry, HBox row) {
        if (DisappearingMessageTimer.getInstance().isScheduled(entry.message.getMsgId())) return;
        DisappearingMessageTimer.getInstance().attachCountdownBadge(row, entry.message.getMsgId(), entry.ttlSeconds,
                messageContainer, () -> {
                    conversations.values().forEach(items -> items.removeIf(item -> entry.message.getMsgId().equals(item.message.getMsgId())));
                    rowsByMessageId.remove(entry.message.getMsgId());
                    service.sendDeleteEphemeral(entry.message.getMsgId());
                    refreshEphemeralCount();
                });
        refreshEphemeralCount();
    }

    private void handleMessageAction(String action, Message message) {
        if ("REPLY".equals(action)) {
            replyToId = message.getMsgId();
            replyToSender = message.getSenderUsername() == null ? currentUsername : message.getSenderUsername();
            replyToSnippet = message.getContent();
            replyPreviewBar.showReply(replyToId, replyToSender, replyToSnippet);
            messageField.requestFocus();
        } else if ("FORWARD".equals(action)) {
            List<ForwardDialog.Friend> friends = new ArrayList<>();
            friends.add(new ForwardDialog.Friend(ROOM, ROOM_LABEL, true, null));
            for (ChatUser user : usersByName.values()) friends.add(new ForwardDialog.Friend(user.username,
                    user.username, user.online, AvatarHelper.base64ToImage(user.avatar)));
            new ForwardDialog(messageField.getScene().getWindow(), message.getContent(), friends)
                    .showAndWait().ifPresent(receiver -> {
                        String id = service.sendForward(receiver, message.getContent(),
                                message.getSenderUsername(), message.getMsgId(), selectedTtl());
                        if (id != null && receiver.equalsIgnoreCase(activePeer)) {
                            ChatEntry forwarded = new ChatEntry(newMessage(id, currentUsername, message.getContent(),
                                    selectedTtl(), null, null, null, message.getSenderUsername(), message.getMsgId(), true),
                                    currentUsername, currentAvatar, receiver, selectedTtl());
                            addEntry(forwarded, true);
                        }
                    });
        }
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh Avatar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh PNG/JPG", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(changeAvatarButton.getScene().getWindow());
        if (file == null) return;
        try {
            String encoded = AvatarHelper.imageToBase64(file, 128, 128);
            currentAvatar = encoded;
            ownAvatar.setImage(AvatarHelper.base64ToImage(encoded));
            service.sendAvatar(encoded);
        } catch (Exception exception) {
            updateStatus("RED", "Không thể đổi avatar: " + exception.getMessage());
        }
    }

    private void refreshEphemeralCount() {
        if (ephemeralCountLabel != null) {
            int count = DisappearingMessageTimer.getInstance().getActiveCount();
            ephemeralCountLabel.setText(count > 0 ? "⏳ " + count : "");
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
    }

    @Override public void onConnecting(String ip, int port) {
        runFx(() -> { setMessagingEnabled(false); updateStatus("YELLOW", "Đang kết nối tới " + ip + ":" + port + "..."); });
    }
    @Override public void onConnected() { runFx(() -> { setMessagingEnabled(true); if (!reconnectingCycle) updateStatus("GREEN", "Đã kết nối tới máy chủ"); }); }
    @Override public void onConnectionLost() { runFx(() -> { reconnectingCycle = true; setMessagingEnabled(false); updateStatus("RED", "Mất kết nối. Đang tự động kết nối lại..."); }); }
    @Override public void onDisconnected() { runFx(() -> { if (!service.isNetworkLost()) { setMessagingEnabled(false); updateStatus("GRAY", "Đã ngắt kết nối"); } }); }
    @Override public void onReconnecting(int attempt, long delayMs) {
        runFx(() -> {
            reconnectAttempt = attempt;
            if (backoffCountdownTimeline != null) backoffCountdownTimeline.stop();
            if (delayMs <= 0) { updateStatus("YELLOW", "Đang thử kết nối lại ngay (lần " + attempt + ")..."); return; }
            countdownRemainingSec = Math.max(1, Math.round(delayMs / 1000.0));
            updateStatus("RED", "Mất kết nối. Thử lại sau " + countdownRemainingSec + "s");
            backoffCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                countdownRemainingSec = Math.max(0, countdownRemainingSec - 1);
                updateStatus("RED", "Mất kết nối. Thử lại sau " + countdownRemainingSec + "s");
            }));
            backoffCountdownTimeline.setCycleCount((int) Math.max(1, countdownRemainingSec));
            backoffCountdownTimeline.playFromStart();
        });
    }
    @Override public void onReconnected() { runFx(() -> { reconnectingCycle = false; if (backoffCountdownTimeline != null) backoffCountdownTimeline.stop(); setMessagingEnabled(true); updateStatus("GREEN", "Đã khôi phục kết nối"); }); }
    @Override public void onReconnectGaveUp() { runFx(() -> updateStatus("RED", "Không thể kết nối lại; hãy thử kết nối thủ công.")); }

    @Override public void onPacketReceived(Packet packet) {
        runFx(() -> {
            switch (packet.getType()) {
                case LOGIN_RESPONSE -> handleLoginResponse(packet);
                case USER_STATUS_CHANGE -> handleUserStatus(packet);
                case AVATAR_UPDATE -> handleAvatarUpdate(packet);
                case MESSAGE_RECEIVE, REPLY_RECEIVE, FORWARD_RECEIVE -> handleIncomingMessage(packet);
                case HISTORY_RESPONSE -> handleHistory(packet);
                case CHAT_ACK -> {
                    Object status = packet.getPayloadValue("status");
                    if ("FAILED".equals(status)) updateStatus("RED", text(packet.getPayloadValue("message")));
                }
                case ERROR_RESPONSE -> updateStatus("RED", text(packet.getPayloadValue("message")));
                default -> { }
            }
        });
    }

    private void handleLoginResponse(Packet packet) {
        if (!Boolean.TRUE.equals(packet.getPayloadValue("success"))) return;
        String avatar = text(packet.getPayloadValue("avatar"));
        if (avatar != null) {
            currentAvatar = avatar;
            ownAvatar.setImage(AvatarHelper.base64ToImage(avatar));
        }
        Object rawUsers = packet.getPayloadValue("users");
        if (rawUsers instanceof List<?> list) {
            List<Map<String, Object>> data = new ArrayList<>();
            for (Object item : list) if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new HashMap<>();
                map.forEach((key, value) -> row.put(String.valueOf(key), value));
                data.add(row);
            }
            updateUsers(data);
        }
    }

    private void handleUserStatus(Packet packet) {
        Map<String, Object> data = packet.getPayloadMap();
        addOrUpdateUser(data);
        rebuildContacts();
    }

    private void handleAvatarUpdate(Packet packet) {
        String username = text(packet.getPayloadValue("username"));
        String avatar = text(packet.getPayloadValue("avatarBase64"));
        if (username == null) return;
        if (username.equalsIgnoreCase(currentUsername)) {
            currentAvatar = avatar;
            ownAvatar.setImage(AvatarHelper.base64ToImage(avatar));
        } else {
            ChatUser existing = usersByName.get(username.toLowerCase());
            if (existing != null) usersByName.put(username.toLowerCase(), new ChatUser(existing.username,
                    existing.displayName, existing.online, avatar, false));
            rebuildContacts();
        }
    }

    private void handleIncomingMessage(Packet packet) {
        String sender = packet.getSender() == null ? "" : packet.getSender();
        String receiver = packet.getReceiver() == null ? currentUsername : packet.getReceiver();
        String content = text(packet.getPayloadValue("content"));
        String id = text(packet.getPayloadValue("msgId"));
        if (id == null) id = text(packet.getPayloadValue("messageId"));
        if (id == null) id = packet.getRequestId();
        long ttl = number(packet.getPayloadValue("ttlSeconds"), 0);
        String peer = ROOM.equalsIgnoreCase(receiver) ? ROOM
                : sender.equalsIgnoreCase(currentUsername) ? receiver : sender;
        String replyId = text(packet.getPayloadValue("replyToMessageId"));
        Message message = newMessage(id, sender, content, ttl, replyId,
                text(packet.getPayloadValue("replyToSender")), text(packet.getPayloadValue("replyToSnippet")),
                text(packet.getPayloadValue("originalSender")), text(packet.getPayloadValue("originalMessageId")),
                packet.getType() == PacketType.FORWARD_RECEIVE);
        message.setTimestamp(number(packet.getPayloadValue("timestamp"), packet.getTimestamp()));
        ChatUser user = usersByName.get(sender.toLowerCase());
        ChatEntry entry = new ChatEntry(message, sender, user == null ? null : user.avatar, peer, ttl);
        addEntry(entry, sender.equalsIgnoreCase(currentUsername));
        if (!sender.equalsIgnoreCase(currentUsername)) SoundManager.getInstance().playNotificationSound();
        if (ttl > 0) attachTimer(entry);
    }

    private void handleHistory(Packet packet) {
        String peer = text(packet.getPayloadValue("partnerUsername"));
        if (peer == null || !peer.equalsIgnoreCase(activePeer)) return;
        String key = conversationKey(peer);
        conversations.put(key, new ArrayList<>());
        messageContainer.getChildren().clear();
        rowsByMessageId.clear();
        Object raw = packet.getPayloadValue("messages");
        if (raw instanceof List<?> messages) for (Object item : messages) if (item instanceof Map<?, ?> map) {
            String sender = text(map.get("sender"));
            String id = text(map.get("msgId"));
            Message message = newMessage(id, sender, text(map.get("content")), 0,
                    text(map.get("replyToMessageId")), text(map.get("replyToSender")),
                    text(map.get("replyToSnippet")), text(map.get("originalSender")),
                    text(map.get("originalMessageId")), Boolean.TRUE.equals(map.get("forwarded")));
            message.setTimestamp(number(map.get("timestamp"), 0));
            ChatUser user = usersByName.get(sender == null ? "" : sender.toLowerCase());
            ChatEntry entry = new ChatEntry(message, sender, user == null ? null : user.avatar, peer, 0);
            conversations.get(key).add(entry);
            renderEntry(entry);
        }
        scrollToBottom();
    }

    private void chooseManualReconnect() { }
    private void handleManualReconnect() {
        if (service.retryReconnectNow() && !service.isConnected()) {
            reconnectButton.setDisable(true);
            updateStatus("YELLOW", "Đang thử kết nối lại ngay...");
        }
    }

    private void syncInitialConnectionState() {
        if (service.isConnected()) { setMessagingEnabled(true); updateStatus("GREEN", "Đã kết nối tới Server"); }
        else { setMessagingEnabled(false); updateStatus("GRAY", "Chưa kết nối tới Server"); }
    }

    private void setMessagingEnabled(boolean enabled) {
        messageField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
        messageField.setPromptText(enabled ? "Nhập tin nhắn..." : "Đang mất kết nối...");
        reconnectButton.setVisible(!enabled);
        reconnectButton.setManaged(!enabled);
        reconnectButton.setDisable(false);
    }

    private void updateStatus(String color, String message) {
        statusCircle.setFill(switch (color) {
            case "GREEN" -> Color.web("#22C55E");
            case "YELLOW" -> Color.web("#EAB308");
            case "RED" -> Color.web("#EF4444");
            default -> Color.web("#94A3B8");
        });
        statusLabel.setText(message == null ? "" : message);
    }

    private void updateMuteButton() { muteButton.setText(SoundManager.getInstance().isMuted() ? "🔇" : "🔊"); }
    private void runFx(Runnable action) { if (Platform.isFxApplicationThread()) action.run(); else Platform.runLater(action); }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private long number(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value != null) try { return Long.parseLong(value.toString()); } catch (NumberFormatException ignored) { }
        return fallback;
    }

    @Override public void onError(String message, Throwable exception) {
        runFx(() -> { if (!service.isConnected()) setMessagingEnabled(false); updateStatus("RED", message); });
    }

    public void dispose() {
        service.removeEventListener(fxListener);
        activeMessageTimeline.stop();
        if (backoffCountdownTimeline != null) backoffCountdownTimeline.stop();
        emojiPicker.hide();
    }

    private final class ContactCell extends ListCell<ChatUser> {
        private final HBox row = new HBox(9);
        private final AvatarView avatar = new AvatarView();
        private final Label name = new Label();
        private final Circle dot = new Circle(4);
        private final Label unread = new Label();
        private final javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        private ContactCell() {
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            name.setStyle("-fx-font-size: 13px; -fx-text-fill: #0F172A;");
            unread.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-background-radius: 12; -fx-padding: 2 6;");
            row.getChildren().setAll(avatar, name, dot, spacer, unread);
        }
        @Override protected void updateItem(ChatUser user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) { setGraphic(null); return; }
            avatar.setSize(user.room ? 36 : 36);
            avatar.setName(user.username);
            avatar.setImage(AvatarHelper.base64ToImage(user.avatar));
            name.setText(user.displayName);
            dot.setFill(user.room ? Color.web("#22C55E") : user.online ? Color.web("#22C55E") : Color.web("#94A3B8"));
            int count = unreadCounts.getOrDefault(user.username.toLowerCase(), 0);
            unread.setText(count > 0 ? String.valueOf(count) : "");
            unread.setVisible(count > 0);
            unread.setManaged(count > 0);
            setGraphic(row);
        }
    }

    private static final class ChatUser {
        private final String username, displayName, avatar;
        private final boolean online, room;
        private ChatUser(String username, String displayName, boolean online, String avatar, boolean room) {
            this.username = username; this.displayName = displayName; this.online = online; this.avatar = avatar; this.room = room;
        }
    }

    private static final class ChatEntry {
        private final Message message;
        private final String sender, avatar, peer;
        private final long ttlSeconds;
        private ChatEntry(Message message, String sender, String avatar, String peer, long ttlSeconds) {
            this.message = message; this.sender = sender == null ? "" : sender; this.avatar = avatar; this.peer = peer; this.ttlSeconds = ttlSeconds;
        }
    }
}
