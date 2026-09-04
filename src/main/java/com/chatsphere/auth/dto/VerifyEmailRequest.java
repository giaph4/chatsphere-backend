package com.chatsphere.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(

        @NotBlank @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Mã xác thực gồm 6 chữ số")
        String otp
) {
}
