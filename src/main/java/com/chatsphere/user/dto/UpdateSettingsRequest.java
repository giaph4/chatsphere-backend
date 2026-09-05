package com.chatsphere.user.dto;

import com.chatsphere.user.domain.PrivacyLevel;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /users/me/settings — thay thế toàn bộ, mọi field bắt buộc.
 * <p>notificationEnabled dùng {@link Boolean} (wrapper) chứ KHÔNG dùng boolean primitive:
 * với primitive, client thiếu field sẽ được Jackson gán false âm thầm và @NotNull không
 * bao giờ kích hoạt → user bị tắt thông báo mà không hiểu vì sao.
 * Quy tắc: request dùng wrapper để phát hiện thiếu, response dùng primitive.
 */
public record UpdateSettingsRequest(

        @NotNull
        PrivacyLevel onlineVisibility,

        @NotNull
        PrivacyLevel callPermission,

        @NotNull
        Boolean notificationEnabled
) {
}
