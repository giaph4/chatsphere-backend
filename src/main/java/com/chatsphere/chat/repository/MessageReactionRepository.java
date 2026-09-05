package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    Optional<MessageReaction> findByMessageIdAndUserId(UUID messageId, UUID userId);

    /** Batch cho cả 1 trang tin nhắn — xem ghi chú N+1 ở {@link MessageAttachmentRepository}. */
    @Query("""
            SELECT r FROM MessageReaction r
            JOIN FETCH r.user
            WHERE r.message.id IN :messageIds
            """)
    List<MessageReaction> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}
