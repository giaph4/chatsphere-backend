package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.MessageStatus;
import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.user.dto.UserSummaryResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * `replyToMessageId`/`forwardedFromMessageId` chỉ trả ID, KHÔNG trả nguyên message cha —
 * tránh JSON lồng sâu tùy ý khi forward-chain hoặc reply-chain dài; client tự gọi
 * lại danh sách tin nhắn đã load trong bộ nhớ để hiển thị preview.
 */
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UserSummaryResponse sender,
        MessageType type,
        String content,
        UUID replyToMessageId,
        UUID forwardedFromMessageId,
        MessageStatus status,
        boolean edited,
        Instant createdAt
) {
}
