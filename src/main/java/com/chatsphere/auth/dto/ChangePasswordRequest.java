package com.chatsphere.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank
        String oldPassword,

        @StrongPassword
        String newPassword
) {

}
