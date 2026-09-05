package com.chatsphere.chat.domain;

import com.chatsphere.common.BaseEntity;
import com.chatsphere.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tin nhắn. `content` NULL khi message chỉ có attachment (Phase 5).
 * `replyToMessage`/`forwardedFromMessage` là self-reference — cùng là Message,
 * ánh xạ đúng cách reply_to_message_id/forwarded_from_message_id trỏ về messages.id.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, updatable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType type;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private Message replyToMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forwarded_from_message_id")
    private Message forwardedFromMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status = MessageStatus.SENT;

    @Column(name = "is_edited", nullable = false)
    private boolean edited = false;

    /** Soft delete phía người gửi tự xóa toàn bộ (khác message_deletions — xóa riêng từng người, Phase 5). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Message text(Conversation conversation, User sender, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setType(MessageType.TEXT);
        message.setContent(content);
        return message;
    }
}
