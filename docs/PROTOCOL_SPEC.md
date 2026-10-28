# ĐẶC TẢ GIAO THỨC TRUYỀN THÔNG MẠNG (PROTOCOL SPECIFICATION)
## Dự án: UDM_08 — Chat TCP Client–Server
**Phiên bản:** 1.0 (Phase 1) | **Tác giả phụ trách:** Thành viên 6 (QA & Architecture Documentation)

---

## 1. TỔNG QUAN VÀ NGUYÊN LÝ THIẾT KẾ

### 1.1. Phạm vi áp dụng
Tài liệu này quy định cấu trúc gói tin, phương thức đóng khung nhị phân (Framing) và chuẩn giao tiếp JSON qua socket TCP giữa **Chat Client** và **Chat Server**. Mọi thành viên trong nhóm (đặc biệt là TV1 - Server Core, TV2 - Model & Config, TV3 - Client Network, TV4 - UI) phải tuân thủ nghiêm ngặt đặc tả này để đảm bảo tính tương thích và không phát sinh lỗi đồng bộ.

### 1.2. Giải pháp chống Dính gói (Sticky Packets) và Xé gói (Fragmented Packets)
Giao thức tầng giao vận TCP là hướng dòng byte (byte-stream), không có khái niệm "ranh giới tin nhắn" (message boundary). Do đó, một lần đọc `InputStream.read()` có thể nhận:
- Chỉ một phần của gói tin (Xé gói / Phân mảnh).
- Nhiều gói tin liên tiếp dính liền nhau (Dính gói).

**Giải pháp:** Áp dụng kỹ thuật **Length-Prefixed Framing**:
- Mỗi gói tin bắt đầu bằng đúng **4 byte nguyên có dấu (32-bit signed integer)** theo thứ tự byte chuẩn mạng **Big-Endian**, biểu thị số byte của phần dữ liệu JSON Payload đi kèm.
- Kế tiếp 4 byte header là đúng $N$ byte nhị phân được mã hóa theo chuẩn **UTF-8** của chuỗi JSON.
- Phía đọc dữ liệu **bắt buộc** phải sử dụng phương thức `DataInputStream.readFully(byte[] b, int off, int len)` để bảo đảm đọc đủ $N$ byte trước khi tiến hành parse JSON.

---

## 2. CẤU TRÚC GÓI TIN NHỊ PHÂN (BYTE-LEVEL FRAMING)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               PAYLOAD LENGTH (4 Bytes, Big-Endian)            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                     JSON PAYLOAD (UTF-8)                      +
|                     Kích thước = LENGTH bytes                 |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Giới hạn kỹ thuật:
- **Kích thước tối thiểu của Payload:** $2$ bytes (chuỗi JSON rỗng `{}`).
- **Kích thước tối đa của Payload:** $10.485.760$ bytes ($10$ MB) nhằm ngăn chặn tấn công từ chối dịch vụ (DoS) hoặc tràn bộ nhớ Heap (`OutOfMemoryError`). Nếu `Length > 10MB` hoặc `Length < 0`, ngắt kết nối socket ngay lập tức.

---

## 3. KHUNG DỮ LIỆU JSON TỔNG QUÁT (ENVELOPE SCHEMA)

Mọi gói tin sau khi bóc tách 4-byte header đều có định dạng chuẩn JSON Envelope như sau:

```json
{
  "type": "ECHO_REQUEST",
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000",
  "sender": "hoangtuanminh",
  "receiver": "SERVER",
  "timestamp": 1714310000000,
  "payload": {
    "key": "value"
  }
}
```

### Ý nghĩa các trường chung:
| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|:---|:---|:---:|:---|
| `type` | String (Enum) | Có | Định danh loại gói tin (xem mục 4). |
| `requestId` | String | Không | UUID định danh yêu cầu, dùng để map request với response tương ứng. |
| `sender` | String | Có | Username của người gửi (hoặc `"SERVER"` nếu là tin từ máy chủ). |
| `receiver` | String | Có | Username người nhận, `"ALL"` nếu broadcast, hoặc `"SERVER"`. |
| `timestamp` | Long | Có | Thời gian tạo gói (Unix Epoch timestamp tính bằng milliseconds). |
| `payload` | Object | Có | Đối tượng JSON chứa tham số chi tiết của từng loại nghiệp vụ. |

