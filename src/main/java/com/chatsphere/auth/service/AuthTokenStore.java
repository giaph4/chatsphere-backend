package com.chatsphere.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Bọc toàn bộ state ngắn hạn của auth trong Redis: OTP xác thực email, token reset mật khẩu,
 * bộ đếm đăng nhập sai. Dữ liệu ở đây tự hết hạn (TTL) và mất đi cũng không sao → đúng chỗ cho Redis,
 * không nên nhét vào Postgres (sẽ phải tự viết job dọn rác).
 */
@Component
@RequiredArgsConstructor
public class AuthTokenStore {

    private static final String KEY_EMAIL_OTP = "email_verify:";
    private static final String KEY_OTP_ATTEMPT = "email_verify_attempt:";
    private static final String KEY_OTP_RESEND_COOLDOWN = "otp_resend_cooldown:";
    private static final String KEY_OTP_RESEND_QUOTA = "otp_resend_quota:";
    private static final String KEY_PASSWORD_RESET = "password_reset:";
    private static final String KEY_LOGIN_ATTEMPT = "login_attempt:";

    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_QUOTA_WINDOW = Duration.ofHours(1);

    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int MAX_OTP_ATTEMPTS = 5;
    public static final int MAX_OTP_RESENDS_PER_HOUR = 3;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(RESEND_COOLDOWN_SECONDS);

    private final StringRedisTemplate redis;
    private final SecureRandom secureRandom = new SecureRandom();

    // ---------- OTP xác thực email ----------

    /** Sinh OTP 6 chữ số, lưu kèm TTL 15', trả về để gửi mail. Cấp mã mới thì reset bộ đếm sai. */
    public String issueEmailOtp(String email) {
        String otp = "%06d".formatted(secureRandom.nextInt(1_000_000)); // giữ số 0 đầu
        redis.opsForValue().set(KEY_EMAIL_OTP + email, otp, OTP_TTL);
        redis.delete(KEY_OTP_ATTEMPT + email);
        return otp;
    }

    /**
     * Đối chiếu OTP có giới hạn số lần thử.
     * <p>OTP chỉ 6 chữ số = 1 triệu khả năng — trong 15 phút TTL, kẻ tấn công thừa sức quét hết
     * nếu không chặn. Sai quá {@value #MAX_OTP_ATTEMPTS} lần thì hủy luôn mã, buộc gửi lại.
     * <p>So sánh bằng {@link MessageDigest#isEqual} (hằng thời gian) thay vì
     * {@code String.equals} — {@code equals} thoát sớm ở ký tự khác đầu tiên, rò rỉ thông tin
     * về tiền tố đúng qua thời gian phản hồi.
     */
    public boolean matchesEmailOtp(String email, String otp) {
        String stored = redis.opsForValue().get(KEY_EMAIL_OTP + email);
        if (stored == null) {
            return false;
        }

        boolean matched = MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                otp.getBytes(StandardCharsets.UTF_8));

        if (!matched) {
            Long attempts = redis.opsForValue().increment(KEY_OTP_ATTEMPT + email);
            if (attempts != null && attempts == 1L) {
                redis.expire(KEY_OTP_ATTEMPT + email, OTP_TTL);
            }
            if (attempts != null && attempts >= MAX_OTP_ATTEMPTS) {
                clearEmailOtp(email); // đốt mã, không cho quét tiếp
            }
        }
        return matched;
    }

    public void clearEmailOtp(String email) {
        redis.delete(KEY_EMAIL_OTP + email);
        redis.delete(KEY_OTP_ATTEMPT + email);
    }

    /**
     * Xin quyền gửi lại OTP — hai tầng chặn, tiêu tốn quota NGAY CẢ KHI email không tồn tại.
     * <p>Đó là chủ ý: nếu chỉ giới hạn khi email có thật, thời gian/kết quả phản hồi sẽ khác nhau
     * giữa email tồn tại và không tồn tại → thành công cụ dò danh sách email (user enumeration).
     * <ul>
     *   <li><b>Cooldown {@value #RESEND_COOLDOWN_SECONDS}s</b>: chặn bấm nút liên tục. Dùng SETNX
     *       (nguyên tử) nên nhiều request đồng thời chỉ một cái lọt qua.</li>
     *   <li><b>Quota {@value #MAX_OTP_RESENDS_PER_HOUR} lần/giờ</b>: chặn kẻ xấu biến hệ thống thành
     *       công cụ spam người khác và đốt sạch hạn mức SMTP (Gmail free ~500 mail/ngày).</li>
     * </ul>
     *
     * @return true nếu được phép gửi
     */
    public boolean tryAcquireOtpResend(String email) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(KEY_OTP_RESEND_COOLDOWN + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            return false; // còn trong cooldown
        }

        String quotaKey = KEY_OTP_RESEND_QUOTA + email;
        Long used = redis.opsForValue().increment(quotaKey);
        if (used != null && used == 1L) {
            redis.expire(quotaKey, RESEND_QUOTA_WINDOW); // cửa sổ 1h tính từ lần gửi đầu
        }
        return used == null || used <= MAX_OTP_RESENDS_PER_HOUR;
    }

    // ---------- Token đặt lại mật khẩu ----------

    /** Token là UUID ngẫu nhiên, KHÔNG phải JWT: cần thu hồi được ngay sau khi dùng 1 lần. */
    public String issueResetToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PASSWORD_RESET + token, userId.toString(), RESET_TTL);
        return token;
    }

    public Optional<UUID> consumeResetToken(String token) {
        String key = KEY_PASSWORD_RESET + token;
        String userId = redis.opsForValue().getAndDelete(key); // đọc + xóa nguyên tử → dùng 1 lần
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    // ---------- Chống brute-force đăng nhập ----------

    public boolean isLoginLocked(String email) {
        String count = redis.opsForValue().get(KEY_LOGIN_ATTEMPT + email);
        if (count == null) {
            return false;
        }
        try {
            return Integer.parseInt(count) >= MAX_LOGIN_ATTEMPTS;
        } catch (NumberFormatException e) {
            // Giá trị rác trong Redis (ai đó ghi tay, key trùng) không được phép chặn đăng nhập.
            redis.delete(KEY_LOGIN_ATTEMPT + email);
            return false;
        }
    }

    /** INCR nguyên tử; chỉ set TTL ở lần sai đầu tiên để cửa sổ 15' tính từ lần sai đầu. */
    public void recordLoginFailure(String email) {
        String key = KEY_LOGIN_ATTEMPT + email;
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, LOCK_TTL);
        }
    }

    public void clearLoginFailures(String email) {
        redis.delete(KEY_LOGIN_ATTEMPT + email);
    }
}