package com.chatsphere.chat.dto;

import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String fileUrl,
        String fileName,
        String fileType,
        long fileSize,
        String thumbnailUrl
) {
}
