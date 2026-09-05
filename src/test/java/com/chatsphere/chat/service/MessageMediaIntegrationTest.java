package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.chat.dto.AttachmentRequest;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.CreateGroupRequest;
import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.support.AbstractIntegrationTest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 mục 5.1 phía tin nhắn: đính kèm, thả cảm xúc, chuyển tiếp, ẩn tin phía mình.
 *
 * <p>Không dùng MinIO thật: {@code fileUrl} được dựng đúng theo tiền tố bucket cấu hình ở
 * {@code application-test.yaml}, đủ để đi qua {@code MediaService.assertManagedUrl}. Phần
 * upload thật đã có {@code MediaServiceTest} phủ riêng.
 */
class MessageMediaIntegrationTest extends AbstractIntegrationTest {

    private static final String BUCKET_PREFIX = "http://localhost:9000/chatsphere-test/";

    @Autowired
    private MessageService messageService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private UserRepository userRepository;

    // ---------- Đính kèm ----------

    @Test
    void gui_tin_nhan_anh_kem_attachment() {
        User alice = persistActiveUser("mmalice1");
        User bob = persistActiveUser("mmbob1");
        UUID conversationId = direct(alice, bob);

        MessageResponse sent = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.IMAGE, null, null, List.of(attachment("anh.png"))));

        assertThat(sent.type()).isEqualTo(MessageType.IMAGE);
        assertThat(sent.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.fileName()).isEqualTo("anh.png");
            assertThat(attachment.fileType()).isEqualTo("image/png");
            assertThat(attachment.id()).isNotNull();
        });

        // Đọc lại từ DB: attachment phải đi kèm khi lấy lịch sử, không chỉ ở response lúc gửi.
        var page = messageService.getMessages(bob.getId(), conversationId, null, 10);
        assertThat(page.items()).singleElement()
                .satisfies(message -> assertThat(message.attachments()).hasSize(1));
    }

    @Test
    void tin_nhan_anh_thieu_attachment_bi_tu_choi() {
        User alice = persistActiveUser("mmalice2");
        User bob = persistActiveUser("mmbob2");
        UUID conversationId = direct(alice, bob);

        assertThatThrownBy(() -> messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.IMAGE, "chi co chu", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ATTACHMENT_REQUIRED);
    }

    @Test
    void tin_nhan_khong_chu_khong_tep_bi_tu_choi() {
        User alice = persistActiveUser("mmalice3");
        User bob = persistActiveUser("mmbob3");
        UUID conversationId = direct(alice, bob);

        assertThatThrownBy(() -> messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "   ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_REQUIRED);
    }

    @Test
    void tu_choi_attachment_tro_ra_ngoai_he_thong() {
        User alice = persistActiveUser("mmalice4");
        User bob = persistActiveUser("mmbob4");
        UUID conversationId = direct(alice, bob);

        AttachmentRequest external = new AttachmentRequest(
                "https://evil.example.com/malware.png", "anh.png", "image/png", 1024);

        assertThatThrownBy(() -> messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.IMAGE, null, null, List.of(external))))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- Reaction ----------

    @Test
    void tha_doi_va_go_reaction() {
        User alice = persistActiveUser("mmalice5");
        User bob = persistActiveUser("mmbob5");
        UUID conversationId = direct(alice, bob);
        MessageResponse message = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Tin de tha cam xuc", null));

        // Thả lần đầu
        MessageResponse afterFirst = messageService.reactToMessage(bob.getId(), message.id(), "❤️");
        assertThat(afterFirst.reactions()).singleElement().satisfies(reaction -> {
            assertThat(reaction.emoji()).isEqualTo("❤️");
            assertThat(reaction.count()).isEqualTo(1);
            assertThat(reaction.userIds()).containsExactly(bob.getId());
        });

        // Đổi emoji -> vẫn 1 reaction, không phải 2
        MessageResponse afterChange = messageService.reactToMessage(bob.getId(), message.id(), "😂");
        assertThat(afterChange.reactions()).singleElement()
                .satisfies(reaction -> assertThat(reaction.emoji()).isEqualTo("😂"));

        // Thả lại đúng emoji đang có -> gỡ
        MessageResponse afterToggleOff = messageService.reactToMessage(bob.getId(), message.id(), "😂");
        assertThat(afterToggleOff.reactions()).isEmpty();
    }

    @Test
    void reaction_duoc_gom_theo_emoji() {
        User alice = persistActiveUser("mmalice6");
        User bob = persistActiveUser("mmbob6");
        User carol = persistActiveUser("mmcarol6");
        UUID groupId = conversationService.createGroup(alice.getId(),
                new CreateGroupRequest("Nhom react", List.of(bob.getId(), carol.getId()))).id();
        MessageResponse message = messageService.sendMessage(alice.getId(), groupId,
                new SendMessageRequest(MessageType.TEXT, "Ai thich thi tha tim", null));

        messageService.reactToMessage(bob.getId(), message.id(), "❤️");
        messageService.reactToMessage(carol.getId(), message.id(), "❤️");
        MessageResponse result = messageService.reactToMessage(alice.getId(), message.id(), "😂");

        assertThat(result.reactions()).hasSize(2);
        // Nhiều nhất lên trước.
        assertThat(result.reactions().getFirst().emoji()).isEqualTo("❤️");
        assertThat(result.reactions().getFirst().count()).isEqualTo(2);
        assertThat(result.reactions().getFirst().userIds())
                .containsExactlyInAnyOrder(bob.getId(), carol.getId());
    }

    @Test
    void nguoi_ngoai_hoi_thoai_khong_the_tha_reaction() {
        User alice = persistActiveUser("mmalice7");
        User bob = persistActiveUser("mmbob7");
        User intruder = persistActiveUser("mmspy7");
        UUID conversationId = direct(alice, bob);
        MessageResponse message = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Tin rieng tu", null));

        assertThatThrownBy(() -> messageService.reactToMessage(intruder.getId(), message.id(), "❤️"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER);
    }

    // ---------- Chuyển tiếp ----------

    @Test
    void chuyen_tiep_sao_chep_noi_dung_va_dinh_kem() {
        User alice = persistActiveUser("mmalice8");
        User bob = persistActiveUser("mmbob8");
        User carol = persistActiveUser("mmcarol8");
        UUID source = direct(alice, bob);
        UUID target = direct(alice, carol);

        MessageResponse origin = messageService.sendMessage(alice.getId(), source,
                new SendMessageRequest(MessageType.IMAGE, "Xem anh nay", null, List.of(attachment("bien.png"))));

        MessageResponse forwarded = messageService.forwardMessage(alice.getId(), origin.id(), target);

        assertThat(forwarded.id()).isNotEqualTo(origin.id());
        assertThat(forwarded.conversationId()).isEqualTo(target);
        assertThat(forwarded.content()).isEqualTo("Xem anh nay");
        assertThat(forwarded.forwardedFromMessageId()).isEqualTo(origin.id());
        assertThat(forwarded.attachments()).singleElement()
                .satisfies(attachment -> assertThat(attachment.fileName()).isEqualTo("bien.png"));
    }

    @Test
    void khong_the_chuyen_tiep_tin_cua_hoi_thoai_minh_khong_tham_gia() {
        User alice = persistActiveUser("mmalice9");
        User bob = persistActiveUser("mmbob9");
        User intruder = persistActiveUser("mmspy9");
        UUID privateConversation = direct(alice, bob);
        UUID intruderConversation = direct(intruder, bob);

        MessageResponse secret = messageService.sendMessage(alice.getId(), privateConversation,
                new SendMessageRequest(MessageType.TEXT, "Bi mat", null));

        assertThatThrownBy(() ->
                messageService.forwardMessage(intruder.getId(), secret.id(), intruderConversation))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER);
    }

    // ---------- Xóa phía mình ----------

    @Test
    void xoa_phia_minh_chi_an_voi_nguoi_do() {
        User alice = persistActiveUser("mmalice10");
        User bob = persistActiveUser("mmbob10");
        UUID conversationId = direct(alice, bob);

        messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Tin se bi an", null));
        MessageResponse keep = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Tin van con", null));
        MessageResponse hide = messageService.getMessages(bob.getId(), conversationId, null, 10)
                .items().getLast();

        messageService.deleteForMe(bob.getId(), hide.id());

        // Bob không còn thấy tin đó...
        assertThat(messageService.getMessages(bob.getId(), conversationId, null, 10).items())
                .extracting(MessageResponse::id)
                .containsExactly(keep.id());

        // ...nhưng Alice vẫn thấy đủ 2 tin. Đây là khác biệt cốt lõi so với thu hồi.
        assertThat(messageService.getMessages(alice.getId(), conversationId, null, 10).items())
                .hasSize(2);
    }

    @Test
    void xoa_phia_minh_hai_lan_khong_gay_loi() {
        User alice = persistActiveUser("mmalice11");
        User bob = persistActiveUser("mmbob11");
        UUID conversationId = direct(alice, bob);
        MessageResponse message = messageService.sendMessage(alice.getId(), conversationId,
                new SendMessageRequest(MessageType.TEXT, "Tin nhan", null));

        messageService.deleteForMe(bob.getId(), message.id());
        messageService.deleteForMe(bob.getId(), message.id()); // idempotent

        assertThat(messageService.getMessages(bob.getId(), conversationId, null, 10).items()).isEmpty();
    }

    // ---------- Helper ----------

    private AttachmentRequest attachment(String fileName) {
        return new AttachmentRequest(
                BUCKET_PREFIX + "2026/09/05/" + UUID.randomUUID() + ".png",
                fileName, "image/png", 2048);
    }

    private UUID direct(User a, User b) {
        return conversationService.getOrCreateDirectConversation(a.getId(), b.getId()).id();
    }

    private User persistActiveUser(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setPasswordHash("irrelevant-hash");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
