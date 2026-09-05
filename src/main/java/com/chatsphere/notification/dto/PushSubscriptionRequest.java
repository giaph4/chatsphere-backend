package com.chatsphere.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đúng những gì {@code PushManager.subscribe()} trả về phía trình duyệt, phẳng hóa một cấp:
 * {@code subscription.endpoint}, {@code subscription.keys.p256dh}, {@code subscription.keys.auth}.
 */
public record PushSubscriptionRequest(

        @NotBlank
        @Size(max = 500)
        String endpoint,

        @NotBlank
        @Size(max = 255)
        String p256dhKey,

        @NotBlank
        @Size(max = 255)
        String authKey
) {
}
