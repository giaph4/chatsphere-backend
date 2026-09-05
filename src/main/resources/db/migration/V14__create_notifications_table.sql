CREATE TABLE notifications
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(30)  NOT NULL,
    -- KHÔNG đặt FK: reference_id trỏ tới nhiều bảng khác nhau tùy theo `type` (message_id,
    -- friend_request_id, call_session_id...). Đây là đánh đổi có ý thức — mất kiểm tra toàn vẹn
    -- ở tầng DB, đổi lấy một bảng thông báo dùng chung cho mọi loại sự kiện thay vì mỗi loại
    -- một bảng. Bù lại: đối tượng gốc bị xóa thì thông báo thành "mồ côi", nên client luôn phải
    -- xử lý được trường hợp bấm vào thông báo mà không tìm thấy đích.
    reference_id UUID,
    content      VARCHAR(500) NOT NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_notifications_type CHECK (type IN
                                             ('NEW_MESSAGE', 'FRIEND_REQUEST', 'FRIEND_ACCEPTED',
                                              'MISSED_CALL', 'MENTIONED'))
);

-- Truy vấn duy nhất của màn hình thông báo: "của tôi, mới nhất trước, phân trang".
-- Composite index đúng thứ tự (lọc trước, sắp xếp sau) giúp PostgreSQL vừa lọc vừa lấy sẵn
-- đúng thứ tự cần, không phải sort lại.
CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);

-- Index riêng cho huy hiệu "số thông báo chưa đọc" hiện trên mọi màn hình. Partial index
-- (chỉ chứa dòng chưa đọc) nhỏ hơn nhiều lần index đầy đủ vì đại đa số thông báo cũ đã đọc.
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE is_read = FALSE;

COMMENT ON TABLE notifications IS 'Thông báo trong ứng dụng (Phase 5 — UC-26, UC-27)';
