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
 * Quan hệ bạn bè đã xác nhận. Mỗi cặp chỉ tồn tại ĐÚNG 1 bản ghi,
 * với bất biến user1.id &lt; user2.id theo thứ tự UUID của PostgreSQL.
 * <p>
 * KHÔNG có updated_at → không kế thừa BaseEntity (bản ghi bất biến sau khi tạo).
 */
@Entity
@Table(name = "friendships")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id_1", nullable = false, updatable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id_2", nullable = false, updatable = false)
    private User user2;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /**
     * Factory DUY NHẤT được phép tạo Friendship — tự sắp xếp 2 user theo đúng
     * thứ tự PostgreSQL dùng cho CHECK constraint chk_friendships_order.
     * <p>
     * KHÔNG dùng UUID.compareTo(): Java so mostSignificantBits như long CÓ DẤU,
     * còn PostgreSQL so 16 byte KHÔNG DẤU → hai bên bất đồng với khoảng một nửa số UUID v4,
     * dẫn đến vi phạm CHECK constraint một cách ngẫu nhiên (test xanh 8/10 lần).
     */
    public static Friendship between(User a, User b) {
        if (a.getId().equals(b.getId())) {
            throw new IllegalArgumentException("Không thể tạo quan hệ bạn bè với chính mình");
        }
        boolean aFirst = comparePostgresOrder(a.getId(), b.getId()) < 0;

        Friendship friendship = new Friendship();
        friendship.setUser1(aFirst ? a : b);
        friendship.setUser2(aFirst ? b : a);
        return friendship;
    }

    static int comparePostgresOrder(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
