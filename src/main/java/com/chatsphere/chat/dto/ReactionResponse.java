package com.chatsphere.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * Reaction đã được GOM theo emoji, không trả từng dòng thô.
 *
 * <p>Giao diện cần đúng thứ này: "❤️ 3" kèm danh sách ai đã thả khi rê chuột. Trả 3 dòng rời
 * rồi bắt mỗi client tự gom lại là chép cùng một đoạn logic sang mọi nền tảng (web, iOS,
 * Android) và chắc chắn sẽ có chỗ gom sai.
 */
public record ReactionResponse(
        String emoji,
        long count,
        List<UUID> userIds
) {
}
