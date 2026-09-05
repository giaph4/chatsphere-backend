package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ngữ nghĩa PUT giống {@code UpdateProfileRequest} (Phase 2): thay thế toàn bộ.
 * `avatarUrl` cho phép null — null nghĩa là xóa ảnh nhóm (quay về mặc định), không phải giữ nguyên.
 */
public record UpdateGroupRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String avatarUrl
) {
}
