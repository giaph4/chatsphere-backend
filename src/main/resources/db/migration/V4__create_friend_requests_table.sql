CREATE TABLE friend_requests
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    sender_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,

    -- Không cho tự gửi lời mời cho chính mình: chặn ngay ở tầng DB,
    -- không phụ thuộc hoàn toàn vào validate ở service.
    CONSTRAINT chk_friend_requests_not_self CHECK (sender_id <> receiver_id)
);

-- Partial unique index: chỉ 1 lời mời PENDING cho mỗi cặp (sender, receiver).
-- Các bản ghi REJECTED/CANCELLED/ACCEPTED được giữ lại làm lịch sử và KHÔNG bị chặn,
-- nên sau khi bị từ chối vẫn gửi lại được mà không mất dữ liệu cũ.
CREATE UNIQUE INDEX idx_friend_requests_pending
    ON friend_requests (sender_id, receiver_id)
    WHERE status = 'PENDING';

-- Truy vấn nóng nhất: "lấy danh sách lời mời đang chờ tôi duyệt".
CREATE INDEX idx_friend_requests_receiver_status ON friend_requests (receiver_id, status);
CREATE INDEX idx_friend_requests_sender_status ON friend_requests (sender_id, status);
