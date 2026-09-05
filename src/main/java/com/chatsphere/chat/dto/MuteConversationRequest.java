package com.chatsphere.chat.dto;

import java.time.Instant;

/**
 * Tắt thông báo hội thoại tới thời điểm {@code mutedUntil}.
 *
 * <p>{@code null} nghĩa là BẬT LẠI ngay — dùng chung một endpoint cho cả bật và tắt vì đây là
 * hai trạng thái của cùng một cài đặt, không phải hai hành động khác nhau. Muốn tắt "vĩnh viễn"
 * thì client gửi một mốc rất xa (ví dụ năm 2099); server không cần biết khái niệm đó.
 */
public record MuteConversationRequest(Instant mutedUntil) {
}
