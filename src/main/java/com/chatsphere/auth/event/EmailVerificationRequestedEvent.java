package com.chatsphere.auth.event;

public record EmailVerificationRequestedEvent(
        String email,
        String displayName,
        String otp
) {
}
