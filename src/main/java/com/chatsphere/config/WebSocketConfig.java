package com.chatsphere.config;

import com.chatsphere.auth.security.WebSocketAuthInterceptor;
import com.chatsphere.common.WsDestinations;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình STOMP over WebSocket (03_CODE_ROADMAP.md 4.1).
 *
 * <p><b>Simple broker (trong bộ nhớ JVM) — biết trước giới hạn:</b> mọi phiên và đăng ký topic
 * nằm trong RAM của đúng tiến trình này. Chạy 2 instance sau load balancer thì người dùng nối
 * vào instance A sẽ KHÔNG nhận được tin do instance B phát. Muốn scale ngang phải đổi sang
 * broker ngoài ({@code enableStompBrokerRelay} + RabbitMQ/ActiveMQ) — xem
 * 04_PRODUCTION_DEPLOYMENT.md. Ở phạm vi học tập 1 instance, simple broker là lựa chọn đúng:
 * không thêm hạ tầng, không thêm điểm hỏng.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CorsProperties corsProperties;

    /**
     * Đăng ký {@code /ws} HAI lần là cố ý, không phải nhầm:
     * <ul>
     *   <li>Lần 1 — WebSocket thuần: client dùng được {@code new WebSocket()} / thư viện STOMP
     *       gốc, không tốn thêm round-trip nào.</li>
     *   <li>Lần 2 — {@code withSockJS()}: đường lui khi WebSocket bị chặn (một số proxy công ty,
     *       mạng lọc gói). SockJS tự hạ cấp xuống HTTP long-polling, endpoint thật nằm dưới
     *       {@code /ws/**} nên không đụng nhau.</li>
     * </ul>
     *
     * <p>{@code setAllowedOrigins} bắt buộc phải khai riêng: handshake WebSocket KHÔNG đi qua
     * {@code CorsConfig} (bean đó chỉ đăng ký cho {@code /api/**}). Bỏ trống thì Spring chặn
     * mọi origin khác — frontend dev ở cổng 5173 sẽ không nối được.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = corsProperties.allowedOrigins().toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(WsDestinations.TOPIC_PREFIX, WsDestinations.QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(WsDestinations.APP_PREFIX);
        registry.setUserDestinationPrefix(WsDestinations.USER_PREFIX);
    }

    /**
     * Kênh INBOUND (client → server) là nơi duy nhất nhìn thấy frame CONNECT, nên interceptor
     * xác thực phải gắn ở đây chứ không phải outbound.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
