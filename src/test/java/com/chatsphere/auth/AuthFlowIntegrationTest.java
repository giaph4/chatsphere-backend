package com.chatsphere.auth;

import com.chatsphere.auth.event.EmailVerificationRequestedEvent;
import com.chatsphere.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nghiệm thu Phase 1 (03_CODE_ROADMAP.md §1 "Hoàn thành khi"):
 * đăng ký -> nhận OTP -> xác thực -> đăng nhập -> nhận JWT -> gọi API bảo vệ bằng token đó.
 * <p>Kèm 2 assertion phủ định giữ cho bảo mật không bị hồi quy: chưa xác thực thì không đăng nhập
 * được, không có token thì không đổi mật khẩu được.
 */
@RecordApplicationEvents
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "flow@chatsphere.test";
    private static final String PASSWORD = "Password1";

    @Autowired
    private MockMvc mockMvc;

    /** Bắt event ngay lúc publish (đồng bộ) — không phải chờ listener @Async chạy xong. */
    @Autowired
    private ApplicationEvents events;

    /** Chặn mọi kết nối SMTP thật trong test. */
    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void dangKy_xacThuc_dangNhap_goiApiBaoVe() throws Exception {
        // --- 1. Đăng ký -> 201 ---
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "username": "flowuser",
                                  "display_name": "Flow User"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // --- 2. Lấy OTP từ event, không đụng tới mail ---
        String otp = events.stream(EmailVerificationRequestedEvent.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có event gửi OTP"))
                .otp();
        assertThat(otp).hasSize(6).containsOnlyDigits();

        // --- 3. Chưa xác thực thì KHÔNG được đăng nhập ---
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        // --- 4. Xác thực email bằng OTP ---
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "otp": "%s" }
                                """.formatted(EMAIL, otp)))
                .andExpect(status().isOk());

        // --- 5. Đăng nhập -> nhận cặp token ---
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.data.expires_in").value(900))
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(body, "$.data.access_token");
        String refreshToken = JsonPath.read(body, "$.data.refresh_token");

        // --- 6. Không token -> 401 (chốt chặn SecurityConfig) ---
        mockMvc.perform(put("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // --- 7. Có token -> 200 ---
        mockMvc.perform(put("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changePasswordBody()))
                .andExpect(status().isOk());

        // --- 8. Đổi mật khẩu đã thu hồi mọi refresh token -> refresh phải trượt ---
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "%s" }
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshToken_xoayVong_tokenCuKhongDungLaiDuoc() throws Exception {
        String email = "rotate@chatsphere.test";
        registerAndVerify(email, "rotateuser");

        String first = login(email);
        String firstRefresh = JsonPath.read(first, "$.data.refresh_token");

        // Lần 1: đổi được, nhận token mới
        String second = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "%s" }
                                """.formatted(firstRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondRefresh = JsonPath.read(second, "$.data.refresh_token");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Lần 2 với token CŨ: bị từ chối (rotation)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "%s" }
                                """.formatted(firstRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        // ...và vì đó là dấu hiệu token bị đánh cắp, token MỚI cũng phải bị thu hồi theo.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "%s" }
                                """.formatted(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_dtoKhongHopLe_tra400VaChiRoField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "khong-phai-email",
                                  "password": "yeu",
                                  "username": "a",
                                  "display_name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- helper ----------

    private void registerAndVerify(String email, String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "username": "%s",
                                  "display_name": "Test User"
                                }
                                """.formatted(email, PASSWORD, username)))
                .andExpect(status().isCreated());

        String otp = events.stream(EmailVerificationRequestedEvent.class)
                .filter(e -> e.email().equals(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có event gửi OTP cho " + email))
                .otp();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "otp": "%s" }
                                """.formatted(email, otp)))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String loginBody() {
        return """
                { "email": "%s", "password": "%s" }
                """.formatted(EMAIL, PASSWORD);
    }

    private String changePasswordBody() {
        return """
                { "old_password": "%s", "new_password": "NewPassword9" }
                """.formatted(PASSWORD);
    }
}
