package com.chatsphere.presence;

import com.chatsphere.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Ảnh chụp trạng thái presence tại thời điểm hiện tại, qua REST.
 *
 * <p>WebSocket chỉ phát THAY ĐỔI trạng thái. Client vừa mở trang mà chỉ nghe WebSocket sẽ không
 * biết ai đang online cho tới khi có người thứ nhất đổi trạng thái — có thể là vài phút sau.
 * Mẫu chuẩn cho mọi dữ liệu realtime: gọi REST một lần lấy trạng thái nền, rồi để WebSocket
 * cập nhật dần từ đó.
 */
@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
@Tag(name = "Presence", description = "Trạng thái online của bạn bè (Phase 4)")
public class PresenceController {

    private final PresenceService presenceService;
    private final PresenceBroadcaster presenceBroadcaster;

    @GetMapping("/friends")
    @Operation(summary = "Danh sách id bạn bè đang online — gọi 1 lần khi mở app")
    public ApiResponse<Set<UUID>> onlineFriends(@AuthenticationPrincipal UUID currentUserId) {
        return ApiResponse.success(presenceBroadcaster.onlineFriendsOf(currentUserId));
    }

    /**
     * Trả {@code false} thay vì lỗi 403 khi không đủ quyền xem: phân biệt "đang offline" với
     * "không cho bạn xem" chính là thứ mà cài đặt riêng tư cố tình che đi (cùng nguyên tắc với
     * mã lỗi {@code USER_BLOCKED} dùng chung cho cả 2 chiều chặn ở Phase 2).
     */
    @GetMapping("/{userId}")
    @Operation(summary = "1 người có đang online không — tôn trọng cài đặt online_visibility")
    public ApiResponse<Boolean> isOnline(@AuthenticationPrincipal UUID currentUserId,
                                         @PathVariable UUID userId) {
        boolean visible = presenceBroadcaster.canSee(currentUserId, userId);
        return ApiResponse.success(visible && presenceService.isOnline(userId));
    }
}
