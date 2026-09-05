package com.chatsphere.chat.repository;

import com.chatsphere.chat.domain.MessageDeletion;
import com.chatsphere.chat.domain.MessageDeletionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface MessageDeletionRepository extends JpaRepository<MessageDeletion, MessageDeletionId> {

    /**
     * Trong trang tin nhắn vừa lấy, tin nào đã bị CHÍNH người này ẩn đi.
     *
     * <p>Lọc sau khi phân trang (thay vì {@code NOT EXISTS} ngay trong query lấy tin) là lựa
     * chọn có chủ ý: thêm subquery vào truy vấn nóng nhất hệ thống sẽ làm chậm mọi lần mở hội
     * thoại, trong khi "xóa phía tôi" là thao tác hiếm. Đánh đổi là trang trả về có thể ít hơn
     * {@code limit} vài tin — chấp nhận được với cursor pagination.
     */
    @Query("""
            SELECT d.id.messageId FROM MessageDeletion d
            WHERE d.id.userId = :userId AND d.id.messageId IN :messageIds
            """)
    Set<UUID> findDeletedMessageIds(@Param("userId") UUID userId,
                                    @Param("messageIds") Collection<UUID> messageIds);
}
