package com.chatsphere.chat.event;

import com.chatsphere.chat.dto.MessageResponse;

/**
 * Một tin nhắn vừa bị thu hồi (UC-20).
 *
 * <p>Phải phát realtime chứ không chỉ đổi DB: người kia đang mở sẵn cửa sổ chat sẽ tiếp tục
 * nhìn thấy nội dung đã thu hồi cho tới khi họ F5 — đúng thứ mà tính năng thu hồi phải ngăn.
 * Payload là {@code MessageResponse} đã có {@code status=RECALLED} và {@code content=null}.
 */
public record MessageRecalledEvent(MessageResponse message) {
}
