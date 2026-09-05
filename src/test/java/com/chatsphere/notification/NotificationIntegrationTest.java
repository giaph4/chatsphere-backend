package com.chatsphere.notification;

import com.chatsphere.chat.domain.MessageType;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.SendMessageRequest;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.chat.service.ConversationService;
import com.chatsphere.chat.service.MessageService;
import com.chatsphere.notification.domain.NotificationType;
import com.chatsphere.notification.repository.NotificationRepository;
import com.chatsphere.notification.service.NotificationService;
import com.chatsphere.support.AbstractIntegrationTest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nghiệm thu Phase 5 mục 5.2 và 5.4 (03_CODE_ROADMAP.md 5.5): thông báo được tạo bất đồng bộ
 * cho người nhận, và KHÔNG được tạo khi hội thoại đang bị tắt thông báo.
 */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ConversationParticipantRepository participantRepository;
    @Autowired
    private UserRepository userRepository;

    // ---------- Quy tắc chọn người nhận (kiểm tra tất định, không phụ thuộc luồng nền) ----------

    @Test
    void nguoi_gui_khong_bao_gio_tu_nhan_thong_bao_cua_chinh_minh() {
        User alice = persistActiveUser("nalice1");
        User bob = persistActiveUser("nbob1");
        ConversationResponse direct = direct(alice, bob);

        List<UUID> recipients = participantRepository.findNotifiableUserIds(
                direct.id(), alice.getId(), Instant.now());

        assertThat(recipients).containsExactly(bob.getId());
    }

    @Test
    void hoi_thoai_dang_mute_bi_loai_khoi_danh_sach_nhan_thong_bao() {
        User alice = persistActiveUser("nalice2");
        User bob = persistActiveUser("nbob2");
        ConversationResponse direct = direct(alice, bob);

        conversationService.muteConversation(bob.getId(), direct.id(), Instant.now().plus(Duration.ofHours(8)));

        assertThat(participantRepository.findNotifiableUserIds(direct.id(), alice.getId(), Instant.now()))
                .isEmpty();
    }

    @Test
    void mute_het_han_thi_tu_dong_nhan_thong_bao_tro_lai() {
        User alice = persistActiveUser("nalice3");
        User bob = persistActiveUser("nbob3");
        ConversationResponse direct = direct(alice, bob);

        Instant muteUntil = Instant.now().plus(Duration.ofHours(1));
        conversationService.muteConversation(bob.getId(), direct.id(), muteUntil);

        // Không có job nào bật lại mute — chỉ cần thời điểm hiện tại vượt qua mốc là xong.
        // Đây chính là lý do lưu mốc thời gian thay vì cờ boolean.
        Instant afterExpiry = muteUntil.plus(Duration.ofMinutes(1));
        assertThat(participantRepository.findNotifiableUserIds(direct.id(), alice.getId(), afterExpiry))
                .containsExactly(bob.getId());
    }

    @Test
    void bo_mute_bang_cach_gui_null() {
        User alice = persistActiveUser("nalice4");
        User bob = persistActiveUser("nbob4");
        ConversationResponse direct = direct(alice, bob);

        conversationService.muteConversation(bob.getId(), direct.id(), Instant.now().plus(Duration.ofHours(8)));
        conversationService.muteConversation(bob.getId(), direct.id(), null);

        assertThat(participantRepository.findNotifiableUserIds(direct.id(), alice.getId(), Instant.now()))
                .containsExactly(bob.getId());
    }

    // ---------- Luồng đầy đủ, đi qua listener bất đồng bộ ----------

    @Test
    void gui_tin_nhan_tao_thong_bao_cho_nguoi_nhan() {
        User alice = persistActiveUser("nalice5");
        User bob = persistActiveUser("nbob5");
        ConversationResponse direct = direct(alice, bob);

        var sent = messageService.sendMessage(alice.getId(), direct.id(),
                new SendMessageRequest(MessageType.TEXT, "Chao Bob", null));

        // Listener chạy trên luồng nền (@Async) sau khi transaction commit -> phải chờ có giới hạn.
        await(() -> notificationRepository.countByUserIdAndReadIsFalse(bob.getId()) == 1);

        var notifications = notificationService.getMyNotifications(bob.getId(), PageRequest.of(0, 10));
        assertThat(notifications.items()).singleElement().satisfies(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.NEW_MESSAGE);
            assertThat(notification.referenceId()).isEqualTo(sent.id());
            assertThat(notification.content()).contains("nalice5").contains("Chao Bob");
            assertThat(notification.read()).isFalse();
        });

        // Người gửi không có thông báo nào.
        assertThat(notificationRepository.countByUserIdAndReadIsFalse(alice.getId())).isZero();
    }

    @Test
    void khong_tao_thong_bao_khi_hoi_thoai_dang_bi_mute() {
        User alice = persistActiveUser("nalice6");
        User bob = persistActiveUser("nbob6");
        ConversationResponse direct = direct(alice, bob);

        conversationService.muteConversation(bob.getId(), direct.id(), Instant.now().plus(Duration.ofHours(8)));

        messageService.sendMessage(alice.getId(), direct.id(),
                new SendMessageRequest(MessageType.TEXT, "Tin nhan bi mute", null));

        // Tin thứ hai ở hội thoại KHÁC (không mute) đóng vai "mốc đồng bộ": khi thông báo của
        // nó đã xuất hiện, luồng nền chắc chắn đã xử lý xong cả tin trước đó — nhờ vậy khẳng
        // định "không có thông báo" là kết luận thật, không phải do kiểm tra quá sớm.
        User carol = persistActiveUser("ncarol6");
        ConversationResponse another = direct(alice, carol);
        messageService.sendMessage(alice.getId(), another.id(),
                new SendMessageRequest(MessageType.TEXT, "Tin nhan binh thuong", null));
        await(() -> notificationRepository.countByUserIdAndReadIsFalse(carol.getId()) == 1);

        assertThat(notificationRepository.countByUserIdAndReadIsFalse(bob.getId())).isZero();
    }

    // ---------- Đọc / đánh dấu đã đọc ----------

    @Test
    void danh_dau_tat_ca_da_doc_dua_so_chua_doc_ve_khong() {
        User alice = persistActiveUser("nalice7");
        User bob = persistActiveUser("nbob7");
        ConversationResponse direct = direct(alice, bob);

        messageService.sendMessage(alice.getId(), direct.id(),
                new SendMessageRequest(MessageType.TEXT, "Tin 1", null));
        messageService.sendMessage(alice.getId(), direct.id(),
                new SendMessageRequest(MessageType.TEXT, "Tin 2", null));
        await(() -> notificationService.countUnread(bob.getId()) == 2);

        assertThat(notificationService.markAllAsRead(bob.getId())).isEqualTo(2);
        assertThat(notificationService.countUnread(bob.getId())).isZero();
    }

    // ---------- Helper ----------

    private User persistActiveUser(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setPasswordHash("irrelevant-hash");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private ConversationResponse direct(User a, User b) {
        return conversationService.getOrCreateDirectConversation(a.getId(), b.getId());
    }

    /**
     * Chờ điều kiện đúng, tối đa 5 giây. Thay cho {@code Thread.sleep} cố định: test xong ngay
     * khi luồng nền chạy xong (thường vài chục mili-giây) thay vì luôn ngủ đủ thời gian tệ nhất.
     */
    private void await(Callable<Boolean> condition) {
        Instant deadline = Instant.now().plusSeconds(5);
        try {
            while (Instant.now().isBefore(deadline)) {
                if (Boolean.TRUE.equals(condition.call())) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Điều kiện không xảy ra trong 5 giây");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Bị ngắt khi đang chờ", e);
        } catch (Exception e) {
            throw new AssertionError("Lỗi khi kiểm tra điều kiện chờ", e);
        }
    }
}
