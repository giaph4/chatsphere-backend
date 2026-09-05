package com.chatsphere.chat.dto;

import com.chatsphere.chat.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * `conversationId` lấy từ path variable của controller, không lặp lại trong body.
 * <p>
 * Phase 3 mới hỗ trợ thật sự type=TEXT (MessageAttachment cho IMAGE/FILE/VOICE thêm ở
 * Phase 5) — content vì vậy bắt buộc không rỗng ở bước này; ràng buộc sẽ nới lỏng khi
 * Phase 5 thêm attachment.
 */
public record SendMessageRequest(

        @NotNull
        MessageType type,

        @NotBlank
        @Size(max = 5000)
        String content,

        UUID replyToMessageId
) {
}
