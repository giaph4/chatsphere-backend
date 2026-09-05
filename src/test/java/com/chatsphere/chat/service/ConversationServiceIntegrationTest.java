package com.chatsphere.chat.service;

import com.chatsphere.chat.domain.ParticipantRole;
import com.chatsphere.chat.dto.ConversationResponse;
import com.chatsphere.chat.dto.CreateGroupRequest;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.chat.repository.ConversationRepository;
import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.support.AbstractIntegrationTest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.repository.UserRepository;
import com.chatsphere.user.service.BlockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test THẬT qua Postgres (Testcontainers) — theo đúng nguyên tắc đã áp dụng ở
 * FriendServiceIntegrationTest (Phase 2): giá trị nằm ở ràng buộc DB thật + hành vi
 * transaction, Mockito không mô phỏng được.
 */
class ConversationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationService conversationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockService blockService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private ConversationParticipantRepository participantRepository;

    private User persistActiveUser(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setPasswordHash("irrelevant-hash");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    // ---------- getOrCreateDirectConversation ----------

    @Test
    void tao_hoi_thoai_direct_lan_2_tra_ve_cung_1_conversation_khong_tao_trung() {
        User alice = persistActiveUser("dalice1");
        User bob = persistActiveUser("dbob1");

        ConversationResponse first = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());
        // Gọi ngược chiều (bob -> alice) vẫn phải nhận diện là CÙNG 1 cuộc trò chuyện.
        ConversationResponse second = conversationService.getOrCreateDirectConversation(bob.getId(), alice.getId());

        assertThat(second.id()).isEqualTo(first.id());
        // KHÔNG dùng conversationRepository.count() — bảng dùng chung cho cả class test (không
        // rollback giữa các test), đếm toàn bảng sẽ dính cả conversation của test khác chạy trước.
        assertThat(conversationRepository.findDirectBetween(alice.getId(), bob.getId())).isPresent();
        assertThat(participantRepository.findActiveByConversationIds(List.of(first.id()))).hasSize(2);
    }

    @Test
    void tu_tao_hoi_thoai_voi_chinh_minh_bi_tu_choi() {
        User alice = persistActiveUser("dalice2");

        assertThatThrownBy(() -> conversationService.getOrCreateDirectConversation(alice.getId(), alice.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_FRIEND_SELF);
    }

    @Test
    void tao_hoi_thoai_direct_voi_nguoi_da_chan_minh_bi_tu_choi() {
        User alice = persistActiveUser("dalice3");
        User bob = persistActiveUser("dbob3");
        blockService.block(bob.getId(), alice.getId()); // bob chặn alice

        assertThatThrownBy(() -> conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);
    }

    // ---------- createGroup ----------

    @Test
    void tao_nhom_nguoi_tao_la_admin_thanh_vien_con_lai_la_member() {
        User creator = persistActiveUser("gcreator1");
        User memberB = persistActiveUser("gmemberb1");
        User memberC = persistActiveUser("gmemberc1");

        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm Test", List.of(memberB.getId(), memberC.getId())));

        assertThat(group.participants()).hasSize(3);
        var byUsername = group.participants().stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.user().username(), p -> p.role()));
        assertThat(byUsername.get("gcreator1")).isEqualTo(ParticipantRole.ADMIN);
        assertThat(byUsername.get("gmemberb1")).isEqualTo(ParticipantRole.MEMBER);
        assertThat(byUsername.get("gmemberc1")).isEqualTo(ParticipantRole.MEMBER);
    }

    @Test
    void tao_nhom_client_tu_them_chinh_minh_vao_memberIds_khong_bi_trung() {
        User creator = persistActiveUser("gcreator2");
        User memberB = persistActiveUser("gmemberb2");

        ConversationResponse group = conversationService.createGroup(
                creator.getId(),
                new CreateGroupRequest("Nhóm Test 2", List.of(creator.getId(), memberB.getId())));

        assertThat(group.participants()).hasSize(2); // KHÔNG có 2 dòng cho creator
    }

    // ---------- addMember / removeMember ----------

    @Test
    void them_thanh_vien_boi_nguoi_khong_phai_admin_bi_tu_choi() {
        User creator = persistActiveUser("gcreator3");
        User memberB = persistActiveUser("gmemberb3");
        User outsider = persistActiveUser("goutsider3");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm 3", List.of(memberB.getId())));

        assertThatThrownBy(() -> conversationService.addMember(memberB.getId(), group.id(), outsider.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GROUP_ADMIN_REQUIRED);
    }

    @Test
    void them_thanh_vien_da_co_trong_nhom_bi_tu_choi() {
        User creator = persistActiveUser("gcreator4");
        User memberB = persistActiveUser("gmemberb4");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm 4", List.of(memberB.getId())));

        assertThatThrownBy(() -> conversationService.addMember(creator.getId(), group.id(), memberB.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_CONVERSATION_MEMBER);
    }

    @Test
    void xoa_thanh_vien_thanh_cong_nguoi_bi_xoa_khong_con_active() {
        User creator = persistActiveUser("gcreator5");
        User memberB = persistActiveUser("gmemberb5");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm 5", List.of(memberB.getId())));

        conversationService.removeMember(creator.getId(), group.id(), memberB.getId());

        assertThat(participantRepository
                .existsByConversationIdAndUserIdAndLeftAtIsNull(group.id(), memberB.getId())).isFalse();
    }

    @Test
    void thao_tac_nhom_tren_hoi_thoai_direct_bi_tu_choi() {
        User alice = persistActiveUser("dalice4");
        User bob = persistActiveUser("dbob4");
        ConversationResponse direct = conversationService.getOrCreateDirectConversation(alice.getId(), bob.getId());

        assertThatThrownBy(() -> conversationService.addMember(alice.getId(), direct.id(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_A_GROUP_CONVERSATION);
    }

    // ---------- leaveGroup ----------

    @Test
    void thanh_vien_thuong_roi_nhom_khong_anh_huong_admin() {
        User creator = persistActiveUser("gcreator6");
        User memberB = persistActiveUser("gmemberb6");
        User memberC = persistActiveUser("gmemberc6");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm 6", List.of(memberB.getId(), memberC.getId())));

        conversationService.leaveGroup(memberB.getId(), group.id());

        assertThat(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(group.id(), creator.getId())
                .orElseThrow().getRole()).isEqualTo(ParticipantRole.ADMIN);
        assertThat(participantRepository
                .existsByConversationIdAndUserIdAndLeftAtIsNull(group.id(), memberB.getId())).isFalse();
    }

    @Test
    void admin_duy_nhat_roi_nhom_tu_dong_chuyen_quyen_cho_thanh_vien_con_lai() {
        User creator = persistActiveUser("gcreator7");
        User memberB = persistActiveUser("gmemberb7");
        ConversationResponse group = conversationService.createGroup(
                creator.getId(), new CreateGroupRequest("Nhóm 7", List.of(memberB.getId())));

        conversationService.leaveGroup(creator.getId(), group.id());

        assertThat(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(group.id(), memberB.getId())
                .orElseThrow().getRole()).isEqualTo(ParticipantRole.ADMIN);
    }

    @Test
    void roi_nhom_khi_khong_phai_thanh_vien_bao_loi() {
        User creator = persistActiveUser("gcreator8");
        ConversationResponse group = conversationService.createGroup(creator.getId(), new CreateGroupRequest(
                "Nhóm 8", List.of(persistActiveUser("gmemberb8").getId())));
        User outsider = persistActiveUser("goutsider8");

        assertThatThrownBy(() -> conversationService.leaveGroup(outsider.getId(), group.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER);
    }
}
