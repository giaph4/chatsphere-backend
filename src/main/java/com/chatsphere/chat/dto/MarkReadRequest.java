package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Đánh dấu đã đọc tới {@code messageId} (UC-24).
 *
 * <p>Gửi "đọc tới tin nào" chứ không phải "đọc thêm N tin": con trỏ tuyệt đối nên gửi trùng
 * nhiều lần vẫn ra cùng kết quả (idempotent) — client cuộn qua lại, mạng gửi lặp, hai tab cùng
 * mở đều không làm sai {@code unreadCount}.
 */
public record MarkReadRequest(

        @NotNull
        UUID conversationId,

        @NotNull
        UUID messageId
) {
}
