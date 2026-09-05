package com.chatsphere.user.dto;

import com.chatsphere.user.domain.PrivacyLevel;

public record UserSettingsResponse(
        PrivacyLevel onlineVisibility,
        PrivacyLevel callPermission,
        boolean notificationEnabled
) {
}
