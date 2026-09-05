package com.chatsphere.user.dto;

import com.chatsphere.user.domain.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hồ sơ đầy đủ — CHỈ trả cho chính chủ qua GET /api/v1/users/me.
 * <p>Không có passwordHash, không có role (client không cần biết — phân quyền do server
 * quyết định; lộ ra chỉ tạo cám dỗ để frontend tự phân quyền), không có deletedAt.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        LocalDate dateOfBirth,
        UserStatus status,
        Instant createdAt
) {
}
