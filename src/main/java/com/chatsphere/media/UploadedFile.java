package com.chatsphere.media;

/**
 * Kết quả một lần upload — vừa là response REST, vừa là thứ client gửi ngược lên khi đính tin
 * nhắn (Phase 5 mục 5.1).
 *
 * <p>Luồng gồm 2 bước tách rời: {@code POST /media/upload} trả về record này, rồi client mới
 * gọi gửi tin nhắn kèm {@code fileUrl}. Không gộp thành một request multipart duy nhất vì:
 * người dùng chọn ảnh xong là upload chạy nền ngay trong lúc họ còn đang gõ chú thích, nên lúc
 * bấm Gửi thì tin bay đi tức thì; gửi lại tin nhắn thất bại cũng không phải tải lên lần nữa.
 */
public record UploadedFile(
        String fileUrl,
        String fileName,
        String fileType,
        long fileSize,
        MediaCategory category
) {
}
