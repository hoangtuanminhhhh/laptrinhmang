# KẾ HOẠCH VÀ KỊCH BẢN KIỂM THỬ TÍCH HỢP (PHASE 1)

## Dự án: UDM_08 — Chat TCP Client–Server

**Phiên bản:** 1.0 (Phase 1: Echo & Core Network Verification)


---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

### 1.1. Mục tiêu

- Kiểm tra độ ổn định và tính toàn vẹn của kết nối TCP Socket giữa Client và Server.
- Kiểm tra cơ chế đóng/mở gói (Framing) của `PacketFramer` với các trường hợp **dính gói (sticky packets)** và **xé gói (fragmented packets)**.
- Kiểm tra luồng I/O mạng của Client và phản hồi của giao diện JavaFX.
- Kiểm tra khả năng xử lý chuỗi Unicode có dấu (Tiếng Việt) và các ký tự icon Emoji đặc thù không bị lỗi ô vuông (`□`).
- Ghi nhận cách ứng dụng xử lý các tình huống sai IP/Port, Server tắt đột ngột, ngắt kết nối và timeout.

### 1.2. Phạm vi Phase 1

- **Đối tượng kiểm thử:**
  - `ChatServer` (TV1) & `ClientHandler` (TV1).
  - `PacketFramer` & `Packet` (TV1).
  - `NetworkService` & `ServerListener` (TV3).
  - `ConfigDialog` & `MainChatController` (TV4).
  - `AvatarView` & `EmojiPickerPopup` (TV5).
- **Tính năng trọng tâm:** Echo Request - Response, kết nối TCP, thông báo trạng thái, gửi emoji và text dài. Bắt tay nghiệp vụ và đèn trạng thái màu chưa được xác nhận triển khai.

---

## 2. MA TRẬN KỊCH BẢN KIỂM THỬ CHI TIẾT (TEST CASES MATRIX)

### Nhóm 1: Kiểm thử Khởi tạo & Trạng thái Kết nối (Connection & Status Messages)

|   Mã TC   | Tên ca kiểm thử                                  | Điều kiện tiên quyết                  | Các bước thực hiện                                                                          | Dữ liệu đầu vào                 | Kết quả mong đợi                                                                                                                          |
| :-------: | :----------------------------------------------- | :------------------------------------ | :------------------------------------------------------------------------------------------ | :------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------- |
| **TC-01** | Kết nối Server thành công (Happy Path)           | Server chạy tại `127.0.0.1:8888` | 1. Bật Client.<br>2. Nhập IP, Port và username.<br>3. Bấm **Kết nối**. | IP: `127.0.0.1`<br>Port: `8888` | - Khu vực chat hiển thị thông báo kết nối thành công.<br>- Server ghi log `Client connected: ...`. |
| **TC-02** | Kết nối khi Server chưa bật (Connection Refused) | Server chưa khởi động | 1. Bật Client.<br>2. Nhập IP `127.0.0.1`, Port `8888` và username.<br>3. Bấm **Kết nối**. | IP: `127.0.0.1`<br>Port: `8888` | - Khu vực chat hiển thị lỗi kết nối.<br>- Ứng dụng không bị đóng đột ngột. |
| **TC-03** | Nhập sai định dạng IP / Port | Bật Client | 1. Để trống trường bắt buộc, nhập Port không phải số hoặc nhập ngoài khoảng cho phép.<br>2. Bấm **Kết nối**. | IP/Port không hợp lệ | - Trường bỏ trống thì thao tác không tiếp tục.<br>- Port không phải số hiện được bắt và in stack trace; Port ngoài khoảng hợp lệ được báo lỗi từ dịch vụ mạng.<br>- Ghi nhận hành vi thực tế. |
| **TC-04** | Kết nối Timeout hoặc không tới được Server | Bật Client | 1. Nhập địa chỉ không thể kết nối tới.<br>2. Bấm **Kết nối** và ghi nhận thời gian/kết quả. | Timeout cấu hình mặc định: `5000ms` | - Kết nối thất bại hoặc timeout được báo qua callback lỗi.<br>- Không giả định mọi địa chỉ sai đều chờ đủ 5 giây; thời gian thực tế phụ thuộc kết quả kết nối mạng. |
| **TC-05** | Server bị tắt đột ngột khi đang kết nối | Client và Server đang kết nối | 1. Tắt tiến trình Server.<br>2. Quan sát khu vực chat của Client. | Tiến trình Server bị dừng | - Client nhận sự kiện ngắt kết nối/lỗi và hiển thị thông báo.<br>- Không giả định thời gian phát hiện cố định hoặc popup/khóa ô chat nếu chưa được triển khai. |

---

### Nhóm 2: Kiểm thử Giao thức Echo & Tính toàn vẹn Dữ liệu (Framing & Payload)

