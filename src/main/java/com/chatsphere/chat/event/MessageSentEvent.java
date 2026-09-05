package com.chatsphere.chat.event;

import com.chatsphere.chat.dto.MessageResponse;

/**
 * Đã lưu thành công 1 tin nhắn mới.
 *
 * <p>{@code MessageService} phát sự kiện này thay vì tự gọi {@code SimpMessagingTemplate}, để:
 * <ul>
 *   <li>Tin gửi qua REST (Phase 3) và qua STOMP (Phase 4) đi CHUNG một đường phát sóng —
 *       không có luồng nào "quên" broadcast.</li>
 *   <li>Service nghiệp vụ không phụ thuộc tầng vận chuyển; test nó không cần dựng WebSocket.</li>
 *   <li>Phase 5 cắm thêm {@code NotificationEventListener} vào đúng sự kiện này
 *       (03_CODE_ROADMAP.md 5.2) mà không phải sửa {@code MessageService}.</li>
 * </ul>
 */
public record MessageSentEvent(MessageResponse message) {
}
