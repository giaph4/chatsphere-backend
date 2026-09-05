package com.chatsphere.chat.controller;

import com.chatsphere.auth.security.StompPrincipal;
import com.chatsphere.chat.dto.MarkReadRequest;
import com.chatsphere.chat.dto.TypingEvent;
import com.chatsphere.chat.dto.TypingRequest;
import com.chatsphere.chat.dto.WsSendMessageRequest;
import com.chatsphere.chat.service.MessageService;
import com.chatsphere.common.ApiError;
import com.chatsphere.common.ApiResponse;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.WsDestinations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Cửa vào realtime của module chat (03_CODE_ROADMAP.md 4.3).
 *
 * <p><b>Đây chỉ là một lớp vỏ mỏng.</b> Toàn bộ quy tắc nghiệp vụ — ai được gửi vào hội thoại
 * nào, chặn, thu hồi, con trỏ đã đọc — vẫn nằm nguyên ở {@code MessageService} của Phase 3 và
 * được dùng lại y nguyên. Đó là lý do roadmap tách Phase 3 (REST) khỏi Phase 4: logic đã được
 * debug xong bằng Postman, ở đây chỉ đổi cách gọi.
 *
 * <p>Các handler đều trả {@code void} và KHÔNG dùng {@code @SendTo}: việc phát sóng do
 * {@code ChatRealtimeBroadcaster} lo sau khi transaction commit. Nếu vừa {@code @SendTo} vừa
 * broadcaster thì mỗi tin sẽ được gửi hai lần.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Client gửi tới {@code /app/chat.sendMessage}. */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Valid @Payload WsSendMessageRequest request, Principal principal) {
        UUID senderId = StompPrincipal.userIdOf(principal);
        messageService.sendMessage(senderId, request.conversationId(), request.toSendMessageRequest());
    }

    /**
     * Client gửi tới {@code /app/chat.typing}. KHÔNG chạm database — phát thẳng lên topic.
     */
    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload TypingRequest request, Principal principal) {
        UUID userId = StompPrincipal.userIdOf(principal);
        messageService.assertParticipant(userId, request.conversationId());

        messagingTemplate.convertAndSend(
                WsDestinations.conversationTopic(request.conversationId()),
                new TypingEvent(request.conversationId(), userId, request.typing()));
    }

    /** Client gửi tới {@code /app/chat.markRead}. */
    @MessageMapping("/chat.markRead")
    public void markRead(@Valid @Payload MarkReadRequest request, Principal principal) {
        UUID userId = StompPrincipal.userIdOf(principal);
        messageService.markRead(userId, request.conversationId(), request.messageId());
    }

    /**
     * Lỗi nghiệp vụ ở luồng STOMP không có mã HTTP để trả về, và cũng không được phép làm đứt
     * kết nối (khác lỗi xác thực ở {@code CONNECT}) — người dùng gõ nhầm vào hội thoại vừa bị
     * xóa mà mất luôn cả phiên realtime thì quá nặng tay. Vì vậy trả lỗi riêng cho chính người
     * gửi qua {@code /user/queue/errors}, dùng lại đúng phong bì {@code ApiResponse} của REST
     * để client chỉ phải hiểu một định dạng lỗi.
     */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(WsDestinations.QUEUE_ERRORS)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.debug("Lỗi nghiệp vụ trên WebSocket: {} — {}", e.getErrorCode(), e.getMessage());
        return ApiResponse.error(ApiError.of(e.getErrorCode(), e.getMessage()));
    }

    /**
     * Chặn mọi lỗi còn lại (payload sai kiểu, validate thất bại, bug thật). Không có handler này
     * thì exception chỉ nằm lại trong log server còn client chờ mãi một phản hồi không bao giờ
     * tới — triệu chứng "bấm gửi không thấy gì xảy ra" rất khó chẩn đoán.
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser(WsDestinations.QUEUE_ERRORS)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("Lỗi không mong đợi trên WebSocket", e);
        return ApiResponse.error(ApiError.of(ErrorCode.INTERNAL_ERROR));
    }
}
