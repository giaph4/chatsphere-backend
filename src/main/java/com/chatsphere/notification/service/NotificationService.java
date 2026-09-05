package com.chatsphere.notification.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.PageResponse;
import com.chatsphere.notification.domain.Notification;
import com.chatsphere.notification.domain.NotificationType;
import com.chatsphere.notification.dto.NotificationResponse;
import com.chatsphere.notification.repository.NotificationRepository;
import com.chatsphere.presence.PresenceService;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Tạo và đọc thông báo trong ứng dụng (03_CODE_ROADMAP.md 5.2).
 *
 * <p><b>Luôn LƯU trước, đẩy realtime sau.</b> Bản ghi DB là nguồn sự thật — người dùng offline
 * lúc có sự kiện vẫn phải thấy thông báo khi họ mở lại app. Frame WebSocket chỉ là lối tắt giúp
 * người đang online thấy ngay mà không phải chờ lần gọi API kế tiếp; mất frame không mất dữ liệu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String QUEUE_NOTIFICATIONS = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NotificationResponse create(UUID userId, NotificationType type, UUID referenceId, String content) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Notification saved = notificationRepository.save(
                Notification.of(user, type, referenceId, truncate(content)));
        NotificationResponse response = toResponse(saved);

        // Chỉ đẩy khi đang online — người offline không có phiên nào nhận, gửi cũng vô ích.
        if (presenceService.isOnline(userId)) {
            messagingTemplate.convertAndSendToUser(userId.toString(), QUEUE_NOTIFICATIONS, response);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(UUID userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadIsFalse(userId);
    }

    /**
     * Đánh dấu 1 thông báo đã đọc.
     *
     * <p>Thông báo của người khác trả về {@code NOTIFICATION_NOT_FOUND} chứ không phải 403:
     * người gọi không có quyền biết id đó có tồn tại hay không.
     */
    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.setRead(true);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    /** Cột `content` là VARCHAR(500) — cắt ở tầng ứng dụng để không bao giờ vỡ vì tin nhắn dài. */
    private String truncate(String content) {
        if (content == null || content.isBlank()) {
            return "Bạn có thông báo mới";
        }
        return content.length() <= 500 ? content : content.substring(0, 497) + "...";
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
