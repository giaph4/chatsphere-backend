package com.chatsphere.auth.security;

import com.chatsphere.common.ApiError;
import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AuthErrorResponder {

    private AuthErrorResponder() {
    }

    static void send(HttpServletResponse response, ErrorCode code, ObjectMapper objectMapper)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(ApiError.of(code)));
    }

}
