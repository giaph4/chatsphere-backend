package com.chatsphere.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @MapsId // báo JPA: khóa chính của entity này chính là khóa ngoại user_id
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_visibility", nullable = false, length = 20)
    private PrivacyLevel onlineVisibility = PrivacyLevel.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_permission", nullable = false, length = 20)
    private PrivacyLevel callPermission = PrivacyLevel.EVERYONE;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = true;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserSettings defaultsFor(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        return settings;
    }
}