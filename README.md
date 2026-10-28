# UDM_08 — Chat TCP Client–Server

Ứng dụng chat desktop client–server hoạt động qua giao thức TCP Socket thuần, toàn bộ thao tác người dùng được thực hiện bằng giao diện đồ họa JavaFX (Desktop GUI, không sử dụng Web/Browser).

* **Mã đề tài:** UDM_08
* **Tên đề tài:** Chat TCP Client–Server
* **Môn học:** Lập trình mạng
* **Giảng viên hướng dẫn:** Mai Ngọc Châu
* **Mã nhóm:** NET_262701302_11

---

## 1. Project Stack

- **Ngôn ngữ:** Java 21 (LTS)
- **Mạng:** Java Socket (TCP thuần, I/O Streams đa luồng)
- **Giao diện (GUI):** JavaFX 21.0.2 (FXML, Scene Builder, CSS Styling)
- **Định dạng dữ liệu:** Jackson Databind 2.17.0 (POJO <-> JSON UTF-8)
- **Cơ sở dữ liệu:** SQLite JDBC 3.45.2.0 (`chat.db`)
- **Bảo mật:** jBCrypt 0.4 (Mã hóa băm mật khẩu người dùng)
- **Nhật ký (Logging):** SLF4J 2.0.12 + Logback Classic 1.5.3
- **Kiểm thử tự động:** JUnit Jupiter 5.10.2 (JUnit 5)
- **Quản lý dự án & Build:** Apache Maven 3.9+
- **Quản lý mã nguồn:** Git & GitHub

---

## 2. Yêu cầu môi trường

- **JDK:** Java Development Kit 21 trở lên (khuyến nghị Eclipse Temurin 21 hoặc Oracle OpenJDK 21)
- **Maven:** Apache Maven 3.9.0 trở lên
- **Hệ điều hành đã thử nghiệm:** Windows 10/11 (64-bit), macOS (Apple Silicon / Intel), Ubuntu 22.04 LTS
- **Scene Builder (Tùy chọn):** Khuyến nghị cài đặt để chỉnh sửa các file FXML trực quan

---

## 3. Video Demo

* **Link video demo nghiệm thu:** *(Đang cập nhật sau khi hoàn thiện kiểm thử cuối Tuần 2)*

---

## 4. Chức năng hệ thống

### Chức năng bắt buộc:
1. **Gửi và nhận tin nhắn qua TCP:** Truyền tải thời gian thực giữa các client qua Server định tuyến.
2. **Reply tin nhắn:** Trả lời một tin nhắn cụ thể, hiển thị trích dẫn nội dung và người gửi của tin nhắn gốc.
3. **Forward tin nhắn:** Chuyển tiếp tin nhắn sang cho người dùng khác trong danh bạ.
4. **Hiển thị Avatar người dùng:** Avatar bo tròn trong danh sách bạn bè và khung chat (hỗ trợ fallback avatar theo chữ cái viết tắt).
5. **Bộ chọn Emoji:** Popup chọn bảng icon Emoji (40–60 emoji phổ biến), chèn ký tự Unicode trực tiếp vào ô chat không lỗi font.

### Trạng thái kết nối hiển thị trên GUI:
- **Xám (Offline/Idle):** Chưa kết nối
- **Vàng (Connecting):** Đang kết nối tới Server
- **Xanh (Connected):** Đã kết nối thành công
- **Đỏ (Disconnected/Error):** Mất kết nối hoặc kết nối thất bại
- **Trạng thái tin nhắn:** *Đang gửi...*, *Đã gửi*, *Gửi thất bại*.

---

## 5. Thành viên nhóm

