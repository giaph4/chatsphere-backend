package com.chatsphere.auth.service;

import com.chatsphere.auth.domain.RefreshToken;
import com.chatsphere.auth.dto.ChangePasswordRequest;
import com.chatsphere.auth.dto.ForgotPasswordRequest;
import com.chatsphere.auth.dto.LoginRequest;
import com.chatsphere.auth.dto.RefreshTokenRequest;
import com.chatsphere.auth.dto.RegisterRequest;
import com.chatsphere.auth.dto.VerifyEmailRequest;
import com.chatsphere.auth.repository.RefreshTokenRepository;
import com.chatsphere.auth.security.JwtProperties;
import com.chatsphere.auth.security.JwtTokenProvider;
import com.chatsphere.auth.security.TokenHasher;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test thuần Mockito — không dựng Spring context, chạy trong mili giây.
 * Integration test lo phần "ráp nối"; ở đây chỉ lo "luật nghiệp vụ" và các nhánh lỗi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private TokenHasher tokenHasher;
    @Mock private AuthTokenStore tokenStore;
    @Mock private RefreshTokenRevoker refreshTokenRevoker;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AuthService authService;

    // ---------- Đăng ký ----------

    @Test
    void register_emailDaTonTai_neEmailAlreadyExists() {
        when(userRepository.existsByEmail("a@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("A@X.com", "Password1", "user", "User")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void register_usernameDaTonTai_neUsernameAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("user")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("a@x.com", "Password1", "user", "User")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    @Test
    void register_emailVietHoaCoKhoangTrang_duocChuanHoa_vaTrangThaiLaChuaXacThuc() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(tokenStore.issueEmailOtp(anyString())).thenReturn("123456");

        authService.register(new RegisterRequest("  A@X.CoM ", "Password1", "user", "User"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("a@x.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed"); // không lưu plaintext
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    // ---------- Xác thực email ----------

    @Test
    void verifyEmail_otpSai_neInvalidVerificationToken_vaKhongXoaOtp() {
        when(tokenStore.matchesEmailOtp("a@x.com", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("a@x.com", "000000")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_VERIFICATION_TOKEN);

        verify(userRepository, never()).findByEmail(anyString());
        verify(tokenStore, never()).clearEmailOtp(anyString());
    }

    @Test
    void verifyEmail_otpDung_chuyenSangActive_vaDotOtp() {
        User user = userWithStatus(UserStatus.PENDING_VERIFICATION);
        when(tokenStore.matchesEmailOtp("a@x.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));

        authService.verifyEmail(new VerifyEmailRequest("a@x.com", "123456"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(tokenStore).clearEmailOtp("a@x.com");
    }

    // ---------- Đăng nhập ----------

    @Test
    void login_daKhoaViSaiQuaNhieu_neTooManyAttempts_vaKhongChamDb() {
        when(tokenStore.isLoginLocked("a@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.com", "x"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);

        // Chốt chặn phải nằm TRƯỚC truy vấn DB, nếu không kẻ tấn công vẫn ép DB làm việc.
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_emailKhongTonTai_traInvalidCredentials_vaTangBoDem() {
        when(tokenStore.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@x.com", "x"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                // KHÔNG phải USER_NOT_FOUND — chống dò email tồn tại (user enumeration)
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(tokenStore).recordLoginFailure("ghost@x.com");
    }

    @Test
    void login_saiMatKhau_traInvalidCredentials_vaTangBoDem() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(tokenStore.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sai", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.com", "sai"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(tokenStore).recordLoginFailure("a@x.com");
    }

    @Test
    void login_chuaXacThucEmail_neEmailNotVerified() {
        User user = userWithStatus(UserStatus.PENDING_VERIFICATION);
        when(tokenStore.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.com", "Password1"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    void login_taiKhoanBiKhoa_neAccountLocked() {
        User user = userWithStatus(UserStatus.LOCKED);
        when(tokenStore.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@x.com", "Password1"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void login_thanhCong_luuHashChuKhongLuuRawToken() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(tokenStore.isLoginLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("access.jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(tokenHasher.sha256Hex("raw-refresh")).thenReturn("hash-refresh");
        when(jwtProperties.accessExpirationMs()).thenReturn(900_000L);
        when(jwtProperties.refreshExpirationMs()).thenReturn(604_800_000L);

        var response = authService.login(new LoginRequest("a@x.com", "Password1"), "JUnit/1.0");

        assertThat(response.accessToken()).isEqualTo("access.jwt");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh"); // raw chỉ trả cho client
        assertThat(response.expiresIn()).isEqualTo(900);              // ms -> giây
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(tokenStore).clearLoginFailures("a@x.com");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash-refresh");
        assertThat(captor.getValue().getDeviceInfo()).isEqualTo("JUnit/1.0");
    }

    // ---------- Refresh ----------

    @Test
    void refresh_tokenKhongTonTai_neInvalidRefreshToken() {
        when(tokenHasher.sha256Hex("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refresh_tokenDaThuHoi_thuHoiLuonToanBoPhienCuaUser() {
        User user = userWithStatus(UserStatus.ACTIVE);
        RefreshToken revoked = new RefreshToken();
        revoked.setUser(user);
        revoked.setRevoked(true);
        revoked.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenHasher.sha256Hex("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        // Token reuse detection: coi như token bị đánh cắp -> đá mọi phiên (transaction riêng).
        verify(refreshTokenRevoker).revokeAllForUser(user.getId());
    }

    @Test
    void refresh_tokenHetHan_neRefreshTokenExpired() {
        RefreshToken expired = new RefreshToken();
        expired.setUser(userWithStatus(UserStatus.ACTIVE));
        expired.setRevoked(false);
        expired.setExpiresAt(Instant.now().minusSeconds(1));

        when(tokenHasher.sha256Hex("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        verify(refreshTokenRevoker, never()).revokeAllForUser(any());
    }

    @Test
    void refresh_hopLe_thuHoiTokenCu_vaCapTokenMoi() {
        RefreshToken valid = new RefreshToken();
        valid.setUser(userWithStatus(UserStatus.ACTIVE));
        valid.setRevoked(false);
        valid.setExpiresAt(Instant.now().plusSeconds(3600));

        when(tokenHasher.sha256Hex(anyString())).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(valid));
        when(jwtTokenProvider.generateAccessToken(any(), any())).thenReturn("access.jwt");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-new");
        when(jwtProperties.accessExpirationMs()).thenReturn(900_000L);
        when(jwtProperties.refreshExpirationMs()).thenReturn(604_800_000L);

        authService.refresh(new RefreshTokenRequest("raw-old"), null);

        assertThat(valid.isRevoked()).isTrue(); // rotation
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ---------- Đăng xuất ----------

    @Test
    void logout_tokenKhongTonTai_khongNeLoi() {
        when(tokenHasher.sha256Hex("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        authService.logout(new RefreshTokenRequest("raw")); // idempotent, không ném gì
    }

    // ---------- Quên / đổi mật khẩu ----------

    @Test
    void forgotPassword_emailKhongTonTai_khongNeLoi_vaKhongGuiMail() {
        when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("ghost@x.com"));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void changePassword_khongCoPrincipal_neUnauthorized() {
        assertThatThrownBy(() -> authService.changePassword(
                null, new ChangePasswordRequest("old", "NewPassword9")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void changePassword_saiMatKhauCu_neWrongOldPassword() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("sai", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), new ChangePasswordRequest("sai", "NewPassword9")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WRONG_OLD_PASSWORD);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void changePassword_matKhauMoiTrungMatKhauCu_neValidationError() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), new ChangePasswordRequest("Password1", "Password1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void changePassword_thanhCong_doiHash_vaThuHoiMoiPhien() {
        User user = userWithStatus(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword9", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword9")).thenReturn("hashed-new");

        authService.changePassword(user.getId(), new ChangePasswordRequest("Password1", "NewPassword9"));

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    // ---------- helper ----------

    private User userWithStatus(UserStatus status) {
        User user = new User();
        // id nằm ở BaseEntity và chỉ có @Getter (JPA tự sinh) → test phải set qua reflection.
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("a@x.com");
        user.setPasswordHash("hashed");
        user.setUsername("user");
        user.setDisplayName("User");
        user.setStatus(status);
        return user;
    }
}
