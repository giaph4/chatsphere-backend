CREATE TABLE conversations
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    type            VARCHAR(10) NOT NULL,
    name            VARCHAR(100),
    avatar_url      VARCHAR(500),
    created_by      UUID REFERENCES users (id),
    -- Chưa có FK ở đây vì bảng messages chưa tồn tại (phụ thuộc vòng).
    -- FK được gắn bổ sung ở V10 sau khi messages đã được tạo.
    last_message_id UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_conversations_type CHECK (type IN ('DIRECT', 'GROUP'))
);

CREATE INDEX idx_conversations_updated_at ON conversations (updated_at DESC);