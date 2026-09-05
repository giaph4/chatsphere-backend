package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * `lastMessage` (nullable — conversation vừa tạo chưa có tin nhắn nào) và `unreadCount`
 * do Service tự tính cho user đang gọi API (so `lastReadMessage` của chính participant đó
 * với tin nhắn mới nhất) — KHÔNG có sẵn trực tiếp trên entity Conversation nên Mapper
 * nhận 2 giá trị này như tham số rời, không tự suy ra.
 */
public record ConversationResponse(
        UUID id,
        ConversationType type,
        String name,
        String avatarUrl,
        MessageResponse lastMessage,
        long unreadCount,
        List<ConversationParticipantResponse> participants,
        Instant updatedAt
) {
}
