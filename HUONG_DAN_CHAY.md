# HƯỚNG DẪN CÀI ĐẶT, KHỞI CHẠY VÀ KIỂM THỬ HỆ THỐNG
**Đề tài:** UDM_08 — Ứng dụng Chat TCP Client–Server qua GUI (Java Desktop)  
**Nhóm thực hiện:** Nhóm 11 (`NET_262701302_11`) — Môn Lập trình mạng

---

## 1. Yêu cầu môi trường

Trước khi chạy dự án, cần đảm bảo máy tính đã cài đặt:
- **JDK 21 LTS trở lên** (khuyến nghị Eclipse Temurin 21 hoặc Oracle OpenJDK 21).
- **Apache Maven 3.8+** (hoặc sử dụng Maven tích hợp sẵn trong IntelliJ IDEA).
- **Hệ điều hành:** Windows 10/11, macOS hoặc Linux có hỗ trợ giao diện đồ họa (Desktop GUI).

Kiểm tra nhanh trên Terminal / PowerShell:
```powershell
java -version
mvn -version
```

> **Lưu ý quan trọng về thư mục dự án:** Mã nguồn Maven nằm trong thư mục con `Code/`. Khi mở bằng **IntelliJ IDEA**, hãy chọn **File -> Open** và trỏ trực tiếp vào thư mục `Code` (nơi chứa file `pom.xml`) hoặc bấm **Load Maven Project** nếu mở từ thư mục gốc `laptrinhmang`.

---

## 2. Biên dịch và đóng gói dự án (Build Fat JAR)

Mở PowerShell / Terminal tại thư mục gốc `laptrinhmang`, di chuyển vào `Code` và chạy lệnh đóng gói:

```powershell
cd Code
mvn clean package -DskipTests
```

Sau khi hiển thị `BUILD SUCCESS`, Maven Shade Plugin sẽ sinh ra **2 file thực thi độc lập** trong thư mục `Code/target/`:
- `Code/target/chat-server.jar` — Ứng dụng Server kèm giao diện **Server Live Monitor Dashboard**.
- `Code/target/chat-client.jar` — Ứng dụng **Chat Client GUI**.

---

## 3. Hướng dẫn khởi chạy ứng dụng chính (Client – Server)

### Cách 1: Chạy trực tiếp bằng file đóng gói `.jar` (Khuyến nghị khi Demo & chạy LAN 2 máy)

1. **Bước 1 — Bật Server (kèm Dashboard giám sát thời gian thực):**
   Mở cửa sổ PowerShell thứ nhất tại thư mục `Code`:
   ```powershell
   java -jar target/chat-server.jar
   ```
   - Cửa sổ **Server Live Monitor Dashboard** sẽ bật lên, đồng thời `ChatServer` bắt đầu lắng nghe kết nối TCP tại cổng `8888`.

2. **Bước 2 — Bật một hoặc nhiều cửa sổ Client:**
   Mở các cửa sổ PowerShell mới tại thư mục `Code` (mỗi lệnh mở 1 người dùng độc lập):
   ```powershell
   java -jar target/chat-client.jar
   ```
   - Trên hộp thoại **Kết nối đến Server Chat**:
     - **IP Server:** `127.0.0.1` (nếu chạy cùng máy) hoặc địa chỉ IPv4 LAN của máy chạy Server (ví dụ `192.168.1.15`).
     - **Port:** `8888`
     - **Tên hiển thị:** Nhập tên người dùng (ví dụ `Alice`, `Bob`) -> Bấm **Kết nối**.

---

### Cách 2: Chạy trực tiếp trên IntelliJ IDEA

> **Quan trọng:** Tuyệt đối chạy thông qua các lớp **`*Launcher`** thay vì chạy trực tiếp lớp kế thừa `javafx.application.Application` để tránh lỗi *"JavaFX runtime components are missing"*.

1. **Reload All Maven Projects** (biểu tượng 🔄 ở thanh công cụ Maven bên phải).
2. **Khởi chạy Server:**
   - **Chế độ có giao diện Dashboard (Khuyến nghị):** Mở `src/main/java/com/chat/server/ServerLauncher.java` -> Chuột phải chọn **Run 'ServerLauncher.main()'**.
   - **Chế độ Console thuần (nhiều luồng, đọc `server.properties`):** Mở `src/main/java/com/chat/server/ServerApp.java` -> Chuột phải chọn **Run 'ServerApp.main()'**.
3. **Khởi chạy nhiều Client cùng lúc trên 1 máy:**
   - Mở `src/main/java/com/chat/client/ClientLauncher.java` -> Chuột phải chọn **Run 'ClientLauncher.main()'**.
   - *Mẹo mở Client thứ 2, thứ 3 trong IntelliJ:* Vào **Run -> Edit Configurations...** -> Chọn `ClientLauncher` -> Bấm **Modify options** -> Tích chọn **Allow multiple instances** -> **Apply**. Sau đó mỗi lần bấm Run sẽ mở thêm một cửa sổ Client mới.

