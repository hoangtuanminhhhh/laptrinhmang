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

    // Xác thực tài khoản
    LOGIN_REQUEST,
    LOGIN_RESPONSE,

    // Nhắn tin cơ bản
    MESSAGE_SEND,
    MESSAGE_RECEIVE,

    // Trả lời tin nhắn cụ thể
    REPLY_SEND,
    REPLY_RECEIVE,

    // Chuyển tiếp tin nhắn
    FORWARD_SEND,
    FORWARD_RECEIVE,

    // Giữ kết nối (Heartbeat)
    PING,
    PONG,

    // Báo lỗi hệ thống
    ERROR_RESPONSE
}
