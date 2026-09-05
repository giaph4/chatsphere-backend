package com.chatsphere.notification.domain;

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
 * Thông báo trong ứng dụng (UC-26).
 *
 * <p>{@code referenceId} cố ý KHÔNG phải quan hệ JPA: nó trỏ tới bảng nào là tùy {@code type}
 * (message, friend request, call session). Một bảng thông báo dùng chung cho mọi loại sự kiện
 * đổi lại việc mất kiểm tra toàn vẹn ở tầng DB — xem ghi chú trong migration V14.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public static Notification of(User user, NotificationType type, UUID referenceId, String content) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setContent(content);
        return notification;
    }
}
