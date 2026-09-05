package com.chatsphere.chat;

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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nghiệm thu Phase 3 qua HTTP thật — kịch bản đúng như "Kiểm tra hoàn thành Phase 3" ở
 * 03_CODE_ROADMAP.md: tạo group 3 người → gửi tin nhắn → lấy lịch sử phân trang → thu hồi
 * tin nhắn → rời nhóm. Các quy tắc nghiệp vụ chi tiết (race condition, ràng buộc DB...) đã
 * được phủ ở ConversationServiceIntegrationTest/MessageServiceIntegrationTest; lớp này chỉ
 * xác nhận controller + SecurityConfig + JSON snake_case nối đúng.
 */
@RecordApplicationEvents
class ChatControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private JavaMailSender mailSender;

    @org.junit.jupiter.api.BeforeEach
    void stubMimeMessageCreation() {
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void luong_day_du_tao_nhom_gui_tin_phan_trang_thu_hoi_roi_nhom() throws Exception {
        String aliceToken = registerVerifyLogin("chatalice", "chatalice@test.local");
        String bobToken = registerVerifyLogin("chatbob", "chatbob@test.local");
        String carolToken = registerVerifyLogin("chatcarol", "chatcarol@test.local");
        UUID bobId = extractSelfId(bobToken);
        UUID carolId = extractSelfId(carolToken);

        // --- Tạo group 3 người: alice tạo -> tự động ADMIN ---
        // Nội dung request CỐ Ý dùng ASCII (không dấu tiếng Việt): MockMvc#content(String) encode
        // bằng charset MẶC ĐỊNH của JVM (không phải UTF-8) khi không set .characterEncoding() —
        // gửi tiếng Việt qua đây sẽ bị vỡ dấu, khác hẳn app thật (client luôn gửi UTF-8 qua HTTP).
        String createBody = mockMvc.perform(post("/api/v1/conversations/group")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Group of 3", "member_ids": ["%s", "%s"] }
                                """.formatted(bobId, carolId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("GROUP"))
                .andExpect(jsonPath("$.data.participants.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(createBody, "$.data.id");

        // --- Gửi tin nhắn: alice, rồi bob, rồi carol (đủ dữ liệu để test phân trang) ---
        sendMessage(aliceToken, conversationId, "Hello everyone");
        sendMessage(bobToken, conversationId, "Hi alice");
        String thirdMessageId = sendMessageAndGetId(carolToken, conversationId, "Hi team");

        // --- Lấy lịch sử phân trang: limit=2 -> phải còn trang sau ---
        String page1 = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.has_next").value(true))
                // mới nhất trước -> tin đầu tiên trả về phải là của carol (gửi sau cùng)
                .andExpect(jsonPath("$.data.items[0].content").value("Hi team"))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(page1, "$.data.next_cursor");

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1)) // còn đúng 1 tin (của alice)
                .andExpect(jsonPath("$.data.has_next").value(false))
                .andExpect(jsonPath("$.data.items[0].content").value("Hello everyone"));

        // --- Thu hồi tin nhắn của carol — chỉ carol mới được thu hồi ---
        mockMvc.perform(put("/api/v1/messages/{id}/recall", thirdMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MESSAGE_RECALL_FORBIDDEN"));

        mockMvc.perform(put("/api/v1/messages/{id}/recall", thirdMessageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(carolToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECALLED"))
                .andExpect(jsonPath("$.data.content").doesNotExist()); // non_null inclusion -> field biến mất

        // --- Carol rời nhóm ---
        mockMvc.perform(post("/api/v1/conversations/{id}/leave", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(carolToken)))
                .andExpect(status().isOk());

        // --- Carol không còn thấy hội thoại này trong danh sách của mình ---
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(carolToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // --- Alice (admin) vẫn thấy nhóm, giờ chỉ còn 2 thành viên active ---
        mockMvc.perform(get("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].participants.length()").value(2));

        // --- Carol (đã rời) không còn gửi tin được vào nhóm ---
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(carolToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "TEXT", "content": "can I still send" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_CONVERSATION_MEMBER"));
    }

    @Test
    void tao_hoi_thoai_direct_2_lan_tra_ve_cung_1_id_qua_http() throws Exception {
        String aliceToken = registerVerifyLogin("directalice", "directalice@test.local");
        String bobToken = registerVerifyLogin("directbob", "directbob@test.local");
        UUID bobId = extractSelfId(bobToken);

        String first = mockMvc.perform(post("/api/v1/conversations/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"%s\"}".formatted(bobId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/v1/conversations/direct")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"%s\"}".formatted(bobId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThatSameConversation(first, second);
    }

    @Test
    void them_thanh_vien_boi_nguoi_khong_phai_admin_bi_tu_choi_qua_http() throws Exception {
        String aliceToken = registerVerifyLogin("permalice", "permalice@test.local");
        String bobToken = registerVerifyLogin("permbob", "permbob@test.local");
        String eveToken = registerVerifyLogin("permeve", "permeve@test.local");
        UUID bobId = extractSelfId(bobToken);
        UUID eveId = extractSelfId(eveToken);

        String createBody = mockMvc.perform(post("/api/v1/conversations/group")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"G\", \"member_ids\": [\"%s\"] }".formatted(bobId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String conversationId = JsonPath.read(createBody, "$.data.id");

        mockMvc.perform(post("/api/v1/conversations/{id}/members", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)) // bob KHÔNG phải admin
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":\"%s\"}".formatted(eveId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GROUP_ADMIN_REQUIRED"));
    }

    // ---------- helper ----------

    private void sendMessage(String token, String conversationId, String content) throws Exception {
        sendMessageAndGetId(token, conversationId, content);
    }

    private String sendMessageAndGetId(String token, String conversationId, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"type\": \"TEXT\", \"content\": \"%s\" }".formatted(content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private void assertThatSameConversation(String firstBody, String secondBody) {
        String firstId = JsonPath.read(firstBody, "$.data.id");
        String secondId = JsonPath.read(secondBody, "$.data.id");
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    private String registerVerifyLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s", "username": "%s", "display_name": "%s" }
                                """.formatted(email, PASSWORD, username, username)))
                .andExpect(status().isCreated());

        String otp = events.stream(com.chatsphere.auth.event.EmailVerificationRequestedEvent.class)
                .filter(e -> e.email().equals(email))
                .reduce((first, second) -> second)
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