---

## 4. CHI TIẾT CÁC LOẠI GÓI TIN (PACKET TYPES)

### 4.1. `ECHO_REQUEST` & `ECHO_RESPONSE` (Kiểm thử Phase 1)
Sử dụng trong Phase 1 để kiểm tra kết nối mạng, đo lường độ trễ (latency) và kiểm tra bộ đóng/mở gói.

#### Request (Client -> Server):
```json
{
  "type": "ECHO_REQUEST",
  "requestId": "echo-001",
  "sender": "client_alpha",
  "receiver": "SERVER",
  "timestamp": 1714310000100,
  "payload": {
    "message": "Xin chào Lập trình mạng! 🇻🇳 😊"
  }
}
```

#### Response (Server -> Client):
```json
{
  "type": "ECHO_RESPONSE",
  "requestId": "echo-001",
  "sender": "SERVER",
  "receiver": "client_alpha",
  "timestamp": 1714310000150,
  "payload": {
    "originalMessage": "Xin chào Lập trình mạng! 🇻🇳 😊",
    "receivedAt": 1714310000140,
    "status": "SUCCESS"
  }
}
```

---

### 4.2. `CONNECT_REQUEST` & `CONNECT_RESPONSE` (Thiết lập phiên)

#### Request (Client -> Server):
```json
{
  "type": "CONNECT_REQUEST",
  "requestId": "conn-001",
  "sender": "guest_user",
  "receiver": "SERVER",
  "timestamp": 1714310000200,
  "payload": {
    "clientVersion": "1.0-SNAPSHOT",
    "os": "Windows 11"
  }
}
```

#### Response (Server -> Client):
```json
{
  "type": "CONNECT_RESPONSE",
  "requestId": "conn-001",
  "sender": "SERVER",
  "receiver": "guest_user",
  "timestamp": 1714310000250,
  "payload": {
    "status": "ACCEPTED",
    "sessionId": "sess-8899aabbcc",
    "heartbeatInterval": 15000
  }
}
```

---

### 4.3. `MESSAGE_SEND` & `MESSAGE_RECEIVE` (Gửi tin nhắn thông thường)

#### Send (Client A -> Server):
```json
{
  "type": "MESSAGE_SEND",
  "requestId": "msg-101",
  "sender": "minh",
  "receiver": "tuan",
  "timestamp": 1714310000500,
  "payload": {
    "content": "Chiều nay có học Lập trình mạng không bạn ơi?",
    "emojis": ["😊", "📚"]
  }
}
```

#### Receive (Server -> Client B `tuan`):
```json
{
  "type": "MESSAGE_RECEIVE",
  "requestId": "msg-101",
  "sender": "minh",
  "receiver": "tuan",
  "timestamp": 1714310000520,
  "payload": {
    "messageId": 9812,
    "content": "Chiều nay có học Lập trình mạng không bạn ơi?",
    "emojis": ["😊", "📚"],
    "avatar": "default_avatar.png"
  }
}
```

---

### 4.4. `REPLY_SEND` & `REPLY_RECEIVE` (Trả lời tin nhắn cụ thể)

#### Send (Client B -> Server):
```json
{
  "type": "REPLY_SEND",
  "requestId": "rep-201",
  "sender": "tuan",
  "receiver": "minh",
  "timestamp": 1714310000800,
  "payload": {
    "replyToMessageId": 9812,
    "replyToSender": "minh",
    "replyToSnippet": "Chiều nay có học Lập trình mạng không bạn ơi?",
    "content": "Có bạn nhé, 13h30 tại phòng C301!"
  }
}
```

#### Receive (Server -> Client A `minh`):
```json
{
  "type": "REPLY_RECEIVE",
  "requestId": "rep-201",
  "sender": "tuan",
  "receiver": "minh",
  "timestamp": 1714310000820,
  "payload": {
    "messageId": 9813,
    "replyToMessageId": 9812,
    "replyToSender": "minh",
    "replyToSnippet": "Chiều nay có học Lập trình mạng không bạn ơi?",
    "content": "Có bạn nhé, 13h30 tại phòng C301!",
    "avatar": "default_avatar.png"
  }
}
```

---

### 4.5. `FORWARD_SEND` & `FORWARD_RECEIVE` (Chuyển tiếp tin nhắn)

