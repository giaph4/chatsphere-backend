package com.chatsphere.user.controller;

import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.PageResponse;
import com.chatsphere.user.dto.*;
import com.chatsphere.user.service.BlockService;
import com.chatsphere.user.service.UserService;
import com.chatsphere.user.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * currentUserId lấy từ {@code @AuthenticationPrincipal} — JwtAuthenticationFilter đặt UUID
 * (không phải UserPrincipal) làm principal của Authentication, giống cách AuthController
 * đã làm ở changePassword() (Phase 1). Service KHÔNG tự đọc SecurityContext (xem UserMapper),
 * nên lấy currentUserId ở tầng controller là trách nhiệm đúng chỗ.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User", description = "Hồ sơ, tìm kiếm, cài đặt riêng tư, chặn người dùng")
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final BlockService blockService;

    @GetMapping("/me")
    @Operation(summary = "Lấy hồ sơ đầy đủ của chính mình")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal UUID currentUserId) {
        return ApiResponse.success(userService.getMyProfile(currentUserId));
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ — PUT thay thế toàn bộ, null ở bio/dateOfBirth nghĩa là xóa")
    public ApiResponse<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(currentUserId, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem hồ sơ rút gọn của người khác — không lộ email")
    public ApiResponse<UserSummaryResponse> getPublicProfile(@PathVariable UUID id) {
        return ApiResponse.success(userService.getPublicProfile(id));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm user theo username/tên hiển thị, kèm quan hệ với người tìm")
    public ApiResponse<PageResponse<UserSearchResultResponse>> search(
            @AuthenticationPrincipal UUID currentUserId,
            @RequestParam @NotBlank String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(userService.search(currentUserId, q, pageable));
    }

    @GetMapping("/me/settings")
    @Operation(summary = "Lấy cài đặt riêng tư — tự tạo mặc định nếu đây là lần đọc đầu tiên")
    public ApiResponse<UserSettingsResponse> getSettings(@AuthenticationPrincipal UUID currentUserId) {
        return ApiResponse.success(userSettingsService.getSettings(currentUserId));
    }

    @PutMapping("/me/settings")
    @Operation(summary = "Cập nhật cài đặt riêng tư (online_visibility, call_permission, thông báo)")
    public ApiResponse<UserSettingsResponse> updateSettings(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(userSettingsService.updateSettings(currentUserId, request));
    }

    @PostMapping("/{id}/block")
    @Operation(summary = "Chặn người dùng — hủy luôn quan hệ bạn bè nếu đang là bạn")
    public ApiResponse<Void> block(@AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        blockService.block(currentUserId, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/block")
    @Operation(summary = "Bỏ chặn — idempotent, bỏ chặn người chưa từng chặn vẫn trả 200")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        blockService.unblock(currentUserId, id);
        return ApiResponse.ok();
    }
}
