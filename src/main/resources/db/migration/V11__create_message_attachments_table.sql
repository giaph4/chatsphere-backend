CREATE TABLE message_attachments
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    message_id    UUID         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    file_url      VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_type     VARCHAR(100) NOT NULL,
    file_size     BIGINT       NOT NULL,
    thumbnail_url VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_message_attachments_size CHECK (file_size > 0)
);

-- Đính kèm LUÔN được đọc theo tin nhắn ("mở hội thoại -> lấy 30 tin -> lấy attachment của
-- chúng"), không bao giờ đọc độc lập. Index này phục vụ đúng truy vấn đó và cũng chính là cái
-- Hibernate cần khi nạp attachment theo lô, tránh N+1.
CREATE INDEX idx_message_attachments_message_id ON message_attachments (message_id);

-- ON DELETE CASCADE ở FK: xóa cứng 1 tin nhắn thì bản ghi đính kèm không còn ý nghĩa gì.
-- Lưu ý file THẬT trên MinIO không bị xóa theo — dọn file mồ côi là việc của job định kỳ
-- (Phase 8), cố tình tách khỏi transaction DB vì xóa file là thao tác không hoàn tác được.
COMMENT ON TABLE message_attachments IS 'Tệp đính kèm của tin nhắn (Phase 5 — UC-19)';
