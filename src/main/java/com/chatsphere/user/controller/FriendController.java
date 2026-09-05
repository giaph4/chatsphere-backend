package com.chatsphere.user.controller;

import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.PageResponse;
import com.chatsphere.user.dto.FriendRequestResponse;
import com.chatsphere.user.dto.FriendResponse;
import com.chatsphere.user.dto.SendFriendRequestRequest;
import com.chatsphere.user.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Friend", description = "Lời mời kết bạn và danh sách bạn bè")
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/api/v1/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi lời mời kết bạn — tự động accept nếu bên kia đã gửi lời mời chéo")
    public ApiResponse<FriendRequestResponse> sendRequest(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody SendFriendRequestRequest request) {
        return ApiResponse.success(friendService.sendRequest(currentUserId, request.receiverId()));
    }

    @PutMapping("/api/v1/friend-requests/{id}/accept")
    @Operation(summary = "Chấp nhận lời mời — chỉ người NHẬN mới được gọi")
    public ApiResponse<FriendRequestResponse> accept(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        return ApiResponse.success(friendService.acceptRequest(currentUserId, id));
    }

    @PutMapping("/api/v1/friend-requests/{id}/reject")
    @Operation(summary = "Từ chối lời mời — chỉ người NHẬN mới được gọi")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        friendService.rejectRequest(currentUserId, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/v1/friend-requests/{id}")
    @Operation(summary = "Thu hồi lời mời đã gửi — chỉ người GỬI mới được gọi")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        friendService.cancelRequest(currentUserId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/friend-requests/received")
    @Operation(summary = "Danh sách lời mời đang chờ TÔI duyệt")
    public ApiResponse<PageResponse<FriendRequestResponse>> received(
            @AuthenticationPrincipal UUID currentUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(friendService.getReceivedRequests(currentUserId, pageable));
    }

    @GetMapping("/api/v1/friend-requests/sent")
    @Operation(summary = "Danh sách lời mời TÔI đã gửi, đang chờ phản hồi")
    public ApiResponse<PageResponse<FriendRequestResponse>> sent(
            @AuthenticationPrincipal UUID currentUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(friendService.getSentRequests(currentUserId, pageable));
    }

    @GetMapping("/api/v1/friends")
    @Operation(summary = "Danh sách bạn bè")
    public ApiResponse<PageResponse<FriendResponse>> friends(
            @AuthenticationPrincipal UUID currentUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(friendService.getFriends(currentUserId, pageable));
    }

    @DeleteMapping("/api/v1/friends/{id}")
    @Operation(summary = "Hủy kết bạn")
    public ApiResponse<Void> removeFriend(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable UUID id) {
        friendService.removeFriend(currentUserId, id);
        return ApiResponse.ok();
    }
}
