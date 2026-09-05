package com.chatsphere.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * "Xóa phía tôi" — ẩn 1 tin nhắn khỏi tầm mắt ĐÚNG MỘT người (UC-28).
 *
 * <p>Phân biệt rõ với hai khái niệm dễ nhầm:
 * <ul>
 *   <li><b>Thu hồi</b> ({@code messages.status = RECALLED}): xóa nội dung với MỌI người, chỉ
 *       người gửi được làm, trong 5 phút.</li>
 *   <li><b>Xóa phía tôi</b> (bảng này): người khác vẫn thấy tin bình thường, ai cũng làm được,
 *       không giới hạn thời gian.</li>
 * </ul>
 * Vì mỗi người có tầm nhìn riêng nên không thể biểu diễn bằng một cột cờ trên {@code messages}.
 */
@Entity
@Table(name = "message_deletions")
@Getter
@Setter
@NoArgsConstructor
public class MessageDeletion {

    @EmbeddedId
    private MessageDeletionId id;

    /**
     * {@code @MapsId} nối quan hệ với đúng cột đã nằm trong khóa tổ hợp — không sinh thêm cột
     * nào. {@code insertable=false, updatable=false}: giá trị do khóa quyết định, quan hệ chỉ
     * để điều hướng.
     */
    @MapsId("messageId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    /** Cột {@code user_id} đã do khóa tổ hợp ánh xạ — không khai lại field để tránh 2 nơi cùng ghi. */
    public UUID getUserId() {
        return id.getUserId();
    }

    public static MessageDeletion of(Message message, UUID userId) {
        MessageDeletion deletion = new MessageDeletion();
        deletion.setId(new MessageDeletionId(message.getId(), userId));
        deletion.setMessage(message);
        deletion.setDeletedAt(Instant.now());
        return deletion;
    }
}
