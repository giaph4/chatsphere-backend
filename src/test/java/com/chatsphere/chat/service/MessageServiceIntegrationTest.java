package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.MessageStatus;
import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.CreateGroupRequest;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.repository.ConversationRepository;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.CursorPageResponse;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.support.AbstractIntegrationTest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import com.chatsphere.user.service.BlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockService blockService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User persistActiveUser(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setPasswordHash("irrelevant-hash");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private SendMessageRequest textMessage(String content) {
        return new SendMessageRequest(MessageType.TEXT, content, null);
    }

    // ---------- sendMessage ----------

    @Test
    void gui_tin_nhan_cap_nhat_lastMessage_cua_conversation() {
        User alice = persistActiveUser("malice1");
        User bob = persistActiveUser("mbob1");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());

        MessageResponse sent = messageService.sendMessage(alice.getId(), direct.id(), textMessage("Chào Bob"));

        var reloaded = conversationRepository.findById(direct.id()).orElseThrow();
        assertThat(reloaded.getLastMessage().getId()).isEqualTo(sent.id());
        assertThat(sent.content()).isEqualTo("Chào Bob");
        assertThat(sent.sender().username()).isEqualTo("malice1");
    }

    @Test
    void gui_tin_nhan_khi_khong_phai_thanh_vien_bi_tu_choi() {
        User alice = persistActiveUser("malice2");
        User bob = persistActiveUser("mbob2");
        User outsider = persistActiveUser("moutsider2");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());

        assertThatThrownBy(() -> messageService.sendMessage(outsider.getId(), direct.id(), textMessage("hi")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER);
    }

    @Test
    void gui_tin_nhan_direct_khi_bi_chan_thi_tu_choi() {
        User alice = persistActiveUser("malice3");
        User bob = persistActiveUser("mbob3");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        blockService.block(bob.getId(), alice.getId());

        assertThatThrownBy(() -> messageService.sendMessage(alice.getId(), direct.id(), textMessage("hi")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);
    }

    @Test
    void gui_tin_nhan_group_khi_2_thanh_vien_chan_nhau_van_gui_duoc() {
        User creator = persistActiveUser("mcreator4");
        User memberB = persistActiveUser("mmemberb4");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm chat", List.of(memberB.getId())));
        // Chặn lẫn nhau nhưng vẫn cùng ở trong nhóm — theo đúng phạm vi 03_CODE_ROADMAP.md 3.3:
        // block chỉ chặn nhắn tin DIRECT, KHÔNG áp dụng cho GROUP.
        blockService.block(memberB.getId(), creator.getId());

        MessageResponse sent = messageService.sendMessage(creator.getId(), group.id(), textMessage("vẫn gửi được"));

        assertThat(sent.content()).isEqualTo("vẫn gửi được");
    }

    // ---------- getMessages (cursor pagination) ----------

    @Test
    void phan_trang_lich_su_tin_nhan_theo_cursor_lay_du_va_dung_thu_tu() {
        User alice = persistActiveUser("malice5");
        User bob = persistActiveUser("mbob5");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());

        List<UUID> sentIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            sentIds.add(messageService.sendMessage(alice.getId(), direct.id(), textMessage("msg" + i)).id());
        }

        List<UUID> collected = new ArrayList<>();
        UUID cursor = null;
        int guard = 0;
        while (true) {
            CursorPageResponse<MessageResponse> page =
                    messageService.getMessages(alice.getId(), direct.id(), cursor, 2);
            page.items().forEach(m -> collected.add(m.id()));
            if (!page.hasNext()) {
                break;
            }
            cursor = page.nextCursor();
            if (++guard > 10) {
                throw new AssertionError("Vòng lặp phân trang không kết thúc — nghi ngờ lỗi cursor");
            }
        }

        // Trang mới nhất trước -> collected phải là sentIds đảo ngược.
        List<UUID> expected = new ArrayList<>(sentIds);
        java.util.Collections.reverse(expected);
        assertThat(collected).containsExactlyElementsOf(expected);
    }

    // ---------- recallMessage ----------

    @Test
    void thu_hoi_tin_nhan_thanh_cong_xoa_noi_dung_that_trong_db() {
        User alice = persistActiveUser("malice6");
        User bob = persistActiveUser("mbob6");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        MessageResponse sent = messageService.sendMessage(alice.getId(), direct.id(), textMessage("bí mật"));

        MessageResponse recalled = messageService.recallMessage(alice.getId(), sent.id());

        assertThat(recalled.status()).isEqualTo(MessageStatus.RECALLED);
        assertThat(recalled.content()).isNull();
    }

    @Test
    void thu_hoi_tin_nhan_khong_phai_nguoi_gui_bi_tu_choi() {
        User alice = persistActiveUser("malice7");
        User bob = persistActiveUser("mbob7");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        MessageResponse sent = messageService.sendMessage(alice.getId(), direct.id(), textMessage("của alice"));

        assertThatThrownBy(() -> messageService.recallMessage(bob.getId(), sent.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_RECALL_FORBIDDEN);
    }

    @Test
    void thu_hoi_tin_nhan_2_lan_lan_2_bao_da_thu_hoi() {
        User alice = persistActiveUser("malice8");
        User bob = persistActiveUser("mbob8");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        MessageResponse sent = messageService.sendMessage(alice.getId(), direct.id(), textMessage("x"));
        messageService.recallMessage(alice.getId(), sent.id());

        assertThatThrownBy(() -> messageService.recallMessage(alice.getId(), sent.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_ALREADY_RECALLED);
    }

    @Test
    void thu_hoi_tin_nhan_qua_5_phut_bi_tu_choi() {
        User alice = persistActiveUser("malice9");
        User bob = persistActiveUser("mbob9");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        MessageResponse sent = messageService.sendMessage(alice.getId(), direct.id(), textMessage("cũ rồi"));

        // created_at có updatable=false ở tầng JPA (BaseEntity) -> không backdate được qua
        // repository.save(); phải UPDATE thẳng bằng SQL để mô phỏng "tin nhắn gửi từ 6 phút trước".
        jdbcTemplate.update("UPDATE messages SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(6))), sent.id());

        assertThatThrownBy(() -> messageService.recallMessage(alice.getId(), sent.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MESSAGE_RECALL_WINDOW_EXPIRED);
    }
}
