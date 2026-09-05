package com.chatsphere.chat.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Khóa chính tổ hợp của {@link MessageDeletion}.
 *
 * <p>{@code @EqualsAndHashCode} là BẮT BUỘC với composite key, không phải tùy chọn phong cách:
 * Hibernate dùng equals/hashCode của khóa để nhận biết hai tham chiếu có cùng trỏ về một dòng
 * hay không. Thiếu nó, mỗi lần nạp lại sẽ ra một "thực thể khác", gây insert trùng và lỗi khó lần.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class MessageDeletionId implements Serializable {

    private UUID messageId;
    private UUID userId;

    public MessageDeletionId(UUID messageId, UUID userId) {
        this.messageId = messageId;
        this.userId = userId;
    }
}
