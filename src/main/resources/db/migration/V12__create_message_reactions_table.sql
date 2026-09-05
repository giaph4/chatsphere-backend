CREATE TABLE message_reactions
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    message_id UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji      VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Mỗi người CHỈ có 1 reaction trên 1 tin nhắn (01_SYSTEM_DESIGN.md 7.3.11): thả emoji khác là
-- ĐỔI, không phải thêm. Ràng buộc này đặt ở DB chứ không chỉ trong service vì hai request thả
-- reaction gần như đồng thời (double-click, mạng gửi lặp) sẽ cùng vượt qua bước kiểm tra
-- "đã có chưa?" ở tầng ứng dụng — chỉ unique index mới thật sự chặn được.
CREATE UNIQUE INDEX idx_message_reactions_user_message ON message_reactions (message_id, user_id);

COMMENT ON TABLE message_reactions IS 'Thả cảm xúc lên tin nhắn (Phase 5 — UC-22)';
