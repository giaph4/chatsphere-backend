package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bản gửi tin nhắn qua WebSocket.
 *
 * <p>Khác {@link SendMessageRequest} đúng một field: {@code conversationId}. Ở REST, id nằm
 * trong path (`/conversations/{id}/messages`); STOMP không có path variable nên phải đưa vào
 * body. Giữ 2 record riêng thay vì thêm field nullable vào record cũ: DTO của REST sẽ có một
 * field "lúc có lúc không" mà client REST không bao giờ được điền — đúng loại mập mờ khiến
 * validate và tài liệu API mất chính xác.
 */
public record WsSendMessageRequest(

        @NotNull
        UUID conversationId,

        @NotNull
        MessageType type,

        @Size(max = 5000)
        String content,

        UUID replyToMessageId,

        @Valid
        @Size(max = 10, message = "Mỗi tin nhắn chỉ đính kèm tối đa 10 tệp")
        List<AttachmentRequest> attachments
) {

    public WsSendMessageRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** Tương thích ngược với luồng Phase 4 chỉ gửi tin nhắn chữ. */
    public WsSendMessageRequest(UUID conversationId, MessageType type, String content, UUID replyToMessageId) {
        this(conversationId, type, content, replyToMessageId, List.of());
    }

    /**
     * Quy về DTO của Phase 3 để tái sử dụng nguyên vẹn MessageService.sendMessage() — kể cả
     * quy tắc "phải có chữ hoặc có tệp" thêm ở Phase 5, luồng WebSocket không kiểm tra lại lần nào.
     */
    public SendMessageRequest toSendMessageRequest() {
        return new SendMessageRequest(type, content, replyToMessageId, attachments);
    }
}
