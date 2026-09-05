package com.chatsphere.chat.domain;

import com.chatsphere.common.BaseEntity;
import com.chatsphere.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuộc trò chuyện — DIRECT (1-1) hoặc GROUP.
 * `name`/`avatarUrl` chỉ có ý nghĩa với GROUP (NULL với DIRECT, tên hiển thị
 * DIRECT được suy ra ở tầng Service từ thông tin user còn lại).
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ConversationType type;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /**
     * Denormalize tin nhắn mới nhất — tránh phải SELECT ... ORDER BY created_at
     * DESC LIMIT 1 cho từng conversation mỗi lần load danh sách hội thoại
     * (màn hình được load nhiều nhất trong app). Đánh đổi: phải tự cập nhật
     * field này ở MessageService.sendMessage().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private Message lastMessage;

    public static Conversation direct(User creator) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(creator);
        return conversation;
    }

    public static Conversation group(String name, User creator) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setName(name);
        conversation.setCreatedBy(creator);
        return conversation;
    }
}
