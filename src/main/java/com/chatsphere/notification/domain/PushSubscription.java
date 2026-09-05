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
 * Một thiết bị/trình duyệt đã cho phép nhận Web Push (UC-23).
 *
 * <p>Ba trường {@code endpoint}, {@code p256dhKey}, {@code authKey} là nguyên văn những gì
 * {@code PushManager.subscribe()} trả về phía trình duyệt. Hai khóa kia dùng để MÃ HÓA nội dung
 * thông báo: dịch vụ đẩy trung gian (FCM của Google, Mozilla autopush...) chuyển tiếp gói tin
 * mà không đọc được nội dung — đó là lý do phải lưu khóa chứ không chỉ lưu địa chỉ endpoint.
 */
@Entity
@Table(name = "push_subscriptions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Định danh duy nhất của thiết bị, do trình duyệt cấp. */
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public static PushSubscription of(User user, String endpoint, String p256dhKey, String authKey) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setP256dhKey(p256dhKey);
        subscription.setAuthKey(authKey);
        return subscription;
    }
}
