package com.chatsphere.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @StrongPassword
        String password,

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username chỉ gồm chữ, số, gạch dưới")
        String username,

        @NotBlank @Size(max = 100)
        String displayName
) {
}