| STT | Họ và tên | MSSV | Vai trò / Phụ trách chính | Nhiệm vụ chi tiết (Tuần 1 & 2) |
|:---:|:---|:---:|:---|:---|
| 1 | *Trưởng nhóm* | *MSSV* | **Network Architect & Server Core** | `Packet`, `PacketType`, `PacketFramer`, `ChatServer`, `ClientHandler`, `ServerApp`. |
| 2 | *Thành viên 2* | *MSSV* | **Server Database & Config Manager** | `server.properties`, `schema.sql`, `NetworkConfig`, `User`/`Message` Model, `DatabaseManager`, `UserRepository`. |
| 3 | *Thành viên 3* | *MSSV* | **Client Network Engine & Multi-threading** | `NetworkService`, `ServerListener`, `NetworkEventListener`, `client.properties`. |
| 4 | *Thành viên 4* | *MSSV* | **Client UI Lead (JavaFX & Scene Builder)** | `ConfigDialog.fxml`, `MainChatView.fxml`, `style.css`, `ConfigDialogController`, `MainChatController`, `ClientApp`. |
| 5 | *Thành viên 5* | *MSSV* | **Client UI Components & Media (Avatar & Emoji)** | `AvatarView`, `EmojiPickerPopup`, `default_avatar.png`, `AvatarEmojiDemoApp`. *(Đã hoàn thành)* |
| 6 | *Thành viên 6* | *MSSV* | **QA, Git Flow & Architecture Documentation** | `.gitignore`, `README.md`, `PROTOCOL_SPEC.md`, `TEST_PLAN_PHASE1.md`, `REPORT_DRAFT.md`, `PacketFramerTest.java`. |

---

## 6. Kiến trúc hệ thống & Giao thức

### 6.1. Mô hình tổng thể
Hệ thống hoạt động theo mô hình Client–Server tập trung qua giao thức vận chuyển TCP:

```
  +--------------+                               +--------------+
  |   Client A   | <======== TCP Stream =======> |              |
  |  (JavaFX UI) |                               |    SERVER    |
  +--------------+                               | (Thread Pool |
                                                 |  & Routing)  |
  +--------------+                               |              |
  |   Client B   | <======== TCP Stream =======> |   chat.db    |
  |  (JavaFX UI) |                               |   (SQLite)   |
  +--------------+                               +--------------+
```

### 6.2. Cơ chế đóng gói (Framing) chống Dính gói / Xé gói
TCP là giao thức hướng luồng (stream-oriented), không phân định ranh giới gói tin. Do đó, hệ thống áp dụng cơ chế **Length-Prefixed Framing**:
- **Header:** 4 byte nguyên (Big-Endian integer) thể hiện kích thước chính xác $N$ của phần thân dữ liệu (Payload) theo đơn vị byte.
- **Payload:** Đúng $N$ bytes chuỗi JSON được mã hóa bằng chuẩn `UTF-8`.

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 Payload Length (4 bytes int)                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                     JSON Payload (UTF-8)                      +
|                     Length = N bytes                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 6.3. Các loại gói tin chính (Packet Types)
- `ECHO_REQUEST` / `ECHO_RESPONSE`: Kiểm thử đường truyền Phase 1.
- `CONNECT_REQUEST` / `CONNECT_RESPONSE`: Bắt tay thiết lập phiên.
- `LOGIN_REQUEST` / `LOGIN_RESPONSE`: Đăng nhập & xác thực danh tính.
- `MESSAGE_SEND` / `MESSAGE_RECEIVE`: Gửi và nhận tin nhắn riêng/nhóm.
- `REPLY_SEND` / `REPLY_RECEIVE`: Trả lời tin nhắn kèm ID tin nhắn cha.
- `FORWARD_SEND` / `FORWARD_RECEIVE`: Chuyển tiếp tin nhắn cho người khác.
- `PING` / `PONG`: Heartbeat định kỳ giữ kết nối và phát hiện timeout.
- `ERROR_RESPONSE`: Phản hồi lỗi nghiệp vụ từ Server.

*(Xem chi tiết đặc tả từng trường JSON tại [docs/PROTOCOL_SPEC.md](docs/PROTOCOL_SPEC.md))*

---

## 7. Cấu hình hệ thống

### Phía Server (`laptrinhmang/Code/src/main/resources/server.properties`):
```properties
# Cổng lắng nghe kết nối TCP
server.port=5000

# Đường dẫn file CSDL SQLite
database.path=data/chat.db

# Thời gian timeout chờ đọc dữ liệu (ms)
server.read.timeout=30000
```

### Phía Client (`laptrinhmang/Code/src/main/resources/client.properties`):
```properties
# Cấu hình mặc định khi mở app
server.host=127.0.0.1
server.port=5000
connect.timeout=5000
```

---

## 8. Hướng dẫn cài đặt và khởi chạy

### Bước 1: Clone dự án
```bash
git clone https://github.com/hoangtuanminhhhh/laptrinhmang.git
cd laptrinhmang
```

