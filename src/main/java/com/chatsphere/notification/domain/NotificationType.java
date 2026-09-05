package com.chatsphere.notification.domain;

/**
 * Danh sách phải khớp với CHECK constraint {@code chk_notifications_type} ở migration V14 —
 * thêm giá trị mới ở đây bắt buộc phải kèm một migration nới ràng buộc, nếu không insert sẽ
 * fail lúc chạy.
 */
public enum NotificationType {

    NEW_MESSAGE,
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    MISSED_CALL,
    MENTIONED
}
