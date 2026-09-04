package com.chatsphere.auth.event;

public record PasswordResetRequestedEvent(
        String email,
        String displayName,
        String resetToken
) {
}
