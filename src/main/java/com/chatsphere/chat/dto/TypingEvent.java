package com.chatsphere.chat.dto;

import java.util.UUID;

/**
 * Sự kiện "đang soạn tin" phát tới {@code /topic/conversation/{id}}.
 *
 * <p><b>KHÔNG lưu database</b> — dữ liệu chỉ có ý nghĩa trong đúng vài giây, ghi xuống đĩa là
 * lãng phí I/O trên bảng nóng nhất hệ thống, và mất frame cũng không gây hậu quả gì.
 *
 * <p>Người gửi cũng nhận lại sự kiện của chính mình (broadcast lên topic chung, không loại trừ
 * người gửi). Đây là đánh đổi có chủ ý: loại trừ ở server đòi hỏi gửi riêng từng thành viên
 * (N frame thay vì 1) chỉ để tiết kiệm một dòng {@code if} phía client — client vốn đã biết
 * userId của mình nên tự bỏ qua rất rẻ.
 */
public record TypingEvent(
        UUID conversationId,
        UUID userId,
        boolean typing
) {
}
