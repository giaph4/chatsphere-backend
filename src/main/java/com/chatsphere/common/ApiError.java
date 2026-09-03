package com.chatsphere.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Phần "error" trong phong bì response — khớp {@code { "code": ..., "message": ... }} (§8.1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message) {

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.name(), errorCode.getDefaultMessage());
    }

    public static ApiError of(ErrorCode errorCode, String message) {
        return new ApiError(errorCode.name(), message);
    }
}
