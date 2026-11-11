# Báo cáo tiến độ giữa kỳ

## 1. Thông tin đề tài

- **Đề tài:** Ứng dụng chat Client–Server qua TCP (UDM_08)
- **Nhóm:** Nhóm 11
- **Thời điểm:** Tuần 3–4
- **Phạm vi báo cáo:** Tiến độ dựa trên nội dung hiện có trong nhánh làm việc; chưa thay thế báo cáo kết quả chạy thực tế.

## 2. Mục tiêu

Xây dựng ứng dụng chat desktop bằng Java/JavaFX, trao đổi dữ liệu qua TCP, đóng khung gói tin để xử lý ranh giới stream và hỗ trợ các chức năng chat được phân công cho nhóm.

## 3. Tiến độ ghi nhận trong mã nguồn

- Có các thành phần giao thức `Packet`, `PacketFramer` và `PacketType`; thiết kế đang hướng tới JSON envelope và framing có header độ dài.
- Có các thành phần server `ChatServer`/`ClientHandler` và client `NetworkService`.
- Có các lớp model `User`/`Message`, thành phần cơ sở dữ liệu/xác thực và tài nguyên JavaFX.
- Đã có kế hoạch kiểm thử Phase 1 và bài kiểm thử framing `PacketFramerTest`. Báo cáo này bổ sung kế hoạch Phase 2 và kiểm thử serialization/ngắt kết nối.

Việc có mặt của lớp hoặc tài nguyên trong repository không được xem là bằng chứng rằng chức năng đã chạy thành công.

## 4. QA và tình trạng kiểm chứng

Các bài kiểm thử Phase 2 tập trung vào round-trip JSON lồng nhau, Unicode, reply/forward metadata, attachment và đóng kết nối giữa header/payload. Các tình huống đăng ký trùng, tin nhắn dài, spam emoji và server/client mất kết nối cần kiểm thử thủ công trên ứng dụng tích hợp.

Tại thời điểm lập báo cáo, chưa có kết quả chạy hoặc video bằng chứng được ghi nhận. Mã nguồn giao thức hiện có các dấu phân định conflict Git còn sót trong `Packet.java`, `PacketFramer.java` và `PacketType.java`; vì vậy cần xử lý blocker tích hợp trong phạm vi phù hợp và chạy lại build/test trước khi kết luận ứng dụng hoạt động.

## 5. Rủi ro và công việc tiếp theo

1. Giải quyết conflict trong các file giao thức theo quyết định của người phụ trách các file đó; không tự ý thay đổi ngoài phạm vi thành viên 6.
2. Chạy build và các bài test tự động Phase 1/Phase 2, lưu log kết quả.
3. Thực hiện các ca kiểm thử thủ công khi client/server có thể chạy; quay video các tình huống mất kết nối.
4. Cập nhật báo cáo bằng kết quả và bằng chứng đã xác minh, phân biệt rõ PASS, FAIL và BLOCKED.

## 6. Kết luận

Các tài liệu và ca kiểm thử Phase 2 đã được chuẩn bị. Chưa đủ căn cứ để xác nhận toàn bộ chức năng hoặc khả năng chịu lỗi đạt yêu cầu cho đến khi blocker build được xử lý và các kiểm thử tích hợp được thực hiện.
