package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    /**
     * Lấy đính kèm cho CẢ một trang tin nhắn bằng 1 query — tránh N+1 ở
     * {@code MessageService.getMessages()}, cùng cách làm với participant/unread count ở Phase 3.
     */
    @Query("""
            SELECT a FROM MessageAttachment a
            WHERE a.message.id IN :messageIds
            ORDER BY a.createdAt ASC
            """)
    List<MessageAttachment> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}
