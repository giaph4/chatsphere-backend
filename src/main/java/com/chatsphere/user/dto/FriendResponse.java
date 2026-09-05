package com.chatsphere.user.dto;

import java.time.Instant;

/**
 * Một người bạn trong danh sách — kèm thời điểm kết bạn (friendship.createdAt).
 * <p>Bọc {@link UserSummaryResponse} thay vì trải phẳng field: mọi nơi hiển thị user
 * dùng đúng một hình dạng JSON, frontend viết 1 component dùng được ở mọi màn hình.
 */
public record FriendResponse(
        UserSummaryResponse user,
        Instant friendsSince
) {
}
