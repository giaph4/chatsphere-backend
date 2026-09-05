package com.chatsphere.notification.repository;

import com.chatsphere.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadIsFalse(UUID userId);

    /**
     * "Đánh dấu đã đọc tất cả" bằng MỘT lệnh UPDATE thay vì nạp từng entity rồi sửa.
     *
     * <p>Người dùng có thể có hàng nghìn thông báo chưa đọc; nạp hết vào persistence context chỉ
     * để lật một cờ boolean là lãng phí bộ nhớ và sinh ra hàng nghìn câu UPDATE riêng lẻ.
     *
     * <p>{@code clearAutomatically}: bulk update đi thẳng xuống DB, KHÔNG đi qua persistence
     * context — không xóa cache thì entity đang giữ trong bộ nhớ vẫn mang giá trị cũ và có thể
     * ghi đè ngược lại kết quả vừa cập nhật.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.read = true
            WHERE n.user.id = :userId AND n.read = false
            """)
    int markAllAsRead(@Param("userId") UUID userId);
}
