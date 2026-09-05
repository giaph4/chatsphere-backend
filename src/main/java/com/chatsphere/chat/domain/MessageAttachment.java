package com.chatsphere.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Tệp đính kèm của 1 tin nhắn (UC-19).
 *
 * <p>Bảng riêng thay vì thêm cột {@code file_url} vào {@code messages}: một tin nhắn có thể kèm
 * nhiều ảnh, và tuyệt đại đa số tin nhắn là chữ thuần — nhét 5 cột luôn NULL vào bảng lớn nhất
 * hệ thống là phí cả dung lượng lẫn băng thông đọc.
 *
 * <p>Không kế thừa {@code BaseEntity}: bản ghi bất biến sau khi tạo, không có {@code updated_at}.
 */
@Entity
@Table(name = "message_attachments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MIME type THẬT do Tika phát hiện lúc upload, không phải giá trị client khai báo. */
    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public static MessageAttachment of(Message message, String fileUrl, String fileName,
                                       String fileType, long fileSize) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessage(message);
        attachment.setFileUrl(fileUrl);
        attachment.setFileName(fileName);
        attachment.setFileType(fileType);
        attachment.setFileSize(fileSize);
        return attachment;
    }
}
