package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * `conversationId` lấy từ path variable của controller, không lặp lại trong body.
 *
 * <p>Phase 5 nới ràng buộc của Phase 3: {@code content} KHÔNG còn {@code @NotBlank} vì tin nhắn
 * chỉ có ảnh (không kèm chữ) là hoàn toàn hợp lệ. Quy tắc thay thế — "phải có chữ HOẶC có tệp,
 * và IMAGE/FILE/VOICE bắt buộc có tệp" — là ràng buộc giữa nhiều field, không diễn đạt được
 * bằng annotation trên từng field, nên được kiểm tra trong {@code MessageService.sendMessage()}
 * và trả về mã lỗi rõ nghĩa ({@code MESSAGE_CONTENT_REQUIRED} / {@code ATTACHMENT_REQUIRED}).
 */
public record SendMessageRequest(

        @NotNull
        MessageType type,

        @Size(max = 5000)
        String content,

        UUID replyToMessageId,

        /** Rỗng hoặc null với tin nhắn chữ thuần. */
        @Valid
        @Size(max = 10, message = "Mỗi tin nhắn chỉ đính kèm tối đa 10 tệp")
        List<AttachmentRequest> attachments
) {

    /** Chuẩn hóa null thành danh sách rỗng để mọi nơi dùng khỏi phải kiểm tra null. */
    public SendMessageRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** Tương thích ngược với các chỗ gọi từ Phase 3/4 chỉ gửi tin nhắn chữ. */
    public SendMessageRequest(MessageType type, String content, UUID replyToMessageId) {
        this(type, content, replyToMessageId, List.of());
    }
}