---

### Cách 3: Kết nối qua mạng LAN giữa 2 máy tính vật lý

1. Kết nối **Máy A (Server)** và **Máy B (Client)** vào cùng một mạng Wi-Fi / LAN hoặc Điểm truy cập cá nhân (Mobile Hotspot).
2. Trên **Máy A (Server)**:
   - Mở PowerShell gõ `ipconfig` để lấy địa chỉ **IPv4 Address** (ví dụ: `192.168.1.25`).
   - Mở cổng `8888` trên Windows Defender Firewall (hoặc bấm **Allow access** khi Windows hỏi quyền mạng cho Java). Lệnh mở nhanh bằng PowerShell (Run as Administrator):
     ```powershell
     New-NetFirewallRule -DisplayName "Chat TCP Server 8888" -Direction Inbound -Protocol TCP -LocalPort 8888 -Action Allow
     ```
   - Khởi chạy Server (`java -jar target/chat-server.jar` hoặc `ServerLauncher`).
3. Trên **Máy B (Client)**:
   - Khởi chạy Client (`java -jar target/chat-client.jar` hoặc `ClientLauncher`).
   - Nhập IP của Máy A (`192.168.1.25`), Port `8888`, nhập tên hiển thị -> Bấm **Kết nối**.

---

## 4. Hướng dẫn chạy các Ứng dụng Demo Giao diện Chuyên biệt

Dự án cung cấp bộ ứng dụng Demo độc lập trong `src/test/java/com/chat/test/` để nghiệm thu chi tiết từng cụm tính năng UI/UX của các thành viên:

| STT | Tính năng nghiệm thu | Lớp khởi chạy trong IntelliJ (`Run main()`) | Lệnh chạy qua Maven (từ thư mục `Code/`) |
| :--- | :--- | :--- | :--- |
| **1** | **Avatar tròn Center-crop, Fallback chữ cái & Emoji Picker** (TV5) | `com.chat.test.AvatarEmojiDemoLauncher` | `mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.AvatarEmojiDemoLauncher"` |
| **2** | **Bong bóng Chat, Reply có trích dẫn, Forward & Avatar Base64** (TV4 & TV5) | `com.chat.test.MessageComponentsDemoLauncher` | `mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.MessageComponentsDemoLauncher"` |
| **3** | **Âm thanh "Ting Ting" (`javafx-media`), Nút Mute, Cuộn mượt & Chống tràn chữ** (TV5) | `com.chat.test.SoundAndPolishDemoLauncher` | `mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.SoundAndPolishDemoLauncher"` |
| **4** | **Tin nhắn tự hủy (Disappearing Messages) kèm huy hiệu đếm ngược & Fade-out** (TV6) | *(Chạy bằng lệnh Maven bên cạnh để nạp đủ module JavaFX)* | `mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.DisappearingMessageDemoApp"` |

---

## 5. Hướng dẫn chạy Kiểm thử Tự động, CSDL & Stress Test Hiệu năng

Toàn bộ lệnh dưới đây được thực thi tại thư mục `Code/`:

### 5.1. Chạy toàn bộ bộ Unit & Integration Test (JUnit 5)
Kiểm thử đồng loạt 9 bộ test: đóng gói JSON, mô phỏng đứt kết nối giữa gói tin, `NetworkService`, `ServerListener`, `HeartbeatService`, `AutoReconnect`, `SocketCleanup`, `FxNetworkEventListener` và `DisappearingMessageTimer`:
```powershell
cd Code
mvn test
```

Chạy riêng một bài test cụ thể (ví dụ `AutoReconnectTest` và `DisappearingMessageTimerTest`):
```powershell
mvn "-Dtest=AutoReconnectTest,DisappearingMessageTimerTest" test
```

### 5.2. Chạy kiểm thử Framing Byte-level (Chống dính gói & xé gói TCP)
Chạy lớp `com.chat.test.PacketFramerTest` trên IntelliJ IDEA hoặc bằng lệnh:
```powershell
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.PacketFramerTest"
```

### 5.3. Chạy kiểm thử SQLite Database, Xác thực SHA-256, Cleanup & Logback
Chạy lớp `com.chat.test.DatabaseTest` trên IntelliJ IDEA hoặc bằng lệnh:
```powershell
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.DatabaseTest"
```
- Sau khi chạy, kiểm tra file CSDL `chat.db` và file nhật ký xoay vòng `logs/server.log` được sinh tự động trong thư mục làm việc.

