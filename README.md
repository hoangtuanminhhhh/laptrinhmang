# UDM_08 — Chat TCP Client–Server

Ứng dụng chat desktop client–server hoạt động qua giao thức TCP Socket thuần, toàn bộ thao tác người dùng được thực hiện bằng giao diện đồ họa JavaFX (Desktop GUI, không sử dụng Web/Browser).

- **Mã đề tài:** UDM_08
- **Tên đề tài:** Chat TCP Client–Server
- **Môn học:** Lập trình mạng
- **Giảng viên hướng dẫn:** ThS. Mai Ngọc Châu
- **Mã nhóm:** NET_262701302_11
- **Nhóm thực hiện:** Nhóm 11

---

## 1. Project Stack

- **Ngôn ngữ:** Java 21 (LTS)
- **Mạng:** Java Socket (TCP thuần, I/O Streams đa luồng)
- **Giao diện (GUI):** JavaFX 21.0.6 (FXML, Scene Builder, CSS Styling)
- **Định dạng dữ liệu:** Jackson Databind 2.17.2 / Gson 2.10.1 (POJO <-> JSON UTF-8)
- **Cơ sở dữ liệu:** SQLite JDBC 3.45.1.0 (`chat.db`)
- **Bảo mật:** at.favre.lib:bcrypt 0.10.2 được khai báo; `AuthService` hiện sử dụng SHA-256
- **Nhật ký (Logging):** `System.out` và `System.err`
- **Kiểm thử tự động:** JUnit Jupiter 5 & Custom Standalone Test Runners
- **Quản lý dự án & Build:** Apache Maven
- **Quản lý mã nguồn:** Git & GitHub

---

## 2. Yêu cầu môi trường

- **JDK:** Java Development Kit 21 trở lên (khuyến nghị Eclipse Temurin 21 hoặc Oracle OpenJDK 21)
- **Maven:** Apache Maven
- **Hệ điều hành:** Các hệ điều hành hỗ trợ JDK 21 và JavaFX
- **Scene Builder (Tùy chọn):** Khuyến nghị cài đặt để xem và chỉnh sửa giao diện FXML trực quan

---

## 3. Video Demo

- **Link video demo nghiệm thu:** _(Đang cập nhật sau khi hoàn thiện kiểm thử cuối Tuần 2)_

---

## 4. Chức năng hệ thống

### Chức năng hiện có:

1. **Gửi và nhận Echo qua TCP:** Client gửi yêu cầu Echo tới Server và nhận phản hồi.
2. **Kết nối mạng bất đồng bộ:** Client kết nối và nhận thông báo trạng thái mạng qua listener.
3. **Thành phần người dùng và SQLite:** Có các lớp model, repository và service xác thực.
4. **Hiển thị Avatar:** Có thành phần `AvatarView` với ảnh đại diện hoặc chữ viết tắt.
5. **Bộ chọn Emoji:** Có popup chọn emoji và chèn ký tự Unicode vào ô nhập JavaFX.

### Trạng thái kết nối hiển thị trên GUI:

- Client hiển thị thông báo đang kết nối, kết nối thành công, ngắt kết nối và lỗi trong vùng chat.

---

## 5. Bảng phân công thành viên, test case nhóm

| STT | Họ và tên      |  MSSV  | Vai trò / Phụ trách chính                         | Nhiệm vụ chi tiết (Tuần 1 & 2)                                                                                      |
| :-: | :------------- | :----: | :------------------------------------------------ | :------------------------------------------------------------------------------------------------------------------ |
|  1  | _Trưởng nhóm_  | _MSSV_ | **Network Architect & Server Core**               | `Packet`, `PacketType`, `PacketFramer`, `ChatServer`, `ClientHandler`, `ServerApp`.                                 |
|  2  | _Thành viên 2_ | _MSSV_ | **Server Database & Config Manager**              | `server.properties`, `schema.sql`, `NetworkConfig`, `User`/`Message` Model, `DatabaseManager`, `UserRepository`.    |
|  3  | _Thành viên 3_ | _MSSV_ | **Client Network Engine & Multi-threading**       | `NetworkService`, `ServerListener`, `NetworkEventListener`, `client.properties`.                                    |
|  4  | _Thành viên 4_ | _MSSV_ | **Client UI Lead (JavaFX & Scene Builder)**       | `ConfigDialog.fxml`, `MainChatView.fxml`, `style.css`, `ConfigDialogController`, `MainChatController`, `ClientApp`. |
|  5  | _Thành viên 5_ | _MSSV_ | **Client UI Components & Media (Avatar & Emoji)** | `AvatarView`, `EmojiPickerPopup`, `default_avatar.png`, `AvatarEmojiDemoApp`. _(Đã hoàn thành)_                     |
|  6  | _Thành viên 6_ | _MSSV_ | **QA, Git Flow & Architecture Documentation**     | `.gitignore`, `README.md`, `PROTOCOL_SPEC.md`, `TEST_PLAN_PHASE1.md`, `REPORT_DRAFT.md`, `PacketFramerTest.java`.   |


## 6. Kiến trúc hệ thống & Giao thức

### 6.1. Mô hình tổng thể

Hệ thống hiện có mô hình Client–Server qua TCP; Server hiện xử lý yêu cầu Echo:

