package com.chatsphere.chat.domain;

import com.chatsphere.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Một người thả một emoji lên một tin nhắn (UC-22).
 *
 * <p>Mỗi cặp (message, user) chỉ có ĐÚNG một dòng — thả emoji khác là cập nhật {@code emoji}
 * của dòng cũ, không tạo dòng mới (unique index {@code idx_message_reactions_user_message}).
 */
@Entity
@Table(name = "message_reactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "emoji", nullable = false, length = 10)
    private String emoji;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public static MessageReaction of(Message message, User user, String emoji) {
        MessageReaction reaction = new MessageReaction();
        reaction.setMessage(message);
        reaction.setUser(user);
        reaction.setEmoji(emoji);
        return reaction;
    }
}