#### Send (Client A -> Server chuyển tiếp cho Client C `anh`):
```json
{
  "type": "FORWARD_SEND",
  "requestId": "fwd-301",
  "sender": "minh",
  "receiver": "anh",
  "timestamp": 1714310001000,
  "payload": {
    "originalSender": "tuan",
    "originalMessageId": 9813,
    "content": "Có bạn nhé, 13h30 tại phòng C301!"
  }
}
```

#### Receive (Server -> Client C `anh`):
```json
{
  "type": "FORWARD_RECEIVE",
  "requestId": "fwd-301",
  "sender": "minh",
  "receiver": "anh",
  "timestamp": 1714310001020,
  "payload": {
    "messageId": 9814,
    "forwardedFrom": "minh",
    "originalAuthor": "tuan",
    "content": "Có bạn nhé, 13h30 tại phòng C301!"
  }
}
```

---

### 4.6. `PING` & `PONG` (Heartbeat giữ kết nối)
Client định kỳ gửi `PING` mỗi 15 giây. Server phản hồi ngay lập tức `PONG`. Nếu quá 30 giây Server không nhận được gói tin nào từ Client thì tự động giải phóng kết nối socket.

#### Ping:
```json
{
  "type": "PING",
  "sender": "minh",
  "receiver": "SERVER",
  "timestamp": 1714310015000,
  "payload": {}
}
```

#### Pong:
```json
{
  "type": "PONG",
  "sender": "SERVER",
  "receiver": "minh",
  "timestamp": 1714310015010,
  "payload": {}
}
```

---

### 4.7. `ERROR_RESPONSE` (Phản hồi lỗi)

```json
{
  "type": "ERROR_RESPONSE",
  "requestId": "req-invalid",
  "sender": "SERVER",
  "receiver": "minh",
  "timestamp": 1714310000999,
  "payload": {
    "errorCode": "ERR_USER_OFFLINE",
    "errorMessage": "Người nhận hiện không trực tuyến hoặc đã ngắt kết nối.",
    "failedPacketType": "MESSAGE_SEND"
  }
}
```

---

## 5. BẢNG MÃ LỖI CHUẨN (STANDARD ERROR CODES)

| Mã lỗi | Tên định danh | Ý nghĩa & Hướng xử lý |
|:---|:---|:---|
| `ERR_001` | `ERR_MALFORMED_PACKET` | Gói tin sai cấu trúc framing, độ dài vượt quá giới hạn 10MB hoặc JSON không parse được. |
| `ERR_002` | `ERR_UNAUTHENTICATED` | Client thực hiện gửi tin trước khi hoàn tất đăng nhập/bắt tay. |
| `ERR_003` | `ERR_USER_OFFLINE` | Người nhận mục tiêu không có trong danh sách kết nối online của Server. |
| `ERR_004` | `ERR_USER_EXISTS` | Tên đăng nhập đã được đăng ký hoặc đã có phiên đang online. |
| `ERR_005` | `ERR_SERVER_BUSY` | Thread pool hoặc hàng đợi của máy chủ đã đầy, từ chối kết nối tạm thời. |
| `ERR_006` | `ERR_PAYLOAD_TOO_LARGE` | Kích thước chuỗi tin nhắn vượt quá dung lượng cho phép. |

---

## 6. QUY TRÌNH LUỒNG TRAO ĐỔI (SEQUENCE DIAGRAM)

```
Client                                      Server
  |                                            |
  |--- 1. TCP 3-Way Handshake (Socket SYN) --->|
  |<-- 2. TCP Handshake ACK -------------------|
  |                                            |
  |--- 3. [Length][CONNECT_REQUEST JSON] ----->|
  |<-- 4. [Length][CONNECT_RESPONSE JSON] -----|
  |                                            |
  |--- 5. [Length][ECHO_REQUEST JSON] -------->| (Kiểm thử Phase 1)
  |<-- 6. [Length][ECHO_RESPONSE JSON] --------|
  |                                            |
  |--- 7. [Length][PING JSON] ---------------->| (Mỗi 15 giây)
  |<-- 8. [Length][PONG JSON] -----------------|
  |                                            |
  |--- 9. Socket Close / Disconnect ---------->|
  |    (Server dọn dẹp Thread, giải phóng IP)  |
```
