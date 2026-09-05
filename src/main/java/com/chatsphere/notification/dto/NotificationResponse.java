package com.chatsphere.notification.dto;

import com.chatsphere.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID referenceId,
        String content,
        boolean read,
        Instant createdAt
) {
}
