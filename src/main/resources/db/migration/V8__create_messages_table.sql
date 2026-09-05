CREATE TABLE messages
(
    id                         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    conversation_id            UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id                  UUID        NOT NULL REFERENCES users (id),
    type                       VARCHAR(20) NOT NULL,
    content                    TEXT,
    -- Self-reference: 1 message có thể reply/forward 1 message khác trong CÙNG bảng.
    reply_to_message_id        UUID REFERENCES messages (id),
    forwarded_from_message_id  UUID REFERENCES messages (id),
    status                     VARCHAR(20) NOT NULL DEFAULT 'SENT',
    is_edited                  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    deleted_at                 TIMESTAMPTZ,

    CONSTRAINT chk_messages_type CHECK (type IN ('TEXT', 'IMAGE', 'FILE', 'VOICE', 'SYSTEM')),
    CONSTRAINT chk_messages_status CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'RECALLED'))
);

-- Index quan trọng nhất hệ thống (theo 01_SYSTEM_DESIGN.md 7.3.8):
-- phục vụ query "lấy N tin nhắn gần nhất của 1 conversation, phân trang cursor theo created_at".
CREATE INDEX idx_messages_conversation_created_at ON messages (conversation_id, created_at DESC);