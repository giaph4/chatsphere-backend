package com.chatsphere.presence;

import java.time.Instant;
import java.util.UUID;

/**
 * Trạng thái online/offline của 1 người, gửi tới {@code /user/{friendId}/queue/presence}.
 *
 * <p>{@code at} là thời điểm đổi trạng thái — với OFFLINE nó chính là "lần cuối truy cập" mà
 * client hiển thị ("Hoạt động 5 phút trước") mà không cần gọi thêm API nào.
 */
public record PresenceEvent(
        UUID userId,
        PresenceStatus status,
        Instant at
) {

    public static PresenceEvent online(UUID userId) {
        return new PresenceEvent(userId, PresenceStatus.ONLINE, Instant.now());
    }

    public static PresenceEvent offline(UUID userId) {
        return new PresenceEvent(userId, PresenceStatus.OFFLINE, Instant.now());
    }
}