|   Mã TC   | Tên ca kiểm thử                                  | Điều kiện tiên quyết                     | Các bước thực hiện                                                                                 | Dữ liệu đầu vào            | Kết quả mong đợi                                                                                                                                 |
| :-------: | :----------------------------------------------- | :--------------------------------------- | :------------------------------------------------------------------------------------------------- | :------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------- |
| **TC-06** | Gửi tin nhắn tiếng Anh ngắn                      | Kết nối thành công                       | 1. Nhập `Hello World!`.<br>2. Bấm nút **Gửi**.                                                     | `"Hello World!"`           | - Server nhận gói `ECHO_REQUEST` và trả `ECHO_RESPONSE`.<br>- Client hiển thị nội dung phản hồi; ghi lại latency đo được, không áp ngưỡng chưa kiểm chứng. |
| **TC-07** | Gửi chuỗi Tiếng Việt đầy đủ dấu Unicode          | Kết nối thành công                       | 1. Nhập văn bản tiếng Việt có dấu: `Cộng hòa Xã hội Chủ nghĩa Việt Nam`.<br>2. Bấm **Gửi**.        | Chuỗi UTF-8 tiếng Việt     | - Nội dung được nhận và hiển thị nguyên vẹn sau khi Echo.<br>- Ghi nhận lỗi mã hóa/ký tự nếu có. |
| **TC-08** | Gửi tin nhắn kèm Emoji Unicode                   | Kết nối thành công                       | 1. Mở popup Emoji của TV5.<br>2. Chọn 3 icon: `😊`, `🚀`, `🎉`.<br>3. Bấm **Gửi**.                 | Chuỗi chứa Emoji 4-byte    | - Emoji được chèn vào ô nhập và chuỗi Echo giữ nguyên các ký tự.<br>- Ghi nhận lỗi hiển thị nếu có. |
| **TC-09** | Gửi tin nhắn cực dài (Payload biên 10.000 ký tự) | Kết nối thành công                       | 1. Dán một đoạn văn bản dài 10.000 ký tự.<br>2. Bấm **Gửi**.                                       | Chuỗi văn bản 10.000 ký tự | - Nếu payload nằm trong giới hạn framing, toàn bộ nội dung được Echo nguyên vẹn; ghi nhận lỗi hoặc giới hạn thực tế nếu có. |
| **TC-10** | Dính gói (Sticky Packets Simulation)             | Chạy kiểm thử tự động `PacketFramerTest` | Ghi liên tiếp 3 gói tin vào 1 `ByteArrayOutputStream` không có khoảng nghỉ, sau đó cho framer đọc. | 3 gói tin liên tiếp        | - Framer đọc tách biệt chính xác thành 3 gói riêng rẽ, không nuốt gói.                                                                           |
| **TC-11** | Xé gói (Fragmented Packets Simulation)           | Chạy kiểm thử tự động `PacketFramerTest` | Cho stream cung cấp dữ liệu theo từng phần, tối đa 3 bytes mỗi lần đọc. | Gói tin phân mảnh | - Framer đợi đủ dữ liệu để trả về `Packet` hoàn chỉnh; không parse payload cụt. |

---

### Nhóm 3: Kiểm thử Đa luồng & Hiệu năng (Concurrency & Multi-Client)

|   Mã TC   | Tên ca kiểm thử                                   | Điều kiện tiên quyết | Các bước thực hiện                                                              | Dữ liệu đầu vào       | Kết quả mong đợi                                                                                                            |
| :-------: | :------------------------------------------------ | :------------------- | :------------------------------------------------------------------------------ | :-------------------- | :-------------------------------------------------------------------------------------------------------------------------- |
| **TC-12** | Nhiều Client kết nối đồng thời                    | Server đang chạy     | 1. Bật đồng thời 3 cửa sổ Client khác nhau.<br>2. Cả 3 cùng kết nối vào Server. | 3 Client              | - Cả 3 kết nối được chấp nhận.<br>- Server ghi nhận các kết nối; xác nhận không lỗi khi nhiều Client hoạt động đồng thời. |
| **TC-13** | Gửi tin nhắn liên tục (Stress Test nhẹ)           | 1 Client kết nối; có công cụ gửi test | Gửi 50 gói Echo liên tiếp và ghi lại số gói phản hồi. | 50 gói `ECHO_REQUEST` | - Server phản hồi đủ số gói đã nhận; ghi nhận lỗi hoặc mất phản hồi nếu có. |
| **TC-14** | Client ngắt kết nối an toàn (Graceful Disconnect) | Client đang kết nối | Đóng cửa sổ Client. | Sự kiện đóng cửa sổ | - Client đóng Socket khi ứng dụng kết thúc.<br>- Server ghi nhận kết nối đã đóng hoặc lỗi I/O tương ứng. |

---

## 3. QUY TRÌNH THỰC HIỆN VÀ GHI NHẬN BẰNG CHỨNG (TEST EVIDENCE)

1. **Chuẩn bị môi trường:**
   - Đảm bảo JDK 21 đã sẵn sàng.
   - Biên dịch dự án từ thư mục gốc: `mvn -f Code/pom.xml clean test-compile` (hoặc build qua IDE).
2. **Thực thi kiểm thử tự động:**
   - `PacketFramerTest` là standalone runner, không phải JUnit test; chạy từ IDE với classpath Maven sau khi biên dịch test.
   - Lệnh `mvn -f Code/pom.xml test` dùng cho các test JUnit.
   - Nếu build bị chặn bởi lỗi mã nguồn, ghi nhận ca liên quan là **BLOCKED** thay vì PASS.
3. **Thực thi Integration Test thủ công:**
   - Chạy `com.chat.server.ServerApp` và `com.chat.client.ClientApp` từ IDE có hỗ trợ Maven.
   - Mở công cụ quay màn hình (OBS Studio hoặc Xbox Game Bar `Win + G`).
   - Lần lượt thực hiện từ **TC-01** đến **TC-14**.
4. **Lưu trữ bằng chứng:**
   - Chụp ảnh màn hình các ca test thành công và lưu vào thư mục `Extra/screenshots/`.
   - Lưu video clip demo (độ dài 2-3 phút) vào `Extra/demo_phase1.mp4`.
   - Xuất file log server lưu vào `Extra/server_test.log`.
