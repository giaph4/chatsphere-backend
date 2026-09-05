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
     * Sớm nhất tham gia trước — phục vụ UC-17: admin cuối cùng rời nhóm thì chuyển quyền cho người này.
     */
    List<ConversationParticipant> findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(UUID conversationId);

    long countByConversationIdAndLeftAtIsNullAndRole(UUID conversationId,
                                                     com.chatsphere.chat.domain.ParticipantRole role);
}
