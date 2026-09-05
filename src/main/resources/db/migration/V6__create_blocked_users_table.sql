CREATE TABLE blocked_users
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_blocked_users_not_self CHECK (blocker_id <> blocked_id)
);

CREATE UNIQUE INDEX idx_blocked_users_pair ON blocked_users (blocker_id, blocked_id);

-- Cần cho câu hỏi ngược "ai đang chặn tôi?" — isBlockedBetween() tra cả 2 chiều trong 1 query,
-- chiều thứ 2 lọc theo blocked_id nên unique index ở trên không phục vụ được (leftmost prefix).
CREATE INDEX idx_blocked_users_blocked_id ON blocked_users (blocked_id);
