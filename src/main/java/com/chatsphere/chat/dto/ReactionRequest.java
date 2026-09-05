package com.chatsphere.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thả/đổi cảm xúc. Gửi lại đúng emoji đang có nghĩa là GỠ reaction (toggle) — xem
 * {@code MessageService.reactToMessage()}.
 *
 * <p>{@code @Size(max = 10)} tính theo ký tự Java (UTF-16 code unit), không phải "1 emoji":
 * nhiều emoji hiện đại là chuỗi ghép nhiều code point (màu da, cờ, gia đình) nên giới hạn 1-2
 * ký tự sẽ chặn oan. Cột DB cũng là VARCHAR(10) cho khớp.
 */
public record ReactionRequest(

        @NotBlank
        @Size(max = 10)
        String emoji
) {
}
