package com.chatsphere.user.repository;

import com.chatsphere.user.domain.FriendRequest;
import com.chatsphere.user.domain.FriendRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {

    Optional<FriendRequest> findBySenderIdAndReceiverIdAndStatus(
            UUID senderId, UUID receiverId, FriendRequestStatus status);

    @Query(value = """
            SELECT fr FROM FriendRequest fr
            JOIN FETCH fr.sender
            JOIN FETCH fr.receiver
            WHERE fr.receiver.id = :me AND fr.status = :status
            """,
            countQuery = """
                    SELECT COUNT(fr) FROM FriendRequest fr
                    WHERE fr.receiver.id = :me AND fr.status = :status
                    """)
    Page<FriendRequest> findReceivedWithUsers(@Param("me") UUID me,
                                              @Param("status") FriendRequestStatus status,
                                              Pageable pageable);

    @Query(value = """
            SELECT fr FROM FriendRequest fr
            JOIN FETCH fr.sender
            JOIN FETCH fr.receiver
            WHERE fr.sender.id = :me AND fr.status = :status
            """,
            countQuery = """
                    SELECT COUNT(fr) FROM FriendRequest fr
                    WHERE fr.sender.id = :me AND fr.status = :status
                    """)
    Page<FriendRequest> findSentWithUsers(@Param("me") UUID me,
                                          @Param("status") FriendRequestStatus status,
                                          Pageable pageable);

    /**
     * Compare-and-set ở tầng DB: chỉ đổi trạng thái NẾU đang là PENDING.
     * <p>Trả về số dòng bị ảnh hưởng — 0 nghĩa là có người xử lý trước mình (double-click,
     * hoặc user mở 2 tab). Đây là cách chống race condition mà KHÔNG cần khóa: một câu
     * UPDATE là nguyên tử, người thua cuộc nhận về 0.
     */
    @Modifying
    @Query("""
            UPDATE FriendRequest fr SET fr.status = :newStatus
            WHERE fr.id = :id
              AND fr.status = com.chatsphere.user.domain.FriendRequestStatus.PENDING
            """)
    int updateStatusIfPending(@Param("id") UUID id, @Param("newStatus") FriendRequestStatus newStatus);

    /**
     * Mọi lời mời PENDING liên quan tới tôi trong trang kết quả — 1 query cho cả trang.
     */
    @Query("""
            SELECT fr FROM FriendRequest fr
            JOIN FETCH fr.sender
            JOIN FETCH fr.receiver
            WHERE fr.status = com.chatsphere.user.domain.FriendRequestStatus.PENDING
              AND ((fr.sender.id = :me AND fr.receiver.id IN :ids)
                OR (fr.receiver.id = :me AND fr.sender.id IN :ids))
            """)
    List<FriendRequest> findPendingAmong(@Param("me") UUID me, @Param("ids") Collection<UUID> ids);
}
