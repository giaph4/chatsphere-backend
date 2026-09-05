package com.chatsphere.chat.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Biên nhận đã đọc phát tới {@code /topic/conversation/{id}} — để người gửi thấy "Đã xem".
 *
 * <p>Phát cho cả nhóm chứ không chỉ người gửi tin cuối: trong group chat, hiển thị đúng cần
 * biết TỪNG người đã đọc tới đâu, mà server không nên đoán hộ client cần gì.
 */
public record ReadReceiptEvent(
        UUID conversationId,
        UUID userId,
        UUID lastReadMessageId,
        Instant readAt
) {
}
