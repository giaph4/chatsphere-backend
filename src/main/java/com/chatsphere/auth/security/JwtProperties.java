package com.chatsphere.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessExpirationMs,
        long refreshExpirationMs
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret chưa được cấu hình");
        }
    }

}
