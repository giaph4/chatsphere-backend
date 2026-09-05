package com.chatsphere.chat.controller;

import com.chatsphere.chat.dto.AddMemberRequest;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.CreateDirectConversationRequest;
import com.chatsphere.chat.dto.CreateGroupRequest;
import com.chatsphere.chat.dto.UpdateGroupRequest;
import com.chatsphere.chat.service.ConversationService;
import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.PageResponse;
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

/**
 * currentUserId lấy qua {@code @AuthenticationPrincipal} — cùng quy ước với
 * UserController/FriendController (Phase 2): JwtAuthenticationFilter đặt thẳng UUID làm
 * principal, Service không tự đọc SecurityContext.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation", description = "Hội thoại 1-1, nhóm chat, thành viên (Phase 3 — REST, chưa real-time)")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "Danh sách hội thoại của tôi — mới nhất trước, kèm lastMessage/unreadCount")
    public ApiResponse<PageResponse<ConversationResponse>> myConversations(
            @AuthenticationPrincipal UUID currentUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(conversationService.getMyConversations(currentUserId, pageable));
    }

    @PostMapping("/direct")
    @Operation(summary = "Tạo hoặc lấy lại hội thoại 1-1 đã có với 1 người")
    public ApiResponse<ConversationResponse> createOrGetDirect(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody CreateDirectConversationRequest request) {
        return ApiResponse.success(
                conversationService.getOrCreateDirectConversation(currentUserId, request.userId()));
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo nhóm chat — người gọi API tự động là ADMIN")
    public ApiResponse<ConversationResponse> createGroup(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.success(conversationService.createGroup(currentUserId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Đổi tên/ảnh nhóm — chỉ ADMIN")
    public ApiResponse<ConversationResponse> updateGroupInfo(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ApiResponse.success(conversationService.updateGroupInfo(currentUserId, id, request));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm thành viên vào nhóm — chỉ ADMIN")
    public ApiResponse<Void> addMember(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request) {
        conversationService.addMember(currentUserId, id, request.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Xóa thành viên khỏi nhóm — chỉ ADMIN")
    public ApiResponse<Void> removeMember(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        conversationService.removeMember(currentUserId, id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Rời nhóm — tự động chuyển ADMIN cho người tham gia sớm nhất nếu mình là admin cuối cùng")
    public ApiResponse<Void> leaveGroup(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID id) {
        conversationService.leaveGroup(currentUserId, id);
        return ApiResponse.ok();
    }
}
