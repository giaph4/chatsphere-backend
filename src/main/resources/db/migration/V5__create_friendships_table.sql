CREATE TABLE friendships
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id_1  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_id_2  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,

    -- Quy ước bắt buộc: user_id_1 < user_id_2 theo thứ tự uuid của PostgreSQL
    -- (so sánh 16 byte KHÔNG DẤU — khác UUID.compareTo của Java, xem Friendship.between()).
    -- Nhờ đó mỗi cặp bạn bè chỉ có ĐÚNG 1 dòng, không thể tồn tại trạng thái lệch 1 chiều.
    CONSTRAINT chk_friendships_order CHECK (user_id_1 < user_id_2)
);

CREATE UNIQUE INDEX idx_friendships_pair ON friendships (user_id_1, user_id_2);

-- user_id_1 đã được index bởi unique index ở trên (cột dẫn đầu);
-- cần index riêng cho user_id_2 để tra ngược chiều cũng nhanh.
CREATE INDEX idx_friendships_user_id_2 ON friendships (user_id_2);
