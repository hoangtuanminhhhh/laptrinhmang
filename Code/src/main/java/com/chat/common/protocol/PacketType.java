package com.chat.common.protocol;

/**
 * Định nghĩa danh mục các loại gói tin truyền thông trong hệ thống UDM_08 TCP Chat.
 */
public enum PacketType {
    // Phase 1: Echo & Connectivity
    ECHO_REQUEST,
    ECHO_RESPONSE,

    // Phiên làm việc & Bắt tay
    CONNECT_REQUEST,
    CONNECT_RESPONSE,

    // Xác thực & Tài khoản
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    REGISTER_REQUEST,
    REGISTER_RESPONSE,
    LOGOUT_REQUEST,
    LOGOUT_RESPONSE,

    // Nhắn tin cơ bản & Xác nhận ACK
    MESSAGE_SEND,
    MESSAGE_RECEIVE,
    MESSAGE_ACK,

    // Trả lời tin nhắn cụ thể
    REPLY_SEND,
    REPLY_RECEIVE,

    // Chuyển tiếp tin nhắn
    FORWARD_SEND,
    FORWARD_RECEIVE,

    // Ảnh đại diện & Trạng thái người dùng
    AVATAR_UPDATE,
    AVATAR_RESPONSE,
    USER_LIST,
    USER_STATUS,

    // Giữ kết nối (Heartbeat)
    PING,
    PONG,

    // Báo lỗi hệ thống
    ERROR_RESPONSE
}
