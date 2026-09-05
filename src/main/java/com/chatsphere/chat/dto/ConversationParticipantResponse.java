package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.ParticipantRole;
import com.chatsphere.user.dto.UserSummaryResponse;

/**
 * Thành viên rút gọn hiển thị trong {@link ConversationResponse} —
 * bọc {@link UserSummaryResponse} thay vì trải phẳng, cùng quy ước với FriendResponse (Phase 2).
 */
public record ConversationParticipantResponse(
        UserSummaryResponse user,
        ParticipantRole role
) {
}
