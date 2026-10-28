# BÁO CÁO BÀI TẬP LỚN MÔN HỌC: LẬP TRÌNH MẠNG
# ĐỀ TÀI: ỨNG DỤNG CHAT CLIENT–SERVER QUA GIAO THỨC TCP (UDM_08)

* **Giảng viên hướng dẫn:** ThS. Mai Ngọc Châu
* **Mã lớp học phần:** NET_262701302_11
* **Nhóm thực hiện:** Nhóm 11

---

## MỤC LỤC TỔNG QUAN

- **LỜI CẢM ƠN**
- **DANH MỤC THUẬT NGỮ VÀ TỪ VIẾT TẮT**
- **DANH MỤC HÌNH VẼ VÀ BẢNG BIỂU**
- **CHƯƠNG 1: GIỚI THIỆU ĐỀ TÀI VÀ MỤC TIÊU NGHIÊN CỨU**
  - 1.1. Bối cảnh và lý do chọn đề tài
  - 1.2. Mục tiêu kỹ thuật của đề tài
  - 1.3. Yêu cầu chức năng và phi chức năng
  - 1.4. Bảng phân công công việc các thành viên trong nhóm
- **CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ CÔNG NGHỆ ÁP DỤNG**
  - 2.1. Giao thức tầng giao vận TCP và mô hình Client–Server
  - 2.2. Vấn đề Dính gói (Sticky Packets) & Xé gói (Fragmented Packets) trong mạng TCP
  - 2.3. Lập trình mạng đa luồng với Java Socket và Thread Pool (`ExecutorService`)
  - 2.4. Công nghệ giao diện đồ họa JavaFX và cơ chế đồng bộ luồng (`Platform.runLater`)
  - 2.5. Hệ quản trị cơ sở dữ liệu nhúng SQLite và thư viện Jackson JSON
- **CHƯƠNG 3: THIẾT KẾ KIẾN TRÚC HỆ THỐNG VÀ GIAO THỨC TRUYỀN THÔNG**
  - 3.1. Kiến trúc tổng thể hệ thống (System Architecture)
  - 3.2. Thiết kế giao thức mạng đóng khung nhị phân (Length-Prefixed Framing)
  - 3.3. Định dạng dữ liệu và các loại thông điệp (Message Types)
  - 3.4. Thiết kế cơ sở dữ liệu (Database Schema: `users`, `messages`)
  - 3.5. Thiết kế luồng xử lý (Sequence Diagrams: Bắt tay, Gửi tin, Reply, Forward)
- **CHƯƠNG 4: HIỆN THỰC KỸ THUẬT VÀ CÁC MODULE CHỨC NĂNG**
  - 4.1. Module Server Core & Quản lý kết nối (TV1)
  - 4.2. Module Database & Quản lý cấu hình (TV2)
  - 4.3. Module Client Network Engine & Xử lý đa luồng (TV3)
  - 4.4. Module Giao diện người dùng JavaFX (TV4)
  - 4.5. Module UI Components: Avatar bo tròn & Bảng Emoji Unicode (TV5)
  - 4.6. Module QA, Kiểm thử tự động & Quản lý Git Flow (TV6)
- **CHƯƠNG 5: KIỂM THỬ, ĐÁNH GIÁ VÀ KẾT QUẢ THỰC NGHIỆM**
  - 5.1. Kế hoạch kiểm thử (Test Plan)
  - 5.2. Kết quả kiểm thử tự động Unit Test (`PacketFramerTest`)
  - 5.3. Kết quả kiểm thử tích hợp kịch bản mạng thực tế (Integration Test)
  - 5.4. Đánh giá hiệu năng và độ ổn định
- **CHƯƠNG 6: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN**
  - 6.1. Những kết quả đã đạt được trong Phase 1
  - 6.2. Những hạn chế tồn tại
  - 6.3. Kế hoạch phát triển trong Phase 2 (Đăng nhập, bảo mật, chat nhóm)
- **TÀI LIỆU THAM KHẢO**

---

## NỘI DUNG CHI TIẾT CÁC CHƯƠNG

### CHƯƠNG 1: GIỚI THIỆU ĐỀ TÀI VÀ MỤC TIÊU NGHIÊN CỨU

