package com.chatsphere.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendFriendRequestRequest(

        @NotNull
        UUID receiverId
) {
}
