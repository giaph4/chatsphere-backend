package com.chatsphere.auth.security;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;

import java.security.Principal;
import java.util.UUID;

/**
 * Danh tính gắn vào phiên STOMP sau khi {@link WebSocketAuthInterceptor} xác thực JWT ở frame
 * {@code CONNECT}.
 *
 * <p>{@link #getName()} trả về CHÍNH userId dạng chuỗi — đây không phải chi tiết tùy tiện:
 * Spring dùng {@code Principal.getName()} làm khóa cho destination riêng người dùng, nên
 * {@code convertAndSendToUser(userId.toString(), "/queue/presence", ...)} ở tầng server và
 * {@code subscribe("/user/queue/presence")} ở tầng client khớp nhau mà không cần bảng tra cứu
 * trung gian. Đổi getName() sang email/username sẽ làm hỏng toàn bộ luồng gửi riêng.
 *
 * <p>KHÔNG dùng {@code UsernamePasswordAuthenticationToken} như luồng REST vì
 * {@code spring-security-messaging} không nằm trong dependency — không có bộ giải tham số
 * {@code @AuthenticationPrincipal} cho {@code @MessageMapping}. Controller nhận {@link Principal}
 * rồi gọi {@link #userIdOf(Principal)}.
 */
public record StompPrincipal(UUID userId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }

    /**
     * Lấy userId từ Principal của phiên STOMP.
     *
     * <p>Ném lỗi thay vì trả null: nếu principal không phải {@link StompPrincipal} nghĩa là
     * frame đã lọt qua interceptor mà chưa xác thực — trạng thái "không thể xảy ra" này phải
     * làm hỏng request một cách ồn ào, không được âm thầm xử lý tiếp với userId rỗng.
     */
    public static UUID userIdOf(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.userId();
        }
        throw new BusinessException(ErrorCode.WEBSOCKET_UNAUTHORIZED);
    }
}
