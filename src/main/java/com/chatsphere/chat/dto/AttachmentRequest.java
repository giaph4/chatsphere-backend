package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Mô tả 1 tệp đã upload xong, gửi kèm khi tạo tin nhắn. Client chép nguyên các giá trị mà
 * {@code POST /api/v1/media/upload} vừa trả về.
 *
 * <p><b>Vì sao dám nhận metadata từ client?</b> Bản thân FILE đã được kiểm tra kỹ lúc upload
 * (magic byte, kích thước) — đó mới là chỗ có rủi ro bảo mật. Mấy trường ở đây chỉ để HIỂN THỊ.
 * Chốt chặn còn lại là {@code fileUrl} bắt buộc phải trỏ vào bucket của chính hệ thống
 * ({@code MediaService.assertManagedUrl}), nếu không kẻ xấu có thể đính một URL bất kỳ và biến
 * khung chat thành nơi dẫn link ra ngoài dưới danh nghĩa tệp nội bộ.
 */
public record AttachmentRequest(

        @NotBlank
        @Size(max = 500)
        String fileUrl,

        @NotBlank
        @Size(max = 255)
        String fileName,

        @NotBlank
        @Size(max = 100)
        String fileType,

        @Positive
        long fileSize
) {
}