#### 1.1. Bối cảnh
Trong kỷ nguyên số, các ứng dụng trao đổi thông tin thời gian thực (Real-time Instant Messaging) đóng vai trò then chốt trong đời sống và doanh nghiệp. Việc xây dựng một ứng dụng chat qua giao thức TCP thuần giúp sinh viên nắm vững bản chất tầng mạng giao vận, cách quản lý luồng socket, giải quyết bài toán đồng bộ hóa và xử lý giao diện đồ họa.

#### 1.2. Mục tiêu kỹ thuật
- Xây dựng phần mềm chat desktop hoàn chỉnh chạy trên nền tảng Java 21 và JavaFX.
- Không sử dụng các framework web hay giao thức bậc cao có sẵn (như WebSocket, HTTP REST) nhằm làm chủ kỹ thuật socket nhị phân.
- Đạt được độ tin cậy cao: không dính gói, không xé gói, không đơ lag giao diện.

---

### CHƯƠNG 2: CƠ SỞ LÝ THUYẾT VÀ CÔNG NGHỆ ÁP DỤNG

#### 2.1. Giao thức TCP và bài toán Ranh giới gói tin
TCP là giao thức hướng luồng tin cậy (reliable stream-oriented). TCP đảm bảo dữ liệu đến đúng thứ tự và không bị mất mát nhờ cơ chế ACK và truyền lại (retransmission). Tuy nhiên, TCP xem dữ liệu truyền đi là một chuỗi các byte liên tục không biên giới.
- **Dính gói (Sticky packets):** Khi ứng dụng gửi 2 gói tin liên tiếp trong thời gian ngắn, thuật toán Nagle hoặc cơ chế buffer của TCP có thể gom chúng thành 1 segment duy nhất gửi qua mạng.
- **Xé gói (Fragmented packets):** Khi một gói tin có kích thước lớn hơn MTU (Maximum Transmission Unit, thường là 1500 bytes), router hoặc TCP stack sẽ chia nhỏ gói tin thành nhiều đợt gửi.

#### 2.2. Giải pháp Length-Prefixed Framing
Nhóm lựa chọn giải pháp chèn 4 byte kích thước (Big-Endian int) vào đầu mỗi gói tin:
$$\text{Header (4 bytes)} = \text{sizeOf}(\text{Payload UTF-8})$$
Phía nhận đọc chính xác 4 byte để biết độ dài $N$, sau đó gọi `readFully` để nạp đủ $N$ bytes vào mảng byte trước khi deserialize thành đối tượng.

---

### CHƯƠNG 3: THIẾT KẾ KIẾN TRÚC HỆ THỐNG VÀ GIAO THỨC TRUYỀN THÔNG

*(Phần này tích hợp nội dung chi tiết từ tài liệu `docs/PROTOCOL_SPEC.md` bao gồm Header 4 byte, JSON Envelope, các loại Packet Type và Database Schema SQLite).*

---

### CHƯƠNG 4: HIỆN THỰC KỸ THUẬT VÀ CÁC MODULE CHỨC NĂNG

*(Mô tả chi tiết mã nguồn, class diagram và vai trò từng thành viên từ TV1 đến TV6 theo đúng bảng phân công).*

---

### CHƯƠNG 5: KIỂM THỬ, ĐÁNH GIÁ VÀ KẾT QUẢ THỰC NGHIỆM

*(Trình bày kết quả thực thi các test cases từ `docs/TEST_PLAN_PHASE1.md`, đính kèm ảnh chụp màn hình kiểm thử thành công, biểu đồ đo đạc thời gian phản hồi Echo latency dưới 15ms trong mạng nội bộ).*

---

### CHƯƠNG 6: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN
- **Kết quả:** Đã làm chủ hoàn toàn cơ chế giao tiếp Socket TCP, giải quyết triệt để dính gói/xé gói, giao diện JavaFX hiện đại, mượt mà và trực quan.
- **Hướng tới:** Nâng cấp cơ chế mã hóa SSL/TLS, tích hợp truyền file media lớn và cuộc gọi thoại trong các giai đoạn tiếp theo.
