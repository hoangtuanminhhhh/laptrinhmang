# UDM_08 — Chat TCP Client–Server

Ứng dụng chat client–server qua giao thức TCP, toàn bộ thao tác thực hiện bằng GUI (không phải Web App).

* **Mã đề tài:** UDM_08
* **Tên đề tài:** Chat TCP Client–Server
* **Môn học:** Lập trình mạng
* **Giảng viên:** Mai Ngọc Châu
* **Mã nhóm:** NET_262701302_11

## Project Stack

- **Ngôn ngữ:** Java
- **Mạng:** Java Socket (TCP)
- **GUI:** JavaFX
- **Build:** Maven
- **Quản lý mã nguồn:** Git, GitHub

## Yêu cầu môi trường

- JDK: 
- Maven: 
- Hệ điều hành đã thử: 

## Video demo

**Link video:**

**Chức năng bắt buộc:**

- Gửi và nhận tin nhắn qua TCP
- Reply một tin nhắn cụ thể và hiển thị nội dung tin được reply
- Forward tin nhắn cho người dùng hoặc cuộc trò chuyện khác
- Hiển thị avatar trong danh sách liên hệ và khu vực tin nhắn
- Chọn, gửi và hiển thị emoji trong nội dung tin nhắn

**Trạng thái hiển thị trên GUI:** đang kết nối, đã kết nối, mất kết nối, đang gửi, đã gửi, gửi thất bại.

## Thành viên

| STT | Họ và tên | MSSV | GitHub | Vai trò / Phần phụ trách |
|---|---|---|---|---|
| 1 | ... | ... | @... |
| 2 | ... | ... | @... |
| 3 | ... | ... | @... |
| 4 | ... | ... | @... |
| 5 | ... | ... | @... |
| 6 | ... | ... | @... |



## Kiến trúc

- **Mô hình:** Client–Server
- **Giao thức tầng giao vận:** TCP
- **Server:** Lắng nghe cổng cấu hình được, mỗi client được xử lý bởi một luồng riêng (thread pool), quản lý danh sách người dùng online và định tuyến tin nhắn
- **Client:** Giao diện GUI, luồng mạng riêng để không làm treo giao diện
- **Định dạng gói tin:** 4 byte độ dài (big-endian) + nội dung JSON mã hóa UTF-8

```
 Client A ──┐                          ┌── Client C
 Client B ──┼──── TCP ────►  SERVER  ◄─┤
            │     (xác thực, kiểm tra dữ liệu, định tuyến, log)
```

### Quy trình kết nối

1. **Thiết lập:** client kết nối tới IP:port, gửi gói đăng nhập
2. **Duy trì:** trao đổi `PING`/`PONG` định kỳ, đặt timeout cho thao tác đọc
3. **Kết thúc:** client gửi `LOGOUT` hoặc server phát hiện ngắt kết nối, giải phóng socket và cập nhật danh sách online

### Các loại message chính
| type | Hướng | Mô tả |
| `LOGIN` / `LOGIN_OK` | C↔S | Đăng nhập, kèm avatar |
| `USER_LIST` | S→C | Danh sách người dùng và avatar |
| `MESSAGE` | C↔S | Tin nhắn (có `msgId`, `replyTo`, nội dung UTF-8/emoji) |
| `FORWARD` | C→S | Chuyển tiếp tin nhắn |
| `ACK` | S→C | Xác nhận đã nhận/đã gửi |
| `ERROR` | S→C | Mã lỗi và mô tả khi dữ liệu không hợp lệ |
| `PING` / `PONG` | hai chiều | Duy trì và kiểm tra kết nối |

(Đặc tả chi tiết từng trường và mã lỗi: xem `DOCX/` hoặc `Extra/protocol.md`.)

## Cấu hình


## Hướng dẫn chạy


## Cấu trúc thư mục

```
.
├── Code/        Mã nguồn (shared, server, client, tools)
├── DOCX/        Báo cáo
├── Extra/       Ảnh, log, bằng chứng kiểm thử, tài liệu bổ sung
├── PPTX/        Slide thuyết trình
├── .gitignore
└── README.md
```

## Xử lý lỗi và nhật ký (log)


## Kiểm thử


## Giới hạn của sản phẩm
