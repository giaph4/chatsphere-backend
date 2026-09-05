package com.chatsphere.chat.event;

import com.chatsphere.chat.dto.ReadReceiptEvent;

/**
 * Một thành viên vừa dời con trỏ "đã đọc" tiến lên (UC-24).
 *
 * <p>Chỉ phát khi con trỏ THỰC SỰ tiến — xem {@code MessageService.markRead()}. Client hay gửi
 * lặp mỗi lần cuộn; phát lại biên nhận y hệt chỉ tốn băng thông và làm UI nhấp nháy.
 */
public record MessageReadEvent(ReadReceiptEvent receipt) {
}
