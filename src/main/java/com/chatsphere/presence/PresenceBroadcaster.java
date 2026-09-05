package com.chatsphere.presence;

import com.chatsphere.common.WsDestinations;
import com.chatsphere.user.domain.PrivacyLevel;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Quyết định AI được biết trạng thái online của ai, rồi gửi đi (03_CODE_ROADMAP.md 4.2).
 *
 * <p>Tách khỏi {@link PresenceService} theo đúng ranh giới trách nhiệm: bên kia chỉ đếm phiên
 * trong Redis, bên này mới đụng tới database (bạn bè, cài đặt riêng tư) và WebSocket. Nhờ vậy
 * {@code PresenceService} test được mà không cần JPA.
 *
 * <p><b>Chỉ phát cho bạn bè</b>, không phát rộng rãi: ngoài chuyện riêng tư, phát cho toàn hệ
 * thống mỗi lần ai đó mở tab sẽ tạo ra lượng frame tăng theo bình phương số người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceBroadcaster {

    private final FriendshipRepository friendshipRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Báo cho bạn bè biết {@code userId} vừa online/offline.
     *
     * <p>Tôn trọng {@code online_visibility} (UC-13): {@link PrivacyLevel#NOBODY} thì im lặng
     * hoàn toàn. {@link PrivacyLevel#EVERYONE} và {@link PrivacyLevel#FRIENDS_ONLY} ở đây hành
     * xử giống nhau vì kênh này vốn chỉ gửi cho bạn bè — khác biệt giữa hai mức chỉ lộ ra ở
     * {@link #canSee(UUID, UUID)} khi người LẠ hỏi trạng thái qua REST.
     */
    @Transactional(readOnly = true)
    public void broadcast(UUID userId, PresenceStatus status) {
        if (visibilityOf(userId) == PrivacyLevel.NOBODY) {
            log.debug("Bỏ qua phát presence của user {} — cài đặt online_visibility = NOBODY", userId);
            return;
        }

        Set<UUID> friendIds = friendshipRepository.findFriendIdsOf(userId);
        if (friendIds.isEmpty()) {
            return;
        }

        PresenceEvent event = status == PresenceStatus.ONLINE
                ? PresenceEvent.online(userId)
                : PresenceEvent.offline(userId);

        for (UUID friendId : friendIds) {
            // Gửi cho MỌI bạn bè, kể cả người đang offline: Spring tự bỏ qua khi không có phiên
            // nào ứng với destination đó. Kiểm tra online trước khi gửi chỉ thêm N truy vấn
            // Redis để tiết kiệm vài frame vốn đã bị bỏ ngay tại chỗ.
            messagingTemplate.convertAndSendToUser(
                    friendId.toString(), WsDestinations.QUEUE_PRESENCE, event);
        }
        log.debug("Đã phát presence {} của user {} tới {} bạn bè", status, userId, friendIds.size());
    }

    /**
     * Bạn bè nào của {@code viewerId} đang online — dùng cho lần tải trang đầu tiên.
     *
     * <p>Không có endpoint này thì client mới mở phải chờ ai đó đổi trạng thái mới biết được
     * chấm xanh: WebSocket chỉ phát THAY ĐỔI, không phát trạng thái hiện tại.
     */
    @Transactional(readOnly = true)
    public Set<UUID> onlineFriendsOf(UUID viewerId) {
        Set<UUID> friendIds = friendshipRepository.findFriendIdsOf(viewerId);
        if (friendIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> online = presenceService.filterOnline(friendIds);
        online.removeIf(friendId -> visibilityOf(friendId) == PrivacyLevel.NOBODY);
        return online;
    }

    /**
     * {@code viewerId} có được nhìn trạng thái của {@code targetId} không (UC-13).
     * Người dùng luôn nhìn thấy chính mình.
     */
    @Transactional(readOnly = true)
    public boolean canSee(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            return true;
        }
        return switch (visibilityOf(targetId)) {
            case EVERYONE -> true;
            case FRIENDS_ONLY -> friendshipRepository.existsBetween(viewerId, targetId);
            case NOBODY -> false;
        };
    }

    /** Mặc định EVERYONE khi chưa có dòng settings — trùng với giá trị mặc định của entity. */
    private PrivacyLevel visibilityOf(UUID userId) {
        return userSettingsRepository.findById(userId)
                .map(settings -> settings.getOnlineVisibility())
                .orElse(PrivacyLevel.EVERYONE);
    }
}
