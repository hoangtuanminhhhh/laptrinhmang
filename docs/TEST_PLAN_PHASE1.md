# KẾ HOẠCH VÀ KỊCH BẢN KIỂM THỬ TÍCH HỢP (PHASE 1)
## Dự án: UDM_08 — Chat TCP Client–Server
**Phiên bản:** 1.0 (Phase 1: Echo & Core Network Verification)
**Người lập kế hoạch & Chịu trách nhiệm QA:** Thành viên 6

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

### 1.1. Mục tiêu
- Xác minh độ ổn định và tính toàn vẹn của kết nối TCP Socket giữa Client và Server.
- Kiểm tra cơ chế đóng/mở gói (Framing) của `PacketFramer`, đảm bảo tuyệt đối không xảy ra hiện tượng **dính gói (sticky packets)** hoặc **xé gói (fragmented packets)**.
- Đảm bảo luồng I/O mạng của Client chạy độc lập, không gây nghẽn hoặc đóng băng (Freeze/ANR) giao diện JavaFX.
- Kiểm tra khả năng xử lý chuỗi Unicode có dấu (Tiếng Việt) và các ký tự icon Emoji đặc thù không bị lỗi ô vuông (`□`).
- Bắt trọn vẹn các ngoại lệ mạng phổ biến: sai IP/Port, Server tắt đột ngột, ngắt kết nối vật lý, timeout.

### 1.2. Phạm vi Phase 1
- **Đối tượng kiểm thử:**
  - `ChatServer` (TV1) & `ClientHandler` (TV1).
  - `PacketFramer` & `Packet` (TV1).
  - `NetworkService` & `ServerListener` (TV3).
  - `ConfigDialog` & `MainChatController` (TV4).
  - `AvatarView` & `EmojiPickerPopup` (TV5).
- **Tính năng trọng tâm:** Echo Request - Response, Bắt tay kết nối, Đổi màu đèn trạng thái, Gửi emoji & text dài.

---

## 2. MA TRẬN KỊCH BẢN KIỂM THỬ CHI TIẾT (TEST CASES MATRIX)

### Nhóm 1: Kiểm thử Khởi tạo & Trạng thái Kết nối (Connection & Status Bar)

| Mã TC | Tên ca kiểm thử | Điều kiện tiên quyết | Các bước thực hiện | Dữ liệu đầu vào | Kết quả mong đợi |
|:---:|:---|:---|:---|:---|:---|
| **TC-01** | Kết nối Server thành công (Happy Path) | Server đang chạy tại `127.0.0.1:5000` | 1. Bật Client.<br>2. Nhập IP & Port đúng.<br>3. Bấm **Kết nối**. | IP: `127.0.0.1`<br>Port: `5000` | - Trạng thái đổi từ Vàng sang **Xanh lá (Đã kết nối)**.<br>- Vùng nhập tin nhắn mở khóa.<br>- Server log: `Client connected: /127.0.0.1`. |
| **TC-02** | Kết nối khi Server chưa bật (Connection Refused) | Server chưa khởi động | 1. Bật Client.<br>2. Nhập IP `127.0.0.1`, Port `5000`.<br>3. Bấm **Kết nối**. | IP: `127.0.0.1`<br>Port: `5000` | - Client chuyển đèn **Đỏ**.<br>- Hiển thị thông báo: `Không thể kết nối tới Server (Connection Refused)`.<br>- Không crash ứng dụng. |
| **TC-03** | Nhập sai định dạng IP / Port | Bật Client | 1. Nhập Port là chữ `abc` hoặc số âm `-1` hoặc `99999`.<br>2. Bấm **Kết nối**. | Port: `99999` | - Báo lỗi validate ngay trên UI: `Cổng Port phải là số từ 1 đến 65535`.<br>- Không gửi request socket. |
| **TC-04** | Kết nối Timeout (Sai IP mạng) | Bật Client | 1. Nhập một IP không tồn tại trong mạng LAN (VD: `192.168.1.250`).<br>2. Bấm **Kết nối**. | Timeout: `5000ms` | - Sau đúng 5 giây, đèn chuyển sang **Đỏ**.<br>- Báo lỗi `Kết nối quá thời gian chờ (SocketTimeoutException)`. |
| **TC-05** | Server bị tắt đột ngột khi đang chat | Client và Server đang kết nối xanh | 1. Tắt đột ngột tiến trình Server (Force Kill / `Ctrl+C`).<br>2. Quan sát giao diện Client. | Tiến trình Server bị Kill | - Trong vòng 3s, Client nhận diện `EOFException`.<br>- Đèn chuyển sang **Đỏ (Mất kết nối)**.<br>- Khóa ô chat, hiển thị popup cảnh báo. |

---

### Nhóm 2: Kiểm thử Giao thức Echo & Tính toàn vẹn Dữ liệu (Framing & Payload)

