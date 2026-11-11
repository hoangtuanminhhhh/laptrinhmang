package com.chat.common.protocol;

/**
 * Định nghĩa danh mục các loại gói tin truyền thông trong hệ thống UDM_08 TCP Chat.
 */
public enum PacketType {
    ECHO_REQUEST,
    ECHO_RESPONSE,
    CONNECT_REQUEST,
    CONNECT_RESPONSE,
    LOGIN_REQUEST,
    LOGIN_RESPONSE,
    REGISTER_REQUEST,
    REGISTER_RESPONSE,
    MESSAGE_SEND,
    MESSAGE_RECEIVE,
    CHAT_MESSAGE,
    CHAT_ACK,
    REPLY_SEND,
    REPLY_RECEIVE,
    FORWARD_SEND,
    FORWARD_RECEIVE,
    PING,
    PONG,
    AVATAR_UPDATE,
    USER_STATUS_CHANGE,
    ERROR_RESPONSE
}


