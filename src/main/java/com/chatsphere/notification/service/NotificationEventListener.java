package com.chatsphere.notification.service;

import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.event.MessageSentEvent;
import com.chatsphere.chat.repository.ConversationParticipantRepository;
import com.chatsphere.notification.domain.NotificationType;
import com.chatsphere.presence.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Biến "có tin nhắn mới" thành thông báo cho từng người nhận (03_CODE_ROADMAP.md 5.2).
 *
 * <p><b>Vì sao tách hẳn ra khỏi {@code MessageService.sendMessage()}?</b> Gửi tin nhắn là thao
 * tác nóng nhất hệ thống và người dùng cảm nhận trực tiếp độ trễ của nó. Một nhóm 50 người sẽ
 * cần 50 lần INSERT thông báo cộng với các lần gọi Web Push ra mạng ngoài — nhét vào luồng gửi
 * tin thì thời gian phản hồi phụ thuộc vào kích thước nhóm và vào một dịch vụ bên thứ ba. Tách
 * ra: người gửi nhận phản hồi ngay sau khi tin được lưu, thông báo đi sau vài chục mili-giây.
 *
 * <p><b>{@code AFTER_COMMIT} + {@code @Async} — thứ tự quan trọng:</b> chờ commit để không bao
 * giờ tạo thông báo cho một tin nhắn rốt cuộc bị rollback, rồi mới nhảy sang luồng nền để không
 * giữ chân luồng vừa gửi tin. Ngược lại ({@code @Async} chạy trước khi commit) sẽ có lúc luồng
 * nền đọc DB mà chưa thấy tin nhắn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int PREVIEW_MAX_LENGTH = 100;

    private final ConversationParticipantRepository participantRepository;
    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;
    private final PresenceService presenceService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        MessageResponse message = event.message();

        List<UUID> recipients = participantRepository.findNotifiableUserIds(
                message.conversationId(), message.sender().id(), Instant.now());
        if (recipients.isEmpty()) {
            return;
        }

        String content = "%s: %s".formatted(message.sender().displayName(), preview(message));

        for (UUID recipientId : recipients) {
            try {
                notificationService.create(recipientId, NotificationType.NEW_MESSAGE, message.id(), content);

                // Web Push CHỈ cho người đang offline: người đang mở app vừa nhận tin qua
                // WebSocket rồi, bắn thêm thông báo hệ điều hành là làm phiền hai lần.
                if (!presenceService.isOnline(recipientId)) {
                    pushNotificationService.sendToUser(recipientId, "Tin nhắn mới", content);
                }
            } catch (Exception e) {
                // Một người nhận lỗi (tài khoản vừa bị xóa, endpoint push hỏng) KHÔNG được làm
                // hỏng thông báo của những người còn lại trong nhóm.
                log.warn("Không tạo được thông báo cho user {} về tin nhắn {}: {}",
                        recipientId, message.id(), e.getMessage());
            }
        }
    }

    /**
     * Xem trước nội dung. Tin nhắn đã thu hồi hoặc chỉ có tệp thì {@code content} là null —
     * mô tả theo loại thay vì hiện chữ "null" trên màn hình khóa của người dùng.
     */
    private String preview(MessageResponse message) {
        if (message.content() == null || message.content().isBlank()) {
            return switch (message.type()) {
                case IMAGE -> "đã gửi một ảnh";
                case VOICE -> "đã gửi một tin nhắn thoại";
                case FILE -> "đã gửi một tệp";
                default -> "đã gửi một tin nhắn";
            };
        }
        String content = message.content();
        return content.length() <= PREVIEW_MAX_LENGTH
                ? content
                : content.substring(0, PREVIEW_MAX_LENGTH - 3) + "...";
    }
}