```
  +--------------+                               +--------------+
  |   Client A   | <======== TCP Stream =======> |              |
  |  (JavaFX UI) |                               |    SERVER    |
  +--------------+                               | (Thread Pool |
                                                 |  & Echo)     |
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
- `CONNECT_REQUEST` / `CONNECT_RESPONSE`: Dự kiến dùng cho bắt tay thiết lập phiên.
- `LOGIN_REQUEST` / `LOGIN_RESPONSE`: Dự kiến dùng cho đăng nhập và xác thực.
- `MESSAGE_SEND` / `MESSAGE_RECEIVE`: Dự kiến dùng cho gửi và nhận tin nhắn.
- `REPLY_SEND` / `REPLY_RECEIVE`: Dự kiến dùng cho trả lời tin nhắn.
- `FORWARD_SEND` / `FORWARD_RECEIVE`: Dự kiến dùng cho chuyển tiếp tin nhắn.
- `PING` / `PONG`: Dự kiến dùng cho heartbeat.
- `ERROR_RESPONSE`: Dự kiến dùng cho phản hồi lỗi nghiệp vụ.

_(Xem chi tiết đặc tả từng trường JSON tại [docs/PROTOCOL_SPEC.md](docs/PROTOCOL_SPEC.md))_

---

## 7. Cấu hình hệ thống

### Phía Server (`Code/src/main/resources/server.properties`):

```properties
# Cổng lắng nghe kết nối TCP
server.port=8888
server.max_connections=100
server.read_timeout=30000
db.url=jdbc:sqlite:chat.db
```

### Phía Client (`Code/src/main/resources/client.properties`):

```properties
# Cấu hình mặc định khi mở app
server.ip=127.0.0.1
server.port=8888
connection.timeout=5000
heartbeat.interval=30000
```

---

## 8. Hướng dẫn cài đặt và khởi chạy

### Bước 1: Clone dự án

```bash
git clone https://github.com/hoangtuanminhhhh/laptrinhmang.git
cd laptrinhmang
```

### Bước 2: Build toàn bộ dự án với Maven

Di chuyển vào thư mục chứa code. Các file `Packet.java`, `PacketFramer.java` và `PacketType.java` hiện còn dấu phân định conflict Git, cần giải quyết trước khi build:

```bash
cd Code
mvn clean package -DskipTests
```

### Bước 3: Khởi chạy Server

Chạy lớp `com.chat.server.ServerApp` từ IDE có hỗ trợ Maven.

### Bước 4: Khởi chạy Client

Chạy lớp `com.chat.client.ClientApp` từ IDE có hỗ trợ Maven.

### Chạy ứng dụng Demo độc lập (Avatar & Emoji Picker của TV5):

Chạy lớp `com.chat.test.AvatarEmojiDemoLauncher` từ IDE với classpath kiểm thử Maven.

---

## 9. Hướng dẫn Kiểm thử (Testing)

Dự án tích hợp bộ kiểm thử cho từng tầng:

### Kiểm thử JUnit:

```powershell
cd Code
mvn test
```

### Kiểm thử JSON và mô phỏng ngắt kết nối:

```powershell
cd Code
mvn "-Dtest=PacketSerializationTest,DisconnectSimulationTest" test
```

`DatabaseTest` và `PacketFramerTest` là standalone test runners; chạy từ IDE với classpath kiểm thử Maven.

_(Chi tiết danh mục test cases kiểm thử tích hợp xem tại [docs/TEST_PLAN_PHASE1.md](docs/TEST_PLAN_PHASE1.md))_

---

## 10. Cấu trúc thư mục

```
.
├── .gitignore                          # Cấu hình GitIgnore tổng thể
├── README.md                           # Tài liệu tổng quan dự án
├── docs/                               # Tài liệu thiết kế & kiểm thử
│   ├── PROTOCOL_SPEC.md                # Đặc tả chi tiết giao thức TCP byte-level
│   ├── TEST_PLAN_PHASE1.md             # Kịch bản kiểm thử tích hợp Phase 1
│   └── REPORT_DRAFT.md                 # Khung sườn báo cáo môn học
├── Code/                               # Maven Project
│   ├── pom.xml                         # Cấu hình Maven dependencies
│   ├── .gitignore
│   └── src/
│       ├── main/
│       │   ├── java/com/chat/
│       │   │   ├── client/             # Client network, UI controllers & components
│       │   │   ├── common/             # Protocol, config, model dùng chung
│       │   │   └── server/             # Server socket core, thread pool, db
│       │   └── resources/              # FXML, icons, properties
│       └── test/java/com/chat/test/    # Tests & Demo launchers
├── DOCX/                               # Báo cáo đồ án (.docx)
├── Extra/                              # Ảnh demo, bằng chứng test, sơ đồ
└── PPTX/                               # Slide thuyết trình báo cáo (.pptx)
```

---

## 11. Xử lý lỗi & Nhật ký (Logging)

- Ứng dụng hiện ghi thông tin và lỗi bằng `System.out` và `System.err`.
- Client chuyển một số cập nhật từ sự kiện mạng về giao diện JavaFX thông qua `Platform.runLater()`.

---

## 12. Giới hạn & Hướng phát triển trong Phase 2

- **Hiện tại:** Có client/server TCP cơ bản, trao đổi Echo, các thành phần Avatar và Emoji, cùng các lớp SQLite và xác thực.
- **Phase 2:**
  - Tích hợp xác thực đăng ký / đăng nhập với luồng giao tiếp TCP.
  - Hoàn thiện chat riêng tư 1-1 và chat nhóm nhiều thành viên.
  - Cơ chế mã hóa TLS/SSL cho kết nối Socket.
  - Truyền tải file đính kèm (ảnh, tài liệu).