### 5.4. Chạy Công cụ Benchmark & Stress Test Đa mức tải (`HeadlessStressTester`)
Công cụ tự động sinh các luồng Client ảo bắn tải đồng thời qua TCP và xuất bảng đo đạc **Throughput (msgs/sec)**, **Bandwidth (KB/sec)**, **Min / Avg / P50 / P90 / P99 / Max Latency (ms)** và **Success Rate (%)**.

- **Chạy tự động trọn bộ 4 mức tải (`LIGHT` -> `MEDIUM` -> `HIGH` -> `EXTREME`):**
  *(Nếu cổng `8888` chưa có Server nào mở, công cụ sẽ tự khởi chạy Embedded ChatServer `maxThreads = 100` để đo benchmark chuẩn)*
  ```powershell
  mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.HeadlessStressTester"
  ```
  *(Hoặc mở `src/test/java/com/chat/test/HeadlessStressTester.java` trong IntelliJ -> Chuột phải **Run 'HeadlessStressTester.main()'**).*

- **Chạy một mức tải chỉ định hoặc bắn vào Server đang chạy bên ngoài:**
  ```powershell
  mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.chat.test.HeadlessStressTester" "-Dexec.args=--host 127.0.0.1 --port 8888 --level MEDIUM"
  ```
  Các giá trị `--level` hỗ trợ:
  - `LIGHT` (5 clients × 20 tin = 100 gói)
  - `MEDIUM` (20 clients × 50 tin = 1,000 gói)
  - `HIGH` (50 clients × 100 tin = 5,000 gói)
  - `EXTREME` (100 clients × 100 tin = 10,000 gói)
  - `ALL` (mặc định — chạy tuần tự cả 4 mức tải và in bảng so sánh tổng hợp)

---

## 6. Tệp cấu hình hệ thống

- **Cấu hình Server (`Code/src/main/resources/server.properties`):**
  - `server.port=8888` — Cổng TCP lắng nghe mặc định.
  - `server.max_connections=100` — Ngưỡng kết nối đồng thời.
  - `db.url=jdbc:sqlite:chat.db` — Đường dẫn cơ sở dữ liệu SQLite.
- **Cấu hình Client (`Code/src/main/resources/client.properties`):**
  - `server.ip=127.0.0.1`, `server.port=8888` — Endpoint mặc định nạp lên hộp thoại kết nối.
  - `connection.timeout=5000` — Timeout kết nối (5 giây).
  - `heartbeat.interval=15000`, `heartbeat.max.missed=3` — Chu kỳ Ping/Pong 15 giây, báo đứt mạng sau 3 chu kỳ mất phản hồi.
  - `reconnect.enabled=true`, `reconnect.initial.delay=2000`, `reconnect.max.delay=30000`, `reconnect.backoff.multiplier=2.0` — Cấu hình Auto-Reconnect theo Exponential Backoff + Jitter.

---

## 7. Xử lý các lỗi thường gặp (Troubleshooting)

1. **Lỗi `Error: JavaFX runtime components are missing, and are required to run this application`:**
   - **Nguyên nhân:** Chạy trực tiếp class `extends Application` (`ClientApp`, `ServerMonitorApp`, `*DemoApp`) từ IDE ở chế độ classpath.
   - **Cách khắc phục:** Luôn chạy qua class trung gian **`ClientLauncher`**, **`ServerLauncher`**, **`AvatarEmojiDemoLauncher`**, **`MessageComponentsDemoLauncher`**, **`SoundAndPolishDemoLauncher`** hoặc chạy qua file Fat JAR (`target/chat-server.jar`, `target/chat-client.jar`).

2. **Lỗi `java.net.BindException: Address already in use: bind` khi bật Server:**
   - **Nguyên nhân:** Đã có một tiến trình Server khác đang chạy ngầm và chiếm cổng `8888`.
   - **Cách khắc phục:** Tắt cửa sổ Server cũ trong IntelliJ/PowerShell, hoặc tìm và tắt tiến trình chiếm cổng `8888` bằng PowerShell:
     ```powershell
     netstat -ano | findstr :8888
     taskkill /PID <SO_PID> /F
     ```

3. **Client máy B báo `Hết thời gian chờ kết nối (5000ms)` khi chạy LAN 2 máy:**
   - **Nguyên nhân:** Máy A đang bật mạng ở chế độ *Public Network* khiến Windows Firewall chặn cổng `8888`, hoặc nhập nhầm IP của card mạng ảo (WSL/VMware/VirtualBox).
   - **Cách khắc phục:** Lấy đúng IP của card **Wireless LAN adapter Wi-Fi** trên Máy A và mở rule Firewall cổng `8888` như hướng dẫn ở Mục 3 (Cách 3).
