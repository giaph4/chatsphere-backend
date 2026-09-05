package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Trang tin nhắn MỚI NHẤT (chưa có cursor — lần gọi đầu tiên).
     * <p>Tách riêng khỏi {@link #findPageBefore} thay vì gộp bằng {@code :cursorCreatedAt IS NULL OR ...}:
     * PostgreSQL ném "could not determine data type of parameter" khi 1 tham số CHỈ xuất hiện
     * trong vế {@code ? IS NULL} — không đủ ngữ cảnh kiểu dữ liệu để suy luận (khác lỗi ở tầng
     * Hibernate/JPQL, đây là giới hạn của trình phân tích kiểu tham số phía PostgreSQL).
     * <p>Lấy dư 1 dòng so với limit để biết còn trang sau hay không, không cần COUNT(*) riêng
     * trên bảng lớn nhất hệ thống.
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender
            WHERE m.conversation.id = :conversationId
              AND m.deletedAt IS NULL
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findFirstPage(@Param("conversationId") UUID conversationId, Pageable pageable);

    /**
     * Trang tin nhắn TRƯỚC cursor — cursor là (createdAt, id) của tin nhắn cuối trang trước,
     * so composite để ổn định thứ tự khi nhiều tin nhắn trùng createdAt (bulk insert, đồng hồ
     * hệ thống không đủ độ phân giải).
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender
            WHERE m.conversation.id = :conversationId
              AND m.deletedAt IS NULL
              AND (m.createdAt < :cursorCreatedAt
                   OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findPageBefore(@Param("conversationId") UUID conversationId,
                                  @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    /**
     * Số tin chưa đọc của {@code userId} cho CẢ 1 trang conversation cùng lúc (1 query) —
     * dùng ở ConversationService.getMyConversations(), tránh N+1 khi user có hàng trăm hội thoại.
     * Conversation không có dòng trong kết quả nghĩa là unreadCount = 0.
     */
    @Query("""
            SELECT new com.chatsphere.chat.repository.ConversationUnreadCount(p.conversation.id, COUNT(m))
            FROM ConversationParticipant p
            JOIN Message m ON m.conversation = p.conversation
                AND m.deletedAt IS NULL
                AND (p.lastReadMessage IS NULL OR m.createdAt > p.lastReadMessage.createdAt)
            WHERE p.user.id = :userId AND p.conversation.id IN :conversationIds AND p.leftAt IS NULL
            GROUP BY p.conversation.id
            """)
    List<ConversationUnreadCount> countUnreadByConversationIds(
            @Param("userId") UUID userId, @Param("conversationIds") Collection<UUID> conversationIds);
}
