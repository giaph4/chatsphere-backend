package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Người tạo nhóm KHÔNG có trong `memberIds` — Service tự thêm người gọi API vào nhóm
 * với role ADMIN (không để client tự khai báo ai là admin ban đầu).
 */
public record CreateGroupRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotEmpty(message = "Nhóm cần ít nhất 1 thành viên khác ngoài người tạo")
        List<UUID> memberIds
) {
}
