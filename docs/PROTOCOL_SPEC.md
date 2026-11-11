# ĐẶC TẢ GIAO THỨC TRUYỀN THÔNG MẠNG (PROTOCOL SPECIFICATION)
## Dự án: UDM_08 — Chat TCP Client–Server


---

## 1. TỔNG QUAN VÀ NGUYÊN LÝ THIẾT KẾ

### 1.1. Phạm vi áp dụng
Tài liệu này mô tả thiết kế cấu trúc gói tin, phương thức đóng khung nhị phân (Framing) và giao tiếp JSON dự kiến giữa **Chat Client** và **Chat Server** qua socket TCP. Các loại gói ngoài Echo là đặc tả mục tiêu, chưa đồng nghĩa đã được triển khai hoặc kiểm chứng trong ứng dụng.

### 1.2. Giải pháp chống Dính gói (Sticky Packets) và Xé gói (Fragmented Packets)
Giao thức tầng giao vận TCP là hướng dòng byte (byte-stream), không có khái niệm "ranh giới tin nhắn" (message boundary). Do đó, một lần đọc `InputStream.read()` có thể nhận:
- Chỉ một phần của gói tin (Xé gói / Phân mảnh).
- Nhiều gói tin liên tiếp dính liền nhau (Dính gói).

**Giải pháp:** Áp dụng kỹ thuật **Length-Prefixed Framing**:
- Mỗi gói tin bắt đầu bằng đúng **4 byte nguyên có dấu (32-bit signed integer)** theo thứ tự byte chuẩn mạng **Big-Endian**, biểu thị số byte của phần dữ liệu JSON Payload đi kèm.
- Kế tiếp 4 byte header là đúng $N$ byte nhị phân được mã hóa theo chuẩn **UTF-8** của chuỗi JSON.
- Phía đọc dữ liệu phải đọc đủ $N$ byte trước khi tiến hành parse JSON; cách triển khai hiện tại trong `PacketFramer` dùng `DataInputStream.readFully`.

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
- **Kích thước tối thiểu theo ví dụ JSON:** $2$ bytes (chuỗi JSON rỗng `{}`); giá trị thực tế phụ thuộc nội dung gói.
- **Kích thước tối đa trong `PacketFramer` hiện tại:** $10.485.760$ bytes ($10$ MiB). Độ dài âm hoặc vượt giới hạn bị từ chối bằng `IOException`; caller chịu trách nhiệm xử lý lỗi và đóng kết nối nếu cần.

---

## 3. KHUNG DỮ LIỆU JSON TỔNG QUÁT (ENVELOPE SCHEMA)

Theo thiết kế, gói tin sau khi bóc tách 4-byte header có định dạng JSON Envelope như sau:

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
| `type` | String (Enum) | Theo loại gói | Định danh loại gói tin (xem mục 4). |
| `requestId` | String | Không | UUID định danh yêu cầu, dùng để map request với response tương ứng. |
| `sender` | String | Theo loại gói | Username của người gửi (hoặc `"SERVER"` nếu là tin từ máy chủ). |
| `receiver` | String | Theo loại gói | Username người nhận, `"ALL"` nếu broadcast, hoặc `"SERVER"`. |
| `timestamp` | Long | Theo loại gói | Thời gian tạo gói (Unix Epoch timestamp tính bằng milliseconds). |
| `payload` | Object | Theo loại gói | Đối tượng JSON chứa tham số chi tiết của từng loại nghiệp vụ. |

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
Theo thiết kế dự kiến, Client gửi `PING` mỗi 15 giây và Server phản hồi `PONG`. Mã nguồn hiện tại chưa triển khai heartbeat định kỳ phía Client hoặc xử lý `PING`/`PONG` tương ứng trên Server.

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

## 5. BẢNG MÃ LỖI DỰ KIẾN (PLANNED ERROR CODES)

| Mã lỗi | Tên định danh | Ý nghĩa & Hướng xử lý |
|:---|:---|:---|
| `ERR_001` | `ERR_MALFORMED_PACKET` | Dự kiến dùng cho gói sai framing hoặc JSON không parse được; `PacketFramer` hiện ném `IOException` khi độ dài không hợp lệ. |
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
  |--- 3. [Length][CONNECT_REQUEST JSON] ----->| (Theo thiết kế; chưa được xử lý trong Server)
  |<-- 4. [Length][CONNECT_RESPONSE JSON] -----| (Theo thiết kế; chưa được xử lý trong Server)
  |                                            |
  |--- 5. [Length][ECHO_REQUEST JSON] -------->| (Kiểm thử Phase 1)
  |<-- 6. [Length][ECHO_RESPONSE JSON] --------|
  |                                            |
  |--- 7. [Length][PING JSON] ---------------->| (Theo thiết kế; chưa có heartbeat định kỳ)
  |<-- 8. [Length][PONG JSON] -----------------| (Theo thiết kế)
  |                                            |
  |--- 9. Socket Close / Disconnect ---------->|
  |    (Server dọn dẹp Thread, giải phóng IP)  |
```
