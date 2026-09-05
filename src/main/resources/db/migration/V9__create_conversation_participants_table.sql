CREATE TABLE conversation_participants
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    conversation_id       UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                  VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    last_read_message_id  UUID REFERENCES messages (id),
    muted_until           TIMESTAMPTZ,
    joined_at             TIMESTAMPTZ NOT NULL,
    left_at               TIMESTAMPTZ,

    CONSTRAINT chk_participants_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

-- Partial unique index: chỉ ràng buộc unique khi participant CÒN active (left_at IS NULL).
-- Cho phép 1 user rời nhóm rồi được mời lại (dòng mới), vẫn giữ dòng cũ làm lịch sử.
CREATE UNIQUE INDEX idx_participant_active_unique
    ON conversation_participants (conversation_id, user_id) WHERE left_at IS NULL;

CREATE INDEX idx_participant_user_id ON conversation_participants (user_id);
