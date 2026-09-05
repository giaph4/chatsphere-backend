package com.chatsphere.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sổ ghi phiên WebSocket đang mở của từng người, lưu trong Redis (03_CODE_ROADMAP.md 4.2).
 *
 * <p><b>Vì sao là Set sessionId chứ không phải một cờ boolean "online"?</b> Một người thường mở
 * nhiều tab và nhiều thiết bị cùng lúc. Với cờ boolean, đóng MỘT tab sẽ đặt cờ về offline dù
 * điện thoại vẫn đang kết nối — bạn bè thấy người đó "offline" trong khi họ vẫn đang chat.
 * Đếm số phiên thì "offline" chỉ xảy ra khi phiên CUỐI CÙNG đóng, đúng nghĩa thực tế.
 *
 * <p><b>Vì sao Redis chứ không phải một {@code Map} tĩnh trong JVM?</b> Dữ liệu này tự hết hạn,
 * mất đi cũng không sao (khởi động lại thì client tự kết nối lại) — đúng loại state hợp với
 * Redis, giống cách {@code AuthTokenStore} dùng ở Phase 1. Quan trọng hơn: đặt ở Redis thì khi
 * scale lên nhiều instance, mọi instance vẫn nhìn thấy cùng một bức tranh presence.
 *
 * <p>Class này CỐ Ý không biết gì về bạn bè, quyền riêng tư hay WebSocket — chỉ là bộ đếm phiên.
 * Việc "ai được thấy ai" thuộc về {@link PresenceBroadcaster}.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String KEY_SESSIONS = "presence:sessions:";

    /**
     * Lưới an toàn chống rác: nếu tiến trình chết đột ngột (kill -9, mất điện), sự kiện
     * DISCONNECT không bao giờ chạy và key sẽ nằm lại vĩnh viễn — người đó "online" mãi mãi.
     * TTL được gia hạn mỗi lần có phiên mới, nên phiên đang sống thật không bao giờ bị hết hạn oan.
     */
    private static final Duration SESSION_KEY_TTL = Duration.ofHours(12);

    private final StringRedisTemplate redis;

    /**
     * Ghi nhận 1 phiên WebSocket vừa mở.
     *
     * @return true nếu đây là phiên ĐẦU TIÊN của người này (vừa chuyển từ offline sang online)
     */
    public boolean addSession(UUID userId, String sessionId) {
        String key = KEY_SESSIONS + userId;
        Long sizeAfterAdd = redis.opsForSet().add(key, sessionId);
        redis.expire(key, SESSION_KEY_TTL);

        Long total = redis.opsForSet().size(key);
        // SADD trả về SỐ PHẦN TỬ MỚI THÊM (0 hoặc 1), không phải tổng — phải hỏi SCARD riêng.
        return sizeAfterAdd != null && sizeAfterAdd > 0 && total != null && total == 1L;
    }

    /**
     * Gỡ 1 phiên vừa đóng.
     *
     * @return true nếu người này KHÔNG còn phiên nào (ứng viên chuyển sang offline —
     *         quyết định cuối cùng còn phải qua bước debounce ở {@code WebSocketEventListener})
     */
    public boolean removeSession(UUID userId, String sessionId) {
        String key = KEY_SESSIONS + userId;
        redis.opsForSet().remove(key, sessionId);

        Long remaining = redis.opsForSet().size(key);
        return remaining == null || remaining == 0L;
    }

    public boolean isOnline(UUID userId) {
        Long sessions = redis.opsForSet().size(KEY_SESSIONS + userId);
        return sessions != null && sessions > 0;
    }

    /**
     * Lọc ra những người đang online trong một danh sách.
     *
     * <p>Hiện gọi SCARD lần lượt cho từng người: với danh sách bạn bè cỡ vài chục thì chi phí
     * không đáng kể so với một round-trip mạng. Nếu Phase 8 đo thấy chậm (người dùng có hàng
     * trăm bạn), gộp lại bằng pipeline là tối ưu đầu tiên nên làm.
     */
    public Set<UUID> filterOnline(Collection<UUID> userIds) {
        Set<UUID> online = new LinkedHashSet<>();
        for (UUID userId : userIds) {
            if (isOnline(userId)) {
                online.add(userId);
            }
        }
        return online;
    }
}
