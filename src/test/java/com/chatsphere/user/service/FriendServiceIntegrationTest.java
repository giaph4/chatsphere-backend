package com.chatsphere.user.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.support.AbstractIntegrationTest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.dto.FriendRequestResponse;
import com.chatsphere.user.repository.FriendRequestRepository;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test THẬT qua Postgres (Testcontainers) — không mock repository, vì toàn bộ giá trị
 * của Phase 2.3 nằm ở các ràng buộc DB thật (partial unique index, CHECK, compare-and-set
 * UPDATE) mà Mockito không thể mô phỏng.
 */
class FriendServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendService friendService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private FriendRequestRepository friendRequestRepository;

    private User persistActiveUser(String username) {
        User user = new User();
        user.setEmail(username + "@test.local");
        user.setPasswordHash("irrelevant-hash");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Test
    void gui_loi_moi_roi_chap_nhan_tao_dung_1_friendship() {
        User alice = persistActiveUser("alice1");
        User bob = persistActiveUser("bob1");

        FriendRequestResponse sent = friendService.sendRequest(alice.getId(), bob.getId());

        FriendRequestResponse accepted = friendService.acceptRequest(bob.getId(), sent.id());

        assertThat(accepted.status().name()).isEqualTo("ACCEPTED");
        assertThat(friendshipRepository.existsBetween(alice.getId(), bob.getId())).isTrue();

        // user1 < user2 theo đúng thứ tự PostgreSQL — không phải thứ tự gửi lời mời
        var friendship = friendshipRepository.findAllWithUsersByUserId(alice.getId(), PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(comparePg(friendship.getUser1().getId(), friendship.getUser2().getId())).isLessThan(0);
    }

    @Test
    void tu_ket_ban_voi_chinh_minh_bi_chan() {
        User alice = persistActiveUser("alice2");

        assertThatThrownBy(() -> friendService.sendRequest(alice.getId(), alice.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_FRIEND_SELF);
    }

    @Test
    void gui_loi_moi_cho_nguoi_da_chan_minh_bi_tu_choi() {
        User alice = persistActiveUser("alice3");
        User bob = persistActiveUser("bob3");

        blockService.block(bob.getId(), alice.getId()); // bob chặn alice

        assertThatThrownBy(() -> friendService.sendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);
    }

    @Test
    void gui_loi_moi_trung_lap_bi_chan_boi_partial_unique_index() {
        User alice = persistActiveUser("alice4");
        User bob = persistActiveUser("bob4");

        friendService.sendRequest(alice.getId(), bob.getId());

        assertThatThrownBy(() -> friendService.sendRequest(alice.getId(), bob.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
    }

    @Test
    void loi_moi_cheo_tu_dong_duoc_chap_nhan() {
        User alice = persistActiveUser("alice5");
        User bob = persistActiveUser("bob5");

        friendService.sendRequest(alice.getId(), bob.getId()); // alice -> bob
        // bob -> alice trong khi lời mời alice->bob còn PENDING: phải tự động accept,
        // KHÔNG được tạo request thứ 2 (partial unique index không chặn được cặp ngược).
        FriendRequestResponse result = friendService.sendRequest(bob.getId(), alice.getId());

        assertThat(result.status().name()).isEqualTo("ACCEPTED");
        assertThat(friendshipRepository.existsBetween(alice.getId(), bob.getId())).isTrue();
    }

    @Test
    void chap_nhan_2_lan_dong_thoi_chi_1_lan_thanh_cong() throws Exception {
        User alice = persistActiveUser("alice6");
        User bob = persistActiveUser("bob6");
        FriendRequestResponse sent = friendService.sendRequest(alice.getId(), bob.getId());

        // Mô phỏng double-click: 2 thread cùng gọi acceptRequest cho CÙNG 1 request.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);

            Callable<Boolean> attempt = () -> {
                ready.countDown();
                go.await();
                try {
                    friendService.acceptRequest(bob.getId(), sent.id());
                    return true;
                } catch (BusinessException e) {
                    return false;
                }
            };

            Future<Boolean> f1 = pool.submit(attempt);
            Future<Boolean> f2 = pool.submit(attempt);
            ready.await();
            go.countDown();

            List<Boolean> results = List.of(f1.get(5, TimeUnit.SECONDS), f2.get(5, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(true, false); // đúng 1 thắng, 1 thua

            // Đúng 1 Friendship được tạo — không có bản ghi trùng.
            long count = friendshipRepository.findAllWithUsersByUserId(alice.getId(), PageRequest.of(0, 10))
                    .getTotalElements();
            assertThat(count).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void chan_nguoi_dang_la_ban_thi_huy_luon_friendship() {
        User alice = persistActiveUser("alice7");
        User bob = persistActiveUser("bob7");
        FriendRequestResponse sent = friendService.sendRequest(alice.getId(), bob.getId());
        friendService.acceptRequest(bob.getId(), sent.id());
        assertThat(friendshipRepository.existsBetween(alice.getId(), bob.getId())).isTrue();

        blockService.block(alice.getId(), bob.getId());

        assertThat(friendshipRepository.existsBetween(alice.getId(), bob.getId())).isFalse();
        assertThat(blockService.isBlockedBetween(alice.getId(), bob.getId())).isTrue();
    }

    @Test
    void chap_nhan_request_khong_ton_tai_bao_404() {
        User bob = persistActiveUser("bob10");

        assertThatThrownBy(() -> friendService.acceptRequest(bob.getId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
    }

    @Test
    void tu_choi_boi_nguoi_khong_phai_nguoi_nhan_bao_404() {
        User alice = persistActiveUser("alice11");
        User bob = persistActiveUser("bob11");
        User eve = persistActiveUser("eve11"); // không liên quan tới request này
        FriendRequestResponse sent = friendService.sendRequest(alice.getId(), bob.getId());

        // Trả NOT_FOUND thay vì ACCESS_DENIED — không tiết lộ "request này có tồn tại"
        // cho người ngoài cuộc, giống lý do acceptRequest() làm y hệt.
        assertThatThrownBy(() -> friendService.rejectRequest(eve.getId(), sent.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FRIEND_REQUEST_NOT_FOUND);

        // Người NHẬN thật vẫn từ chối được bình thường sau đó.
        friendService.rejectRequest(bob.getId(), sent.id());
    }

    @Test
    void thu_hoi_boi_nguoi_khong_phai_nguoi_gui_bao_404() {
        User alice = persistActiveUser("alice12");
        User bob = persistActiveUser("bob12");
        FriendRequestResponse sent = friendService.sendRequest(alice.getId(), bob.getId());

        // Bob là người NHẬN, không phải người GỬI -> không được cancel (đó là hành động của sender).
        assertThatThrownBy(() -> friendService.cancelRequest(bob.getId(), sent.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
    }

    @Test
    void huy_ket_ban_khi_chua_la_ban_bao_404() {
        User alice = persistActiveUser("alice13");
        User bob = persistActiveUser("bob13");

        assertThatThrownBy(() -> friendService.removeFriend(alice.getId(), bob.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FRIENDS);
    }

    @Test
    void chan_2_lan_lien_tiep_bao_loi_409() {
        User alice = persistActiveUser("alice8");
        User bob = persistActiveUser("bob8");
        blockService.block(alice.getId(), bob.getId());

        assertThatThrownBy(() -> blockService.block(alice.getId(), bob.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_BLOCKED);
    }

    @Test
    void bo_chan_nguoi_chua_tung_chan_khong_bao_loi() {
        User alice = persistActiveUser("alice9");
        User bob = persistActiveUser("bob9");

        blockService.unblock(alice.getId(), bob.getId()); // không throw = pass
    }

    @Test
    void tim_kiem_tra_ve_dung_relationship_cho_moi_trang_thai() {
        User me = persistActiveUser("searcher1");
        User friend = persistActiveUser("searchfriend1");
        User blocked = persistActiveUser("searchblocked1");
        User stranger = persistActiveUser("searchstranger1");

        FriendRequestResponse sent = friendService.sendRequest(me.getId(), friend.getId());
        friendService.acceptRequest(friend.getId(), sent.id());
        blockService.block(me.getId(), blocked.getId());

        var page = userService.search(me.getId(), "search", PageRequest.of(0, 10));

        assertThat(page.items()).hasSize(3); // friend + blocked + stranger, KHÔNG có "me"
        var byUsername = page.items().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.user().username(), r -> r.relationship()));
        assertThat(byUsername.get("searchfriend1").name()).isEqualTo("FRIEND");
        assertThat(byUsername.get("searchblocked1").name()).isEqualTo("BLOCKED");
        assertThat(byUsername.get("searchstranger1").name()).isEqualTo("NONE");
    }

    @Test
    void tim_kiem_voi_ky_tu_dai_dien_khong_dump_toan_bo_bang() {
        User me = persistActiveUser("wildcardtester");
        persistActiveUser("otherUnrelatedUser");

        // Nếu escapeLikeWildcards() không hoạt động, "%" sẽ khớp MỌI user khác "me".
        var page = userService.search(me.getId(), "%", PageRequest.of(0, 50));

        assertThat(page.items()).isEmpty();
    }

    private static int comparePg(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
