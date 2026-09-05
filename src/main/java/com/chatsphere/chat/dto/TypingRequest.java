package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Client báo bắt đầu ({@code typing=true}) hoặc dừng soạn tin ({@code typing=false}).
 *
 * <p>Dùng cờ boolean thay vì chỉ gửi "đang gõ" rồi để người nhận tự hết hạn sau vài giây:
 * người gõ xong rồi xóa hết chữ vẫn hiện "đang soạn tin..." thêm mấy giây là sai sự thật.
 * Phía client vẫn nên đặt thêm timeout an toàn phòng khi frame {@code false} bị mất.
 */
public record TypingRequest(

        @NotNull
        UUID conversationId,

        boolean typing
) {
}
