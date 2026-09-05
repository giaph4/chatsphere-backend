package com.chatsphere.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Quan hệ chặn — CÓ HƯỚNG: blocker chặn blocked.
 * Không chuẩn hóa thứ tự như Friendship vì chiều chặn mang ý nghĩa nghiệp vụ:
 * "A chặn B" khác hẳn "B chặn A", và cả hai có thể cùng tồn tại.
 */
@Entity
@Table(name = "blocked_users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Người thực hiện hành động chặn. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false, updatable = false)
    private User blocker;

    /** Người bị chặn. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false, updatable = false)
    private User blocked;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public static BlockedUser of(User blocker, User blocked) {
        BlockedUser entity = new BlockedUser();
        entity.setBlocker(blocker);
        entity.setBlocked(blocked);
        return entity;
    }
}
