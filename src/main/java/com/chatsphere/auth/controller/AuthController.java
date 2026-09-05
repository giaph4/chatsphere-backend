package com.chatsphere.auth.controller;

import com.chatsphere.auth.dto.*;
import com.chatsphere.auth.service.AuthService;
import com.chatsphere.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, quản lý token và mật khẩu")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký tài khoản, gửi OTP xác thực qua email")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác thực email bằng mã OTP 6 chữ số")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.ok();
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Gửi lại mã OTP xác thực; luôn trả 200 dù email không tồn tại, "
            + "giới hạn 60 giây/lần và 3 lần/giờ")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendVerificationOtp(request);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập, trả về access token + refresh token")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ApiResponse.success(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Đổi refresh token lấy cặp token mới (token cũ bị thu hồi)")
    public ApiResponse<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ApiResponse.success(authService.refresh(request, userAgent));
    }

    @PostMapping("/logout")
    @Operation(summary = "Thu hồi refresh token của thiết bị hiện tại")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Gửi link đặt lại mật khẩu; luôn trả 200 dù email không tồn tại")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu bằng token nhận qua email")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    /**
     * Endpoint DUY NHẤT trong nhóm /auth cần đăng nhập. {@code SecurityConfig} phải có rule
     * {@code .requestMatchers(PUT, "/api/v1/auth/change-password").authenticated()} đứng TRƯỚC
     * rule {@code permitAll} của "/api/v1/auth/**", nếu không endpoint này sẽ mở toang.
     */
    @PutMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu khi đã đăng nhập")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ApiResponse.ok();
    }
}