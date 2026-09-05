package com.chatsphere.chat.repository;

import java.util.UUID;

/**
 * Kết quả chiếu (projection) của {@link MessageRepository#countUnreadByConversationIds}
 * qua JPQL constructor expression {@code SELECT new ...ConversationUnreadCount(...)}.
 * Chỉ conversation có tin chưa đọc mới xuất hiện trong kết quả — thiếu = 0.
 */
public record ConversationUnreadCount(UUID conversationId, long unreadCount) {
}