| Mã TC | Tên ca kiểm thử | Điều kiện tiên quyết | Các bước thực hiện | Dữ liệu đầu vào | Kết quả mong đợi |
|:---:|:---|:---|:---|:---|:---|
| **TC-06** | Gửi tin nhắn tiếng Anh ngắn | Kết nối thành công | 1. Nhập `Hello World!`.<br>2. Bấm nút **Gửi**. | `"Hello World!"` | - Server nhận đủ gói tin, phản hồi Echo trong < 100ms.<br>- Màn hình chat hiển thị tin nhắn phản hồi. |
| **TC-07** | Gửi chuỗi Tiếng Việt đầy đủ dấu Unicode | Kết nối thành công | 1. Nhập văn bản tiếng Việt có dấu: `Cộng hòa Xã hội Chủ nghĩa Việt Nam`.<br>2. Bấm **Gửi**. | Chuỗi UTF-8 tiếng Việt | - Server đọc đúng số byte UTF-8 (tiếng Việt chiếm 2-3 bytes/ký tự).<br>- Phản hồi về Client hiển thị hoàn hảo, không lỗi font `??` hoặc ô vuông. |
| **TC-08** | Gửi tin nhắn kèm Emoji Unicode | Kết nối thành công | 1. Mở popup Emoji của TV5.<br>2. Chọn 3 icon: `😊`, `🚀`, `🎉`.<br>3. Bấm **Gửi**. | Chuỗi chứa Emoji 4-byte | - Icon được hiển thị sắc nét trên khung chat.<br>- Chiều dài byte header tính toán chính xác. |
| **TC-09** | Gửi tin nhắn cực dài (Payload biên 10.000 ký tự) | Kết nối thành công | 1. Dán một đoạn văn bản dài 10.000 ký tự.<br>2. Bấm **Gửi**. | Chuỗi văn bản 10.000 ký tự | - `PacketFramer` đọc đủ 10.000 bytes qua `readFully`.<br>- Toàn bộ nội dung được phản hồi nguyên vẹn, không bị mất phần đuôi. |
| **TC-10** | Dính gói (Sticky Packets Simulation) | Chạy kiểm thử tự động `PacketFramerTest` | Ghi liên tiếp 3 gói tin vào 1 `ByteArrayOutputStream` không có khoảng nghỉ, sau đó cho framer đọc. | 3 gói tin liên tiếp | - Framer đọc tách biệt chính xác thành 3 gói riêng rẽ, không nuốt gói. |
| **TC-11** | Xé gói (Fragmented Packets Simulation) | Chạy kiểm thử tự động `PacketFramerTest` | Gửi 1 gói tin 100 bytes nhưng chia làm 2 đợt ghi (50 bytes đợt 1, 50 bytes đợt 2). | Gói tin phân mảnh | - Framer đợi đủ 100 bytes mới trả về `Packet`, không ném ngoại lệ sớm. |

---

### Nhóm 3: Kiểm thử Đa luồng & Hiệu năng (Concurrency & Multi-Client)

| Mã TC | Tên ca kiểm thử | Điều kiện tiên quyết | Các bước thực hiện | Dữ liệu đầu vào | Kết quả mong đợi |
|:---:|:---|:---|:---|:---|:---|
| **TC-12** | Nhiều Client kết nối đồng thời | Server đang chạy | 1. Bật đồng thời 3 cửa sổ Client khác nhau.<br>2. Cả 3 cùng kết nối vào Server. | 3 Client | - Cả 3 đều nhận đèn Xanh.<br>- Server tạo 3 Worker Thread riêng biệt trong Thread Pool, log IP rõ ràng. |
| **TC-13** | Gửi tin nhắn liên tục (Stress Test nhẹ) | 1 Client kết nối | Gửi tự động 50 tin nhắn trong vòng 1 giây. | 50 gói `ECHO_REQUEST` | - Server xử lý và echo đủ 50 gói, không bị rò rỉ socket, không tràn ram. |
| **TC-14** | Client ngắt kết nối an toàn (Graceful Disconnect) | Client đang kết nối | Bấm nút **Ngắt kết nối** hoặc đóng cửa sổ X của Client. | Sự kiện Close Window | - Client đóng Socket sạch sẽ.<br>- Server bắt được tín hiệu ngắt, giải phóng thread, không in tràn `StackTrace` ra console. |

---

## 3. QUY TRÌNH THỰC HIỆN VÀ GHI NHẬN BẰNG CHỨNG (TEST EVIDENCE)

1. **Chuẩn bị môi trường:**
   - Đảm bảo JDK 21 đã sẵn sàng.
   - Biên dịch dự án: `mvn clean test-compile`.
2. **Thực thi Unit Test tự động:**
   - Chạy lệnh: `mvn test -Dtest=PacketFramerTest`.
   - Kết quả xuất ra màn hình console và file báo cáo tại `target/surefire-reports/`.
3. **Thực thi Integration Test thủ công:**
   - Mở Terminal 1: Chạy Server (`mvn exec:java`).
   - Mở Terminal 2: Chạy Client (`mvn javafx:run`).
   - Mở công cụ quay màn hình (OBS Studio hoặc Xbox Game Bar `Win + G`).
   - Lần lượt thực hiện từ **TC-01** đến **TC-14**.
4. **Lưu trữ bằng chứng:**
   - Chụp ảnh màn hình các ca test thành công và lưu vào thư mục `laptrinhmang/Extra/screenshots/`.
   - Lưu video clip demo (độ dài 2-3 phút) vào `laptrinhmang/Extra/demo_phase1.mp4`.
   - Xuất file log server lưu vào `laptrinhmang/Extra/server_test.log`.
