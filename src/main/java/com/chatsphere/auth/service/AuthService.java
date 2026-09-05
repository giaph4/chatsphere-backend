package com.chatsphere.auth.service;

import com.chatsphere.auth.domain.RefreshToken;
import com.chatsphere.auth.dto.*;
import com.chatsphere.auth.event.EmailVerificationRequestedEvent;
import com.chatsphere.auth.event.PasswordResetRequestedEvent;
import com.chatsphere.auth.repository.RefreshTokenRepository;
import com.chatsphere.auth.security.JwtProperties;
import com.chatsphere.auth.security.JwtTokenProvider;
import com.chatsphere.auth.security.TokenHasher;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.email.EmailService;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenHasher tokenHasher;
    private final AuthTokenStore tokenStore;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // Kiểm tra trước để trả mã lỗi rõ ràng; unique index ở DB vẫn là chốt chặn cuối
        // (2 request đồng thời có thể cùng qua được check này).
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setStatus(UserStatus.PENDING_VERIFICATION); // chưa xác thực email thì chưa cho đăng nhập
        userRepository.save(user);

        String otp = tokenStore.issueEmailOtp(email);
        eventPublisher.publishEvent(
                new EmailVerificationRequestedEvent(email, user.getDisplayName(), otp));
    }

    /**
     * Gửi lại OTP xác thực. Cần thiết vì {@code EmailService} nuốt lỗi SMTP (không làm sập luồng
     * đăng ký) — không có endpoint này, user đăng ký lúc mail server hỏng sẽ kẹt vĩnh viễn:
     * email đã chiếm chỗ trong bảng users mà không có cách nào lấy được mã.
     * <p>Giống {@link #forgotPassword}, hàm này KHÔNG tiết lộ email có tồn tại hay không:
     * email lạ hoặc đã xác thực rồi đều trả về im lặng, controller luôn trả 200.
     * Riêng lỗi vượt hạn mức thì trả 429 — hạn mức tính theo email bất kể email có thật hay không,
     * nên vẫn không rò rỉ thông tin.
     */
    @Transactional(readOnly = true)
    public void resendVerificationOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());

        // Xin quota TRƯỚC khi tra DB → thời gian phản hồi không phụ thuộc email có tồn tại hay không.
        if (!tokenStore.tryAcquireOtpResend(email)) {
            throw new BusinessException(ErrorCode.TOO_MANY_OTP_REQUESTS);
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.debug("resendOtp bỏ qua: email không tồn tại hoặc đã xác thực");
            return;
        }

        String otp = tokenStore.issueEmailOtp(email); // cấp mã MỚI, mã cũ bị ghi đè
        eventPublisher.publishEvent(
                new EmailVerificationRequestedEvent(email, user.getDisplayName(), otp));
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String email = normalizeEmail(request.email());

        if (!tokenStore.matchesEmailOtp(email, request.otp())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        tokenStore.clearEmailOtp(email); // OTP dùng 1 lần
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String deviceInfo) {
        String email = normalizeEmail(request.email());

        if (tokenStore.isLoginLocked(email)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        // Email không tồn tại và mật khẩu sai trả CÙNG một lỗi → không cho kẻ tấn công dò xem
        // email nào có trong hệ thống (user enumeration).
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            tokenStore.recordLoginFailure(email);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Mật khẩu đúng rồi mới xét trạng thái — lúc này tiết lộ trạng thái là an toàn.
        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
            case LOCKED, DEACTIVATED -> throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            case ACTIVE -> { /* hợp lệ, đi tiếp */ }
        }

        tokenStore.clearLoginFailures(email);
        user.setLastLoginAt(Instant.now());
        return issueTokenPair(user, deviceInfo);
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request, String deviceInfo) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(tokenHasher.sha256Hex(request.refreshToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // Token đã thu hồi mà vẫn được trình ra = dấu hiệu bị đánh cắp (token reuse detection,
        // OAuth 2.0 Security BCP §4.14.2): người dùng thật đã xoay token này rồi, nên bên trình ra
        // đây là bản sao. Không biết bản sao nằm ở phía nào → thu hồi TOÀN BỘ phiên của user,
        // buộc đăng nhập lại. Thà phiền một lần còn hơn để kẻ trộm bám phiên.
        if (stored.isRevoked()) {
            // Chạy ở transaction RIÊNG: ngay sau đây ta ném exception, nếu dùng chung transaction
            // thì rollback sẽ hủy luôn việc thu hồi (xem RefreshTokenRevoker).
            UUID victimId = stored.getUser().getId();
            int killed = refreshTokenRevoker.revokeAllForUser(victimId);
            log.warn("Phát hiện dùng lại refresh token đã thu hồi, userId={}, đã hủy {} phiên",
                    victimId, killed);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Rotation: token cũ chết ngay khi đổi được token mới. Nếu token đã bị đánh cắp,
        // kẻ trộm dùng trước thì lần dùng của user thật sẽ trượt → phát hiện được bất thường.
        stored.setRevoked(true);
        return issueTokenPair(stored.getUser(), deviceInfo);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        // Idempotent: token không tồn tại / đã revoke vẫn coi là đăng xuất thành công.
        refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(request.refreshToken()))
                .ifPresent(token -> token.setRevoked(true));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> user = userRepository.findByEmail(email);

        if (user.isEmpty()) {
            // Cố tình KHÔNG ném lỗi: endpoint này không đăng nhập được vẫn gọi được, nếu báo
            // "email không tồn tại" thì thành công cụ dò danh sách email. Controller luôn trả 200.
            log.debug("forgotPassword cho email không tồn tại, bỏ qua âm thầm");
            return;
        }

        String token = tokenStore.issueResetToken(user.get().getId());
        eventPublisher.publishEvent(
                new PasswordResetRequestedEvent(email, user.get().getDisplayName(), token));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        UUID userId = tokenStore.consumeResetToken(request.token())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByUserId(userId); // đá mọi thiết bị ra, buộc đăng nhập lại
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        // Phòng thủ: nếu ai đó lỡ tay mở endpoint này ra public trong SecurityConfig thì
        // principal sẽ là null. Trả 401 thay vì NullPointerException -> 500.
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_OLD_PASSWORD);
        }
        // Ràng buộc liên trường — annotation trên DTO không diễn đạt được, xử ở đây.
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /** Cấp cặp access + refresh mới, lưu HASH của refresh xuống DB. */
    private LoginResponse issueTokenPair(User user, String deviceInfo) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(tokenHasher.sha256Hex(rawRefreshToken)); // raw KHÔNG chạm tới DB
        entity.setDeviceInfo(truncate(deviceInfo, 255));
        entity.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpirationMs()));
        refreshTokenRepository.save(entity);

        // raw token chỉ tồn tại trong response này; server sau đó không còn cách nào đọc lại được.
        return new LoginResponse(accessToken, rawRefreshToken,
                jwtProperties.accessExpirationMs() / 1000); // ms -> giây
    }

    /**
     * Email lưu và tra cứu ở dạng chữ thường để "A@x.com" và "a@x.com" là một tài khoản.
     * <p>Locale.ROOT là bắt buộc: {@code toLowerCase()} không tham số dùng locale mặc định của JVM,
     * và ở locale Thổ Nhĩ Kỳ "I" hạ thành "ı" (không chấm) → cùng một email cho ra 2 chuỗi khác
     * nhau tùy máy chủ chạy ở đâu.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}