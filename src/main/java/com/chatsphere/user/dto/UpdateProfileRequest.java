package com.chatsphere.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Cập nhật hồ sơ — ngữ nghĩa PUT: THAY THẾ TOÀN BỘ các field cho phép sửa.
 * <p>
 * displayName bắt buộc (không được xóa trắng tên hiển thị).
 * bio / dateOfBirth cho phép null — null nghĩa là XÓA giá trị, KHÔNG phải "giữ nguyên".
 * Client luôn phải gửi đủ 3 field (thường điền sẵn từ GET /users/me rồi cho user sửa).
 * <p>Quy ước này phải ghi rõ cho frontend: hiểu nhầm sẽ gây bug "sửa tên xong bio tự biến mất".
 */
public record UpdateProfileRequest(

        @NotBlank
        @Size(max = 100)
        String displayName,

        @Size(max = 255)
        String bio,

        @Past(message = "Ngày sinh phải ở quá khứ")
        LocalDate dateOfBirth
) {
}
