CREATE TABLE message_deletions
(
    message_id UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    deleted_at TIMESTAMPTZ NOT NULL,

    -- Khóa chính tổ hợp thay cho cột id sinh tự động (khác thiết kế gốc 7.3.9 một chút):
    -- bảng này thuần túy là quan hệ nhiều-nhiều "ai đã ẩn tin nào", không bao giờ được tham
    -- chiếu từ nơi khác, nên một cột id nữa chỉ tốn chỗ và thêm một index phải bảo trì.
    PRIMARY KEY (message_id, user_id)
);

-- "Xóa phía tôi" khác hẳn "thu hồi": thu hồi (messages.status = RECALLED) xóa tin với MỌI
-- người, còn bảng này chỉ ẩn tin khỏi tầm mắt đúng một người — người khác vẫn thấy bình thường.
-- Vì vậy phải là bảng riêng chứ không thể là một cột cờ trên messages.
CREATE INDEX idx_message_deletions_user_id ON message_deletions (user_id);

COMMENT ON TABLE message_deletions IS 'Ẩn tin nhắn riêng từng người (Phase 5 — UC-28)';
