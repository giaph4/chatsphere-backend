package com.chatsphere.auth.security;

import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.WsDestinations;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chốt bảo mật của toàn bộ tầng WebSocket — tương đương {@link JwtAuthenticationFilter} của REST,
 * nhưng đặt ở kênh tin nhắn STOMP chứ không phải filter HTTP.
 *
 * <p><b>Vì sao không dùng lại được filter HTTP?</b> WebSocket chỉ có ĐÚNG MỘT request HTTP —
 * cái bắt tay (handshake) nâng cấp giao thức. Sau đó mọi frame đi trên cùng một kết nối TCP đã
 * mở, không còn HTTP header nào nữa, nên không có chỗ cho {@code Authorization} kiểu REST.
 * Hơn nữa trình duyệt KHÔNG cho phép gắn header tùy ý vào handshake của {@code new WebSocket()}.
 * Vì vậy JWT được gửi trong <b>native header của frame STOMP CONNECT</b> — frame đầu tiên client
 * gửi sau khi kết nối — và được xác thực tại đây.
 *
 * <p>Interceptor xử lý 2 loại frame:
 * <ul>
 *   <li>{@code CONNECT} — xác thực JWT, gắn {@link StompPrincipal} cho cả phiên.</li>
 *   <li>{@code SUBSCRIBE} — phân quyền theo destination. <b>Bắt buộc phải có</b>: nếu bỏ, bất kỳ
 *       ai đăng nhập hợp lệ đều có thể subscribe {@code /topic/conversation/{id}} của người lạ và
 *       đọc trộm toàn bộ tin nhắn realtime của họ. Xác thực (anh là ai) không thay được phân
 *       quyền (anh được xem gì).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String NATIVE_AUTH_HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ConversationParticipantRepository participantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            default -> {
                // SEND/UNSUBSCRIBE/DISCONNECT: danh tính đã gắn từ CONNECT, quyền gửi tin
                // do MessageService kiểm tra (phải là participant còn active).
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            throw reject("CONNECT không kèm token", ErrorCode.WEBSOCKET_UNAUTHORIZED);
        }

        try {
            Claims claims = tokenProvider.parse(token);
            UUID userId = tokenProvider.getUserId(claims);
            accessor.setUser(new StompPrincipal(userId));
            log.debug("WebSocket CONNECT hợp lệ cho user {} (session {})", userId, accessor.getSessionId());
        } catch (JwtException | IllegalArgumentException e) {
            // Khác REST: token hỏng ở REST chỉ khiến request thành "ẩn danh" rồi để
            // SecurityConfig quyết định. Ở đây phải chặn ngay — một phiên STOMP không có
            // Principal sẽ sống mãi tới khi client tự đóng, và mọi frame sau đó đều vô nghĩa.
            throw reject("Token không hợp lệ: " + e.getMessage(), ErrorCode.WEBSOCKET_UNAUTHORIZED);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        // /user/** đã tự an toàn: Spring dịch sang session của CHÍNH người đang subscribe,
        // không cách nào nghe ké queue của người khác dù có sửa destination.
        if (destination != null && destination.startsWith(WsDestinations.USER_PREFIX + "/")) {
            return;
        }

        UUID conversationId = WsDestinations.parseConversationTopic(destination);
        if (conversationId == null) {
            throw reject("Destination không được phép: " + destination,
                    ErrorCode.WEBSOCKET_SUBSCRIPTION_DENIED);
        }

        UUID userId = StompPrincipal.userIdOf(accessor.getUser());
        if (!participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw reject("User %s không phải thành viên hội thoại %s".formatted(userId, conversationId),
                    ErrorCode.WEBSOCKET_SUBSCRIPTION_DENIED);
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(NATIVE_AUTH_HEADER);
        return (header != null && header.startsWith(PREFIX)) ? header.substring(PREFIX.length()) : null;
    }

    /**
     * {@link MessageDeliveryException} là exception mà {@code StompSubProtocolHandler} hiểu:
     * nó dừng frame, gửi lại ERROR frame cho client rồi đóng phiên — client biết mình bị từ
     * chối thay vì treo im lặng chờ mãi.
     *
     * <p>Message trả cho client CỐ Ý chỉ là mô tả chung của {@link ErrorCode}; chi tiết
     * ("thiếu token" hay "sai chữ ký", "hội thoại nào") chỉ nằm trong log server.
     */
    private MessageDeliveryException reject(String reason, ErrorCode errorCode) {
        log.debug("Từ chối frame STOMP: {}", reason);
        return new MessageDeliveryException(errorCode.getDefaultMessage());
    }
}
