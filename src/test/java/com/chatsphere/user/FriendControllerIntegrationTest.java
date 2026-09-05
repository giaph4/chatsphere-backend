package com.chatsphere.user;

import com.chatsphere.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nghiệm thu Phase 2.4 qua HTTP thật (MockMvc), không gọi thẳng service như
 * FriendServiceIntegrationTest — mục đích riêng của lớp test này là xác nhận controller,
 * SecurityConfig (endpoint đòi hỏi JWT) và JSON snake_case nối đúng với service layer.
 */
@RecordApplicationEvents
class FriendControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubMimeMessageCreation() {
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void chua_dang_nhap_thi_bi_tu_choi_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void luong_ket_ban_day_du_qua_http() throws Exception {
        String aliceToken = registerVerifyLogin("alicehttp", "alicehttp@test.local");
        String bobToken = registerVerifyLogin("bobhttp", "bobhttp@test.local");

        // --- GET /users/me trả đúng field, KHÔNG lộ password_hash ---
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alicehttp"))
                .andExpect(jsonPath("$.data.email").value("alicehttp@test.local"));

        // --- Alice tìm Bob — phải thấy relationship = NONE ---
        MvcResult searchResult = mockMvc.perform(get("/api/v1/users/search")
                        .param("q", "bobhttp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].user.username").value("bobhttp"))
                .andExpect(jsonPath("$.data.items[0].relationship").value("NONE"))
                // hồ sơ rút gọn KHÔNG được có field email
                .andExpect(jsonPath("$.data.items[0].user.email").doesNotExist())
                .andReturn();
        UUID bobId = extractUserId(searchResult, "$.data.items[0].user.id");

        // --- Alice gửi lời mời kết bạn cho Bob ---
        String sendBody = mockMvc.perform(post("/api/v1/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_id\":\"%s\"}".formatted(bobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(sendBody, "$.data.id");

        // --- Bob thấy lời mời trong danh sách "received" ---
        mockMvc.perform(get("/api/v1/friend-requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sender.username").value("alicehttp"));

        // --- Alice (KHÔNG phải người nhận) không được accept lời mời của chính mình ---
        mockMvc.perform(put("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_REQUEST_NOT_FOUND"));

        // --- Bob chấp nhận ---
        mockMvc.perform(put("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // --- Accept lần 2 (double-click) -> 409, không tạo Friendship trùng ---
        mockMvc.perform(put("/api/v1/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIEND_REQUEST_NOT_PENDING"));

        // --- Cả 2 đều thấy nhau trong danh sách bạn bè ---
        mockMvc.perform(get("/api/v1/friends").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].user.username").value("bobhttp"))
                .andExpect(jsonPath("$.data.total_elements").value(1));

        mockMvc.perform(get("/api/v1/friends").header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].user.username").value("alicehttp"));

        // --- Tìm lại: giờ phải thấy relationship = FRIEND ---
        mockMvc.perform(get("/api/v1/users/search").param("q", "bobhttp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(jsonPath("$.data.items[0].relationship").value("FRIEND"));

        // --- Alice chặn Bob -> hủy luôn bạn bè ---
        mockMvc.perform(post("/api/v1/users/{id}/block", bobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/friends").header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(jsonPath("$.data.total_elements").value(0));

        // --- Bob (đang bị chặn) gửi lại lời mời cho Alice -> 403, KHÔNG tiết lộ ai chặn ai ---
        mockMvc.perform(post("/api/v1/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_id\":\"%s\"}".formatted(extractSelfId(aliceToken))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_BLOCKED"));

        // --- Alice bỏ chặn ---
        mockMvc.perform(delete("/api/v1/users/{id}/block", bobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk());
    }

    @Test
    void doi_settings_rieng_tu_qua_http() throws Exception {
        String token = registerVerifyLogin("settingsuser", "settingsuser@test.local");

        // Lần đầu đọc -> tự tạo mặc định EVERYONE/EVERYONE/true
        mockMvc.perform(get("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online_visibility").value("EVERYONE"))
                .andExpect(jsonPath("$.data.notification_enabled").value(true));

        mockMvc.perform(put("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "online_visibility": "FRIENDS_ONLY", "call_permission": "NOBODY", "notification_enabled": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online_visibility").value("FRIENDS_ONLY"))
                .andExpect(jsonPath("$.data.call_permission").value("NOBODY"))
                .andExpect(jsonPath("$.data.notification_enabled").value(false));
    }

    @Test
    void tu_ket_ban_voi_chinh_minh_tra_400() throws Exception {
        String token = registerVerifyLogin("selfuser", "selfuser@test.local");
        UUID selfId = extractSelfId(token);

        mockMvc.perform(post("/api/v1/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_id\":\"%s\"}".formatted(selfId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_FRIEND_SELF"));
    }

    @Test
    void tim_kiem_thieu_tu_khoa_tra_400() throws Exception {
        String token = registerVerifyLogin("blankqueryuser", "blankqueryuser@test.local");

        mockMvc.perform(get("/api/v1/users/search").param("q", "  ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- helper ----------

    private String registerVerifyLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s", "username": "%s", "display_name": "%s" }
                                """.formatted(email, PASSWORD, username, username)))
                .andExpect(status().isCreated());

        String otp = events.stream(com.chatsphere.auth.event.EmailVerificationRequestedEvent.class)
                .filter(e -> e.email().equals(email))
                .reduce((first, second) -> second) // lấy event MỚI NHẤT cho email này
                .orElseThrow(() -> new AssertionError("Không có OTP event cho " + email))
                .otp();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"otp\": \"%s\" }".formatted(email, otp)))
                .andExpect(status().isOk());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(loginBody, "$.data.access_token");
    }

    private UUID extractSelfId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private UUID extractUserId(MvcResult result, String jsonPath) throws Exception {
        String body = result.getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, jsonPath));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
