package com.chatsphere.user.dto;

import java.util.UUID;

/**
 * Thông tin user hiển thị cho NGƯỜI KHÁC — cố ý KHÔNG có email, dateOfBirth.
 * <p>Tách riêng khỏi {@link UserProfileResponse} để việc lộ email trở thành lỗi COMPILE
 * (record này không có chỗ chứa email) thay vì lỗi runtime khi ai đó quên lọc.
 * <p>Dùng lại ở: kết quả tìm kiếm, danh sách bạn bè, người gửi lời mời,
 * và (Phase 3) danh sách thành viên hội thoại.
 */
public record UserSummaryResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        String bio
) {
}
