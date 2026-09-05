-- =====================================================================
-- SEED DATA — môi trường DEV, Phase 0-2 (users, user_settings,
-- friend_requests, friendships, blocked_users).
--
-- KHÔNG PHẢI Flyway migration — cố tình KHÔNG đặt trong
-- src/main/resources/db/migration, vì đây là dữ liệu mẫu để test tay,
-- không phải thay đổi schema. Chạy thủ công khi cần:
--
--   docker exec -i chatsphere-postgres psql -U chatsphere -d chatsphere \
--     < scripts/seed_dev_data.sql
--
-- An toàn chạy lại nhiều lần: mọi INSERT đều có ON CONFLICT DO NOTHING,
-- không cập nhật hay nhân đôi dữ liệu nếu đã tồn tại. KHÔNG chạy trên
-- production hay bất kỳ DB nào có dữ liệu thật.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. USERS
--
-- status = 'ACTIVE' thẳng (bỏ qua OTP) vì đây là dữ liệu mẫu, không phải
-- luồng đăng ký thật. password_hash là BCrypt strength 12 SINH THẬT bằng
-- org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
-- — đúng bean PasswordEncoderConfig của app, đã tự kiểm bằng matches()
-- trước khi đưa vào đây (không phải giá trị bịa).
--
--   admin, minhanh, quanghuy, thuytrang, vanan, hoaipham, ngocmai
--   → mật khẩu "Password1" (mọi user role USER)
--   admin → mật khẩu "Admin@123" (chưa có user ADMIN nào trong DB trước đó)
-- ---------------------------------------------------------------------

