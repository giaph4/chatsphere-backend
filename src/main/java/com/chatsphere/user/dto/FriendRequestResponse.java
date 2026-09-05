package com.chatsphere.user.dto;

import com.chatsphere.user.domain.FriendRequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lời mời kết bạn. Trả cả sender và receiver để dùng chung được cho 2 màn hình:
 * "lời mời đến" (quan tâm sender) và "lời mời đã gửi" (quan tâm receiver).
 */
public record FriendRequestResponse(
        UUID id,
        UserSummaryResponse sender,
        UserSummaryResponse receiver,
        FriendRequestStatus status,
        Instant createdAt
) {
}
