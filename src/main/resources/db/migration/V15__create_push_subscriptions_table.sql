CREATE TABLE push_subscriptions
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   VARCHAR(500) NOT NULL,
    p256dh_key VARCHAR(255) NOT NULL,
    auth_key   VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- endpoint là định danh DUY NHẤT do trình duyệt cấp cho mỗi thiết bị. Người dùng bấm "cho phép
-- thông báo" nhiều lần trên cùng máy sẽ gửi lại đúng endpoint đó — unique index biến việc này
-- thành cập nhật thay vì nhân bản, tránh gửi trùng một thông báo nhiều lần tới cùng một máy.
CREATE UNIQUE INDEX idx_push_subscriptions_endpoint ON push_subscriptions (endpoint);

-- Gửi push luôn bắt đầu bằng "lấy mọi thiết bị của user này".
CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions (user_id);

COMMENT ON TABLE push_subscriptions IS 'Đăng ký Web Push theo từng thiết bị (Phase 5 — UC-23)';
