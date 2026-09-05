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
 * Thành viên của 1 conversation. KHÔNG kế thừa BaseEntity vì bảng này không có
 * updated_at — chỉ cần joined_at (bất biến) và left_at (soft leave).
 * <p>
 * "Rời nhóm" KHÔNG xóa dòng — chỉ set left_at, giữ lại lịch sử tham gia
 * (UC-17: xử lý admin cuối cùng rời nhóm cần biết ai từng ở trong nhóm).
 */
@Entity
@Table(name = "conversation_participants")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ParticipantRole role = ParticipantRole.MEMBER;

    /** Phục vụ tính unreadCount: đếm message có created_at > message này. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private Message lastReadMessage;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @CreatedDate
    @Column(name = "joined_at", updatable = false, nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    public static ConversationParticipant of(Conversation conversation, User user, ParticipantRole role) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(role);
        return participant;
    }

    public boolean isActive() {
        return leftAt == null;
    }
}
