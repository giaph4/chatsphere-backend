package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Hội thoại ĐÍCH để chuyển tiếp tới; tin nhắn nguồn nằm ở path variable. */
public record ForwardMessageRequest(

        @NotNull
        UUID targetConversationId
) {
}