INSERT INTO users (id, email, password_hash, username, display_name, status, role, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'admin@chatsphere.local',
     '$2a$12$aPk5HLz5aDGZYsuZn2I6FeNb4CZ42Pug2BXa8TbQ2VpP/mLypyP6.',
     'admin', 'Quản trị viên', 'ACTIVE', 'ADMIN', now(), now()),

    (gen_random_uuid(), 'minhanh@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'minhanh', 'Minh Anh', 'ACTIVE', 'USER', now(), now()),

    (gen_random_uuid(), 'quanghuy@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'quanghuy', 'Quang Huy', 'ACTIVE', 'USER', now(), now()),

    (gen_random_uuid(), 'thuytrang@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'thuytrang', 'Thùy Trang', 'ACTIVE', 'USER', now(), now()),

    (gen_random_uuid(), 'vanan@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'vanan', 'Văn An', 'ACTIVE', 'USER', now(), now()),

    (gen_random_uuid(), 'hoaipham@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'hoaipham', 'Hoài Phạm', 'ACTIVE', 'USER', now(), now()),

    (gen_random_uuid(), 'ngocmai@chatsphere.local',
     '$2a$12$JoGBNoCCHo12FY0cZT2BQ.N7VrwqY/AJy5tHUUJd6FNHsL719cBni',
     'ngocmai', 'Ngọc Mai', 'ACTIVE', 'USER', now(), now())
ON CONFLICT (email) DO NOTHING;
-- ON CONFLICT (email): idx_users_email là UNIQUE INDEX (không phải named
-- constraint) — Postgres vẫn dùng được làm arbiter vì suy luận theo cột,
-- không cần tên constraint.

-- ---------------------------------------------------------------------
-- 2. USER_SETTINGS
--
-- Chỉ seed 2 user với giá trị KHÁC mặc định để demo — số còn lại (kể cả
-- admin) app sẽ tự tạo lazy khi đọc lần đầu (UserSettingsService.getOrCreate),
-- đúng thiết kế "không backfill" đã ghi trong 08_PHASE2_USER_FRIEND_REPORT.md.
-- ---------------------------------------------------------------------

INSERT INTO user_settings (user_id, online_visibility, call_permission, notification_enabled, updated_at)
SELECT id, 'FRIENDS_ONLY', 'FRIENDS_ONLY', true, now()
FROM users WHERE username = 'vanan'
UNION ALL
SELECT id, 'NOBODY', 'NOBODY', false, now()
FROM users WHERE username = 'ngocmai'
ON CONFLICT (user_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. FRIEND_REQUESTS
--
-- Trộn đủ 4 trạng thái để test được mọi nhánh qua Swagger/curl ngay:
--   ACCEPTED (2 cặp) · PENDING (2 cặp) · REJECTED (1) · CANCELLED (1)
--
-- ⚠️ KHÔNG dùng ON CONFLICT DO NOTHING ở bảng này: idx_friend_requests_pending
-- CHỈ là partial unique index áp lên status = 'PENDING' (đúng thiết kế —
-- cho phép gửi lại sau khi bị từ chối/hủy, xem 08_PHASE2_USER_FRIEND_REPORT.md
-- §3.4). Nghĩa là KHÔNG có ràng buộc DB nào chặn trùng lặp ở ACCEPTED/
-- REJECTED/CANCELLED — dùng ON CONFLICT ở đây sẽ lặng lẽ bỏ qua PENDING
-- trùng nhưng vẫn NHÂN ĐÔI vô hạn 4 dòng còn lại mỗi lần chạy script
-- (bug thật đã xảy ra và được phát hiện ở đúng bản seed đầu tiên: chạy
-- script 2 lần tạo ra 2 bản ACCEPTED/REJECTED/CANCELLED giống hệt nhau).
-- Dùng WHERE NOT EXISTS để tự đảm bảo idempotent bất kể index có gì.
-- ---------------------------------------------------------------------

INSERT INTO friend_requests (id, sender_id, receiver_id, status, created_at, updated_at)
SELECT gen_random_uuid(), s.id, r.id, 'ACCEPTED', now() - interval '3 days', now() - interval '3 days'
FROM users s, users r WHERE s.username = 'minhanh' AND r.username = 'quanghuy'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id)
UNION ALL
SELECT gen_random_uuid(), s.id, r.id, 'ACCEPTED', now() - interval '2 days', now() - interval '2 days'
FROM users s, users r WHERE s.username = 'thuytrang' AND r.username = 'vanan'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id)
UNION ALL
SELECT gen_random_uuid(), s.id, r.id, 'PENDING', now() - interval '1 hour', now() - interval '1 hour'
FROM users s, users r WHERE s.username = 'hoaipham' AND r.username = 'minhanh'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id)
UNION ALL
SELECT gen_random_uuid(), s.id, r.id, 'PENDING', now() - interval '30 minutes', now() - interval '30 minutes'
FROM users s, users r WHERE s.username = 'ngocmai' AND r.username = 'quanghuy'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id)
UNION ALL
SELECT gen_random_uuid(), s.id, r.id, 'REJECTED', now() - interval '5 days', now() - interval '4 days'
FROM users s, users r WHERE s.username = 'vanan' AND r.username = 'ngocmai'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id)
UNION ALL
SELECT gen_random_uuid(), s.id, r.id, 'CANCELLED', now() - interval '6 days', now() - interval '6 days'
FROM users s, users r WHERE s.username = 'hoaipham' AND r.username = 'thuytrang'
  AND NOT EXISTS (SELECT 1 FROM friend_requests fr WHERE fr.sender_id = s.id AND fr.receiver_id = r.id);
-- NOT EXISTS khớp theo (sender_id, receiver_id) bất kể status — đủ dùng vì
-- mỗi cặp trong bộ seed này chỉ xuất hiện đúng 1 lần với đúng 1 status.

-- ---------------------------------------------------------------------
-- 4. FRIENDSHIPS
--
-- ⚠️ BẮT BUỘC dùng LEAST/GREATEST — KHÔNG tự đoán thứ tự bằng mắt hay
-- chèn 2 cột theo thứ tự viết trong câu lệnh. LEAST/GREATEST cho kiểu
-- uuid dùng ĐÚNG toán tử "<" mà PostgreSQL dùng để kiểm tra
-- chk_friendships_order — tức là tự động khớp 100% với CHECK constraint,
-- không có khe hở nào để sai (khác java UUID.compareTo() — xem cảnh báo
-- trong Friendship.between(), 08_PHASE2_USER_FRIEND_REPORT.md §3.4).
-- ---------------------------------------------------------------------

INSERT INTO friendships (id, user_id_1, user_id_2, created_at)
SELECT gen_random_uuid(), LEAST(u1.id, u2.id), GREATEST(u1.id, u2.id), now() - interval '3 days'
FROM users u1, users u2 WHERE u1.username = 'minhanh' AND u2.username = 'quanghuy'
UNION ALL
SELECT gen_random_uuid(), LEAST(u1.id, u2.id), GREATEST(u1.id, u2.id), now() - interval '2 days'
FROM users u1, users u2 WHERE u1.username = 'thuytrang' AND u2.username = 'vanan'
ON CONFLICT DO NOTHING;
-- 2 cặp này khớp đúng 2 friend_requests ACCEPTED ở mục 3 — giữ dữ liệu
-- nhất quán như app thật sẽ tạo ra (accept request luôn kèm 1 friendship).

-- ---------------------------------------------------------------------
-- 5. BLOCKED_USERS
--
-- ngocmai chặn vanan — dùng để test: vanan biến mất khỏi kết quả search
-- của ngocmai, và cả 2 không gửi lời mời kết bạn cho nhau được (403).
-- ---------------------------------------------------------------------

INSERT INTO blocked_users (id, blocker_id, blocked_id, created_at)
SELECT gen_random_uuid(), b.id, t.id, now() - interval '1 day'
FROM users b, users t WHERE b.username = 'ngocmai' AND t.username = 'vanan'
ON CONFLICT DO NOTHING;

COMMIT;

-- =====================================================================
-- KIỂM TRA NHANH SAU KHI SEED
-- =====================================================================

\echo '--- users (role, status) ---'
SELECT username, email, role, status FROM users ORDER BY created_at;

\echo '--- friend_requests (đọc bằng username cho dễ) ---'
SELECT s.username AS sender, r.username AS receiver, fr.status
FROM friend_requests fr
JOIN users s ON s.id = fr.sender_id
JOIN users r ON r.id = fr.receiver_id
ORDER BY fr.created_at;

\echo '--- friendships ---'
SELECT u1.username AS user_1, u2.username AS user_2, f.created_at
FROM friendships f
JOIN users u1 ON u1.id = f.user_id_1
JOIN users u2 ON u2.id = f.user_id_2
ORDER BY f.created_at;

\echo '--- blocked_users ---'
SELECT b.username AS blocker, t.username AS blocked
FROM blocked_users bu
JOIN users b ON b.id = bu.blocker_id
JOIN users t ON t.id = bu.blocked_id;

\echo '--- user_settings ---'
SELECT u.username, us.online_visibility, us.call_permission, us.notification_enabled
FROM user_settings us
JOIN users u ON u.id = us.user_id;
