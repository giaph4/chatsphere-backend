package com.chatsphere.presence;

import com.chatsphere.auth.security.StompPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Nối vòng đời phiên WebSocket với module presence (03_CODE_ROADMAP.md 4.2).
 *
 * <p>Nghe {@code SessionConnectedEvent} (sau khi CONNECT đã được
 * {@code WebSocketAuthInterceptor} xác thực — nên chắc chắn có Principal) và
 * {@code SessionDisconnectEvent} (kể cả khi client đóng tab hoặc rớt mạng, không chỉ khi gửi
 * frame DISCONNECT tử tế).
 */
@Slf4j
@Component
public class WebSocketEventListener {

    /**
     * Khoảng chờ trước khi thực sự báo offline (thiết kế mục 9.1 file 01).
     *
     * <p><b>Vì sao cần debounce?</b> Mất sóng wifi vài giây, chuyển từ wifi sang 4G, hay chỉ là
     * người dùng bấm F5 — tất cả đều tạo ra một cặp disconnect/connect cách nhau 1-2 giây. Báo
     * offline ngay lập tức khiến chấm trạng thái của bạn bè nhấp nháy xanh-xám liên tục. Chờ 10
     * giây rồi kiểm tra lại: nếu người đó đã kết nối lại (phiên mới), sự kiện offline bị hủy và
     * bạn bè không thấy gì bất thường.
     */
    private static final Duration OFFLINE_DEBOUNCE = Duration.ofSeconds(10);

    private final PresenceService presenceService;
    private final PresenceBroadcaster presenceBroadcaster;
    private final ThreadPoolTaskScheduler presenceScheduler;

    /**
     * Viết constructor tay thay vì {@code @RequiredArgsConstructor}: dự án không cấu hình
     * {@code lombok.config}, nên Lombok KHÔNG chép {@code @Qualifier} từ field sang tham số
     * constructor. Thiếu qualifier, Spring có thể chọn nhầm bean {@code taskScheduler} mặc định
     * của Boot khi bean đó cũng tồn tại.
     */
    public WebSocketEventListener(PresenceService presenceService,
                                  PresenceBroadcaster presenceBroadcaster,
                                  @Qualifier("presenceScheduler") ThreadPoolTaskScheduler presenceScheduler) {
        this.presenceService = presenceService;
        this.presenceBroadcaster = presenceBroadcaster;
        this.presenceScheduler = presenceScheduler;
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        UUID userId = userIdOf(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userId == null || sessionId == null) {
            return;
        }

        boolean cameOnline = presenceService.addSession(userId, sessionId);
        log.debug("Phiên WebSocket {} mở cho user {} (phiên đầu tiên: {})", sessionId, userId, cameOnline);

        if (cameOnline) {
            presenceBroadcaster.broadcast(userId, PresenceStatus.ONLINE);
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        UUID userId = userIdOf(event.getUser());
        String sessionId = event.getSessionId();
        if (userId == null || sessionId == null) {
            return;
        }

        boolean noSessionsLeft = presenceService.removeSession(userId, sessionId);
        log.debug("Phiên WebSocket {} đóng cho user {} (hết phiên: {})", sessionId, userId, noSessionsLeft);

        if (noSessionsLeft) {
            scheduleOfflineCheck(userId);
        }
    }

    /**
     * Hẹn kiểm tra lại sau {@link #OFFLINE_DEBOUNCE}. Chỉ phát OFFLINE nếu tới lúc đó người dùng
     * VẪN không có phiên nào — nghĩa là họ đi thật, không phải chớp mạng hay F5.
     */
    private void scheduleOfflineCheck(UUID userId) {
        presenceScheduler.schedule(() -> {
            if (presenceService.isOnline(userId)) {
                log.debug("Hủy báo offline cho user {} — đã kết nối lại trong thời gian debounce", userId);
                return;
            }
            presenceBroadcaster.broadcast(userId, PresenceStatus.OFFLINE);
        }, Instant.now().plus(OFFLINE_DEBOUNCE));
    }

    /**
     * Trả null (thay vì ném lỗi) khi phiên không có Principal: cả hai sự kiện này chạy trên
     * luồng nội bộ của broker — ném lỗi ở đây chỉ làm bẩn log chứ không ai xử lý được.
     * Trường hợp duy nhất xảy ra là phiên bị đóng ngay khi CONNECT thất bại, lúc đó không có
     * gì để cập nhật cả.
     */
    private UUID userIdOf(Principal principal) {
        return principal instanceof StompPrincipal stompPrincipal ? stompPrincipal.userId() : null;
    }
}
