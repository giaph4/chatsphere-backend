package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(

        @NotNull
        UUID userId
) {
}
