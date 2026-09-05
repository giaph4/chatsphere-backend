package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.MessageStatus;
import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.user.dto.UserSummaryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * `replyToMessageId`/`forwardedFromMessageId` chỉ trả ID, KHÔNG trả nguyên message cha —
 * tránh JSON lồng sâu tùy ý khi forward-chain hoặc reply-chain dài; client tự gọi
 * lại danh sách tin nhắn đã load trong bộ nhớ để hiển thị preview.
 * <p>
 * Phase 5 thêm `attachments` và `reactions`: hai danh sách này KHÔNG nằm sẵn trên entity
 * Message mà do Service nạp theo lô rồi truyền vào mapper (xem {@code ConversationMapper}) —
 * cùng nguyên tắc mapper stateless đã áp dụng cho `ConversationResponse` ở Phase 3, để mapper
 * không tự ý bắn thêm query gây N+1. Cả hai luôn là danh sách rỗng chứ không bao giờ null,
 * client không phải kiểm tra null trước khi duyệt.
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
        List<AttachmentResponse> attachments,
        List<ReactionResponse> reactions,
        Instant createdAt
) {
}
