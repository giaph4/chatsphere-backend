package com.chatsphere.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Chính sách CORS cho tầng REST ({@code /api/**}).
 *
 * <p>Bean {@link CorsConfigurationSource} (tên mặc định "corsConfigurationSource") sẽ được
 * SecurityConfig ở Phase 1.2 kích hoạt qua {@code http.cors(Customizer.withDefaults())}.
 * WebSocket/SockJS ({@code /ws}) có cấu hình origin riêng ở WebSocketConfig (Phase 4).
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));         // mọi request header: Authorization, Content-Type...
        cfg.setExposedHeaders(List.of("Location"));  // header response JS phía FE được phép đọc (vd sau POST 201)
        cfg.setAllowCredentials(true);               // xem ghi chú
        cfg.setMaxAge(3600L);                        // cache preflight OPTIONS 1 giờ -> đỡ round-trip

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}