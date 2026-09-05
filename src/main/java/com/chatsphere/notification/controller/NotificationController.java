package com.chatsphere.notification.controller;

import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.PageResponse;
import com.chatsphere.notification.dto.NotificationResponse;
import com.chatsphere.notification.dto.PushSubscriptionRequest;
import com.chatsphere.notification.dto.VapidPublicKeyResponse;
import com.chatsphere.notification.service.NotificationService;
import com.chatsphere.notification.service.PushSubscriptionService;
import com.chatsphere.notification.service.VapidProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Thông báo trong ứng dụng và đăng ký Web Push (03_CODE_ROADMAP.md 5.2, 5.3).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Thông báo trong ứng dụng và Web Push (Phase 5)")
public class NotificationController {

    private final NotificationService notificationService;
    private final PushSubscriptionService pushSubscriptionService;
    private final VapidProperties vapidProperties;

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tôi — mới nhất trước")
    public ApiResponse<PageResponse<NotificationResponse>> myNotifications(
            @AuthenticationPrincipal UUID currentUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(notificationService.getMyNotifications(currentUserId, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc — cho huy hiệu trên chuông")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal UUID currentUserId) {
        return ApiResponse.success(notificationService.countUnread(currentUserId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Đánh dấu 1 thông báo đã đọc")
    public ApiResponse<Void> markAsRead(@AuthenticationPrincipal UUID currentUserId,
                                        @PathVariable UUID id) {
        notificationService.markAsRead(currentUserId, id);
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả đã đọc")
    public ApiResponse<Integer> markAllAsRead(@AuthenticationPrincipal UUID currentUserId) {
        return ApiResponse.success(notificationService.markAllAsRead(currentUserId));
    }

    // ---------- Web Push ----------

    /**
     * Khóa công khai VAPID cho {@code PushManager.subscribe()} phía trình duyệt.
     *
     * <p>Công khai khóa này là ĐÚNG theo thiết kế của Web Push — nó chỉ dùng để trình duyệt
     * xác minh chữ ký của server, không cho phép ai gửi thông báo thay ta (việc đó cần khóa bí
     * mật). {@code enabled=false} báo cho frontend biết để ẩn hẳn nút bật thông báo.
     */
    @GetMapping("/push/public-key")
    @Operation(summary = "Khóa công khai VAPID để trình duyệt đăng ký nhận push")
    public ApiResponse<VapidPublicKeyResponse> vapidPublicKey() {
        return ApiResponse.success(new VapidPublicKeyResponse(
                vapidProperties.enabled(),
                vapidProperties.enabled() ? vapidProperties.publicKey() : ""));
    }

    @PostMapping("/push/subscribe")
    @Operation(summary = "Đăng ký thiết bị nhận Web Push")
    public ApiResponse<Void> subscribe(@AuthenticationPrincipal UUID currentUserId,
                                       @Valid @RequestBody PushSubscriptionRequest request) {
        pushSubscriptionService.subscribe(currentUserId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/push/subscribe")
    @Operation(summary = "Hủy đăng ký Web Push của thiết bị hiện tại")
    public ApiResponse<Void> unsubscribe(@RequestParam String endpoint) {
        pushSubscriptionService.unsubscribe(endpoint);
        return ApiResponse.ok();
    }
}
