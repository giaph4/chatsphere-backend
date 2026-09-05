package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    Optional<ConversationParticipant> findByConversationIdAndUserIdAndLeftAtIsNull(
            UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);

    /**
     * Batch-query participant còn active cho CẢ 1 trang conversation — tránh N+1 ở getMyConversations().
     */
    @Query("""
            SELECT p FROM ConversationParticipant p
            JOIN FETCH p.user
            WHERE p.conversation.id IN :conversationIds AND p.leftAt IS NULL
            """)
    List<ConversationParticipant> findActiveByConversationIds(
            @Param("conversationIds") Collection<UUID> conversationIds);

    /**
     * Ai trong hội thoại này cần được tạo thông báo cho 1 tin nhắn mới (Phase 5 mục 5.2):
     * còn active, KHÔNG phải người gửi, và KHÔNG đang tắt thông báo hội thoại (UC-27).
     *
     * <p>Trả thẳng UUID thay vì entity là chủ ý: listener chạy trên luồng nền, ngoài transaction
     * — chạm vào quan hệ LAZY ở đó sẽ ném {@code LazyInitializationException}. Lọc mute ngay
     * trong SQL cũng đảm bảo hội thoại đang mute không tốn một vòng lặp nào ở tầng Java.
     */
    @Query("""
            SELECT p.user.id FROM ConversationParticipant p
            WHERE p.conversation.id = :conversationId
              AND p.leftAt IS NULL
              AND p.user.id <> :senderId
              AND (p.mutedUntil IS NULL OR p.mutedUntil <= :now)
            """)
    List<UUID> findNotifiableUserIds(@Param("conversationId") UUID conversationId,
                                     @Param("senderId") UUID senderId,
                                     @Param("now") java.time.Instant now);

    /**
     * Sớm nhất tham gia trước — phục vụ UC-17: admin cuối cùng rời nhóm thì chuyển quyền cho người này.
     */
    List<ConversationParticipant> findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(UUID conversationId);

    long countByConversationIdAndLeftAtIsNullAndRole(UUID conversationId,
                                                     com.chatsphere.chat.domain.ParticipantRole role);
}
