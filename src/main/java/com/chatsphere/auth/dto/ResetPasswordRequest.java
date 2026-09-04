package com.chatsphere.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(

        @NotBlank
        String token,

        @StrongPassword
        String newPassword
) {
}
