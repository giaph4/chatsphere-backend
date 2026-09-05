package com.chatsphere.chat.service;

import com.chatsphere.chat.dto.MessageResponse;
import com.chatsphere.chat.dto.ReadReceiptEvent;
import com.chatsphere.chat.event.MessageReadEvent;
import com.chatsphere.chat.event.MessageRecalledEvent;
import com.chatsphere.chat.event.MessageSentEvent;
import com.chatsphere.common.WsDestinations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lớp mỏng duy nhất biến thay đổi dữ liệu thành frame WebSocket. Ngoài class này, không chỗ nào
 * trong module chat được gọi {@link SimpMessagingTemplate}.
 *
 * <p><b>Vì sao {@code AFTER_COMMIT} chứ không phát ngay trong service?</b> Nếu phát trước khi
 * transaction commit, một lỗi ở cuối transaction (vi phạm ràng buộc, mất kết nối DB) sẽ rollback
 * tin nhắn — nhưng frame WebSocket thì KHÔNG rollback được, nó đã bay tới trình duyệt rồi. Kết
 * quả: người dùng nhìn thấy một tin nhắn không hề tồn tại trong DB và biến mất khi F5. Chờ commit
 * xong mới phát thì chỉ còn rủi ro ngược lại, nhẹ hơn nhiều: tin có trong DB nhưng frame lỡ mất —
 * người dùng F5 hoặc mở lại là thấy.
 *
 * <p>{@code fallbackExecution = true}: nếu về sau có luồng gọi service ngoài transaction (job nền,
 * test gọi thẳng), listener vẫn chạy thay vì im lặng không làm gì — kiểu hỏng khó phát hiện nhất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageSent(MessageSentEvent event) {
        send(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageRecalled(MessageRecalledEvent event) {
        send(event.message());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageRead(MessageReadEvent event) {
        ReadReceiptEvent receipt = event.receipt();
        messagingTemplate.convertAndSend(
                WsDestinations.conversationTopic(receipt.conversationId()), receipt);
    }

    /**
     * Tin mới và tin thu hồi dùng CHUNG một destination và chung kiểu payload
     * {@link MessageResponse}: client chỉ cần một handler, phân biệt bằng {@code status} —
     * {@code RECALLED} thì thay chỗ tin cũ (cùng {@code id}), còn lại thì chèn tin mới.
     */
    private void send(MessageResponse message) {
        messagingTemplate.convertAndSend(
                WsDestinations.conversationTopic(message.conversationId()), message);
        log.debug("Đã phát tin nhắn {} tới hội thoại {}", message.id(), message.conversationId());
    }
}
