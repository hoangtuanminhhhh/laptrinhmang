package com.chat.common.protocol;

/**
 * Định nghĩa danh mục các loại gói tin truyền thông trong hệ thống UDM_08 TCP Chat.
 */
public enum PacketType {
    ECHO_REQUEST,
    ECHO_RESPONSE,
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
    FORWARD_SEND,
    FORWARD_RECEIVE,

    // Giữ kết nối (Heartbeat)
    PING,
    PONG,
    AVATAR_UPDATE,
    USER_STATUS_CHANGE,
    ERROR_RESPONSE
}
=======
public enum PacketType {
    ECHO_REQUEST,
    ECHO_RESPONSE,
    PING,
    PONG
}
>>>>>>> 8baad60d68f0886283b7ccda1ca4376fd03e9b80