### Bước 2: Build toàn bộ dự án với Maven
Di chuyển vào thư mục chứa code:
```bash
cd laptrinhmang/Code
mvn clean package -DskipTests
```

### Bước 3: Khởi chạy Server
Cách 1 — Chạy qua Maven plugin:
```bash
mvn exec:java
```
Cách 2 — Chạy file JAR độc lập sau khi build:
```bash
java -jar target/chat-server.jar
```

### Bước 4: Khởi chạy Client
Cách 1 — Chạy qua JavaFX Maven plugin:
```bash
mvn javafx:run
```
Cách 2 — Chạy file JAR độc lập:
```bash
java -jar target/chat-client.jar
```

### Chạy ứng dụng Demo độc lập (Avatar & Emoji Picker của TV5):
```bash
mvn test-compile exec:java -Dexec.mainClass="com.chat.test.AvatarEmojiDemoLauncher" -Dexec.classpathScope="test"
```

---

## 9. Hướng dẫn Kiểm thử (Testing)

Dự án tích hợp bộ kiểm thử tự động với **JUnit 5**:
Chạy toàn bộ các ca Unit Test:
```bash
cd laptrinhmang/Code
mvn test
```

Chạy riêng kiểm thử tầng Framing chống dính/xé gói (`PacketFramerTest`):
```bash
mvn test -Dtest=PacketFramerTest
```

*(Chi tiết danh mục test cases kiểm thử tích hợp xem tại [docs/TEST_PLAN_PHASE1.md](docs/TEST_PLAN_PHASE1.md))*

---

## 10. Cấu trúc thư mục

```
.
├── .gitignore                          # Cấu hình GitIgnore tổng thể
├── README.md                           # Tài liệu tổng quan dự án
├── bangphancong.md                     # Bảng phân công nhiệm vụ chi tiết
├── docs/                               # Tài liệu thiết kế & kiểm thử (TV6)
│   ├── PROTOCOL_SPEC.md                # Đặc tả chi tiết giao thức TCP byte-level
│   ├── TEST_PLAN_PHASE1.md             # Kịch bản kiểm thử tích hợp Phase 1
│   └── REPORT_DRAFT.md                 # Khung sườn báo cáo môn học
├── laptrinhmang/
│   ├── Code/                           # Maven Project
│   │   ├── pom.xml                     # Cấu hình Maven dependencies & plugins
│   │   ├── .gitignore
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/chat/
│   │       │   │   ├── client/         # Client network, UI controllers & components
│   │       │   │   ├── common/         # Protocol, config, model dùng chung
│   │       │   │   └── server/         # Server socket core, thread pool, db
│   │       │   └── resources/          # FXML, CSS, icons, properties
│   │       └── test/java/com/chat/test/ # Unit tests & Demo launchers
│   ├── DOCX/                           # Báo cáo đồ án (.docx)
│   ├── Extra/                          # Ảnh demo, bằng chứng test, sơ đồ
│   └── PPTX/                           # Slide thuyết trình báo cáo (.pptx)
```

---

## 11. Xử lý lỗi & Nhật ký (Logging)
- Ứng dụng ghi nhận log có cấu trúc thông qua thư viện `Logback`.
- Mọi sự kiện kết nối, ngắt kết nối bất thường (`EOFException`, `SocketTimeoutException`) đều được ghi log rõ ràng ở cấp độ `INFO` và `WARN` kèm địa chỉ Remote IP.
- Mọi thao tác ngoại lệ mạng ở Client đều được chuyển tiếp an toàn về giao diện JavaFX thông qua `Platform.runLater()` nhằm tránh hiện tượng crash giao diện hoặc Not-Responding.

---

## 12. Giới hạn & Hướng phát triển trong Phase 2
- **Phase 1 (Hiện tại):** Hoàn thành bắt tay TCP, đóng gói 4-byte header, truyền tải tin nhắn Echo, xử lý UI status bar, tích hợp Avatar bo tròn và bảng Emoji.
- **Phase 2:**
  - Hệ thống xác thực đăng ký / đăng nhập với SQLite và jBCrypt.
  - Chat riêng tư 1-1 và Chat nhóm nhiều thành viên.
  - Cơ chế mã hóa End-to-End (E2EE) hoặc TLS/SSL cho kết nối Socket.
  - Truyền tải file đính kèm (ảnh, tài liệu).
