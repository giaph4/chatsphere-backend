package com.chatsphere.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Ánh xạ nhóm cấu hình {@code app.cors.*} trong application.yaml.
 * Đặt nhiều origin bằng cách phân tách dấu phẩy: CORS_ALLOWED_ORIGINS=<a href="http://localhost:5173,http://localhost:3000">...</a>
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:5173");
        }
    }
}
