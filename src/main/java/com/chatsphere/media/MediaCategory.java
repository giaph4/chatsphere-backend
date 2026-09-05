package com.chatsphere.media;

import com.chatsphere.chat.domain.MessageType;

import java.util.Set;

/**
 * Nhóm loại file được phép tải lên, kèm giới hạn kích thước riêng cho từng nhóm.
 *
 * <p><b>Vì sao chia nhóm thay vì một hạn mức chung?</b> Một tấm ảnh 25MB gần như luôn là ảnh
 * chưa nén gửi nhầm, còn một file tài liệu 25MB thì hoàn toàn bình thường. Hạn mức chung buộc
 * ta phải chọn: đủ rộng cho tài liệu thì mở toang cho ảnh, hoặc đủ chặt cho ảnh thì chặn oan
 * tài liệu.
 *
 * <p>Danh sách MIME là <b>allowlist</b> (chỉ cho phép những gì liệt kê), không phải blocklist.
 * Blocklist luôn thua: mỗi định dạng nguy hiểm mới xuất hiện là một lỗ hổng, còn allowlist thì
 * cái gì chưa biết mặc định bị chặn.
 */
public enum MediaCategory {

    IMAGE(MessageType.IMAGE, 10L * 1024 * 1024, Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp")),

    VOICE(MessageType.VOICE, 10L * 1024 * 1024, Set.of(
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/webm", "audio/wav", "audio/x-wav")),

    FILE(MessageType.FILE, 25L * 1024 * 1024, Set.of(
            "application/pdf",
            "application/zip",
            "text/plain", "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

    private final MessageType messageType;
    private final long maxBytes;
    private final Set<String> allowedMimeTypes;

    MediaCategory(MessageType messageType, long maxBytes, Set<String> allowedMimeTypes) {
        this.messageType = messageType;
        this.maxBytes = maxBytes;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public MessageType messageType() {
        return messageType;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public boolean allows(String mimeType) {
        return allowedMimeTypes.contains(mimeType);
    }
}
