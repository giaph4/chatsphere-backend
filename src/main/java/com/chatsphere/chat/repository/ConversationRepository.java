package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Tìm conversation DIRECT đã tồn tại giữa 2 người — gọi TRƯỚC khi tạo mới
     * (ConversationService.getOrCreateDirectConversation) để tránh trùng lặp.
     * Join qua ConversationParticipant 2 lần (1 cho mỗi người), cả 2 phải còn active.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationParticipant p1 ON p1.conversation = c AND p1.leftAt IS NULL
            JOIN ConversationParticipant p2 ON p2.conversation = c AND p2.leftAt IS NULL
            WHERE c.type = com.chatsphere.chat.domain.ConversationType.DIRECT
              AND p1.user.id = :userId1 AND p2.user.id = :userId2
            """)
    Optional<Conversation> findDirectBetween(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    /**
     * Danh sách hội thoại của 1 user, mới nhất trước (màn hình load nhiều nhất — UC-14).
     * <p>LEFT JOIN FETCH lastMessage + sender của nó: đều là quan hệ to-one nên an toàn dùng
     * chung Pageable (không giống fetch collection — xem ghi chú ở FriendshipRepository Phase 2).
     * participants/unreadCount KHÔNG fetch ở đây — là quan hệ 1-nhiều, phải batch-query riêng
     * cho cả trang (ConversationParticipantRepository/MessageRepository) để tránh Hibernate
     * phân trang trong RAM.
     */
    @Query(value = """
            SELECT c FROM Conversation c
            JOIN ConversationParticipant p ON p.conversation = c AND p.user.id = :userId AND p.leftAt IS NULL
            LEFT JOIN FETCH c.lastMessage lm
            LEFT JOIN FETCH lm.sender
            ORDER BY c.updatedAt DESC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Conversation c
            JOIN ConversationParticipant p ON p.conversation = c AND p.user.id = :userId AND p.leftAt IS NULL
            """)
    Page<Conversation> findMyConversations(@Param("userId") UUID userId, Pageable pageable);
}
