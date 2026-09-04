package com.chatsphere.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
