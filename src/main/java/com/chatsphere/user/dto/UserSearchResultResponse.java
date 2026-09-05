package com.chatsphere.user.dto;

/**
 * Kết quả tìm kiếm = thông tin rút gọn + quan hệ với người đang tìm.
 * <p>Trả kèm quan hệ để frontend không phải gọi thêm N request phụ chỉ để biết
 * nên hiện nút "Kết bạn" hay "Nhắn tin" cho từng dòng.
 */
public record UserSearchResultResponse(
        UserSummaryResponse user,
        RelationshipStatus relationship
) {
}
