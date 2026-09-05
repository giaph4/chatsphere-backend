package com.chatsphere.chat.controller;

import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.service.MessageService;
import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Phase 3 — REST thuần, CHƯA real-time: người nhận phải tự gọi lại {@code GET .../messages}
 * để thấy tin nhắn mới (đúng hành vi mong đợi, xem 03_CODE_ROADMAP.md Phase 3). Phase 4 sẽ
 * thêm STOMP controller gọi lại {@code sendMessage()} rồi broadcast qua WebSocket.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Message", description = "Gửi tin nhắn, lấy lịch sử phân trang, thu hồi (Phase 3 — REST)")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/api/v1/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi tin nhắn vào 1 cuộc trò chuyện")
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(messageService.sendMessage(currentUserId, conversationId, request));
    }

    @GetMapping("/api/v1/conversations/{conversationId}/messages")
    @Operation(summary = "Lấy lịch sử tin nhắn — cursor-based pagination, mới nhất trước")
    public ApiResponse<CursorPageResponse<MessageResponse>> getMessages(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(messageService.getMessages(currentUserId, conversationId, cursor, limit));
    }

    @PutMapping("/api/v1/messages/{id}/recall")
    @Operation(summary = "Thu hồi tin nhắn — chỉ người gửi, trong vòng 5 phút")
    public ApiResponse<MessageResponse> recall(
            @AuthenticationPrincipal UUID currentUserId,
            @PathVariable UUID id) {
        return ApiResponse.success(messageService.recallMessage(currentUserId, id));
    }
}
