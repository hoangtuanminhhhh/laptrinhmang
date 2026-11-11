# Kế hoạch kiểm thử Phase 2 — Tuần 3 & 4

**Dự án:** UDM_08 — Chat TCP Client–Server  
**Phạm vi:** lỗi đầu vào, dữ liệu JSON phức tạp và ngắt kết nối trong khi truyền dữ liệu  


## 1. Mục tiêu

- Kiểm tra tính toàn vẹn của gói JSON có payload lồng nhau, thông tin reply/forward, danh sách tệp và Unicode.
- Xác nhận tầng framing phát hiện kết nối bị đóng giữa header hoặc payload thay vì chấp nhận gói không hoàn chỉnh.
- Định nghĩa các ca kiểm thử thủ công cho đăng ký trùng tên, tin nhắn dài, emoji liên tục và server/client mất kết nối.
- Ghi nhận kết quả và bằng chứng thực tế; không coi ca kiểm thử thủ công là đạt nếu chưa chạy và lưu bằng chứng.

## 2. Môi trường và điều kiện

- JDK 21, Maven và dự án trong `Code/`.
- Kiểm thử tự động dùng JUnit 5 đã khai báo trong `Code/pom.xml`.
- Ca kiểm thử giao diện cần một server và client chạy riêng; dùng tài khoản dữ liệu thử, không dùng dữ liệu thật.
- Chạy kiểm thử tự động:

  ```powershell
  mvn -f Code/pom.xml "-Dtest=PacketSerializationTest,DisconnectSimulationTest" test
  ```

## 3. Ma trận kiểm thử

| Mã | Loại | Kịch bản / thao tác | Kết quả mong đợi |
|---|---|---|---|
| P2-01 | Tự động | Đóng gói rồi đọc lại Packet có message lồng nhau, reply, forward, attachment và ký tự tiếng Việt/emoji. | Các trường envelope, giá trị lồng nhau và Unicode được giữ nguyên. |
| P2-02 | Tự động | Đóng socket khi header mới nhận được một phần. | Đọc frame báo `EOFException`, không trả về một frame hợp lệ giả. |
| P2-03 | Tự động | Đóng socket sau header nhưng trước khi nhận đủ payload. | Đọc frame báo `EOFException`, không trả về payload thiếu. |
| P2-04 | Thủ công | Đăng ký hai lần cùng username sau khi luồng đăng ký được tích hợp vào UI/server. | Lần đăng ký trùng bị từ chối và thông báo phù hợp; hiện tại chưa có luồng đăng ký qua giao thức để chạy end-to-end. |
| P2-05 | Thủ công | Gửi tin nhắn 10.000 ký tự, sau đó thử payload vượt giới hạn giao thức. | Ghi nhận kết quả khi chức năng gửi tin được tích hợp; hiện Server chỉ xử lý Echo. |
| P2-06 | Thủ công | Gửi liên tiếp nhiều emoji (bao gồm emoji ngoài BMP) và kiểm tra trên hai client. | Ghi nhận kết quả khi chuyển tin giữa hai client được hỗ trợ; hiện chưa có định tuyến chat giữa client. |
| P2-07 | Thủ công | Đóng client đột ngột khi đang chat. | Xác nhận server giải phóng kết nối; hiện có thể kiểm tra kết nối TCP, nhưng chưa có phiên chat end-to-end. |
| P2-08 | Thủ công | Dừng server đột ngột khi client đang kết nối. | Client hiển thị sự kiện ngắt kết nối/lỗi; thời gian phát hiện và thao tác gửi sau đó cần được đo thực tế. |
| P2-09 | Thủ công | Tắt/bật lại kết nối mạng hoặc server rồi thử kết nối lại. | Ghi nhận kết quả thực tế; không giả định có auto-reconnect vì chưa thấy cơ chế này trong source hiện tại. |
| P2-10 | Thủ công | Thực hiện reply/forward và kiểm tra nội dung tại bên nhận sau khi chức năng được tích hợp. | Chưa thể chạy end-to-end với server hiện tại; khi tích hợp, trích dẫn và nhãn nguồn forward phải khớp tin nhắn đã chọn. |

## 4. Quy trình và bằng chứng

1. Chạy P2-01 đến P2-03 bằng lệnh Maven ở trên và lưu nguyên văn kết quả.
2. Chạy các ca P2-04 đến P2-10 trên server/client thực tế; ghi phiên bản commit, thời điểm, điều kiện và kết quả từng ca.
3. Quay video ngắn cho các ca ngắt kết nối theo yêu cầu phân công; lưu ở thư mục nộp bài của nhóm, không đưa dữ liệu cá nhân vào video.
4. Chỉ đánh dấu **PASS** khi có kết quả quan sát được. Ghi **BLOCKED** nếu không thể chạy vì build, môi trường hoặc chức năng chưa sẵn sàng; nêu rõ lý do.

## 5. Kết quả thực hiện

Lệnh kiểm thử tự động hiện bị chặn ở bước biên dịch do các dấu conflict Git còn trong `Packet.java`, `PacketFramer.java` và `PacketType.java`; chưa có kết quả PASS cho P2-01 đến P2-03. Các ca thủ công cần được chạy lại sau khi blocker build được xử lý và các chức năng phụ thuộc được tích hợp.
