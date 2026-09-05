package com.chatsphere.user.repository;

import com.chatsphere.user.domain.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, UUID> {

    /**
     * Kiểm tra chặn HAI CHIỀU bằng MỘT query.
     * <p>Cả "tôi chặn nó" và "nó chặn tôi" đều phải ngăn tương tác, nên đừng gọi
     * existsByBlockerIdAndBlockedId 2 lần — sẽ thành 2 round-trip DB cho mỗi lần gửi tin nhắn
     * ở Phase 3 và mỗi cuộc gọi ở Phase 6. Query này dùng idx_blocked_users_pair (chiều 1)
     * và idx_blocked_users_blocked_id (chiều 2).
     */
    @Query("""
            SELECT COUNT(b) > 0 FROM BlockedUser b
            WHERE (b.blocker.id = :a AND b.blocked.id = :b)
               OR (b.blocker.id = :b AND b.blocked.id = :a)
            """)
    boolean existsBlockBetween(@Param("a") UUID a, @Param("b") UUID b);

    Optional<BlockedUser> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** Ai trong trang kết quả tìm kiếm đang bị tôi chặn — 1 query cho cả trang. */
    @Query("""
            SELECT b.blocked.id FROM BlockedUser b
            WHERE b.blocker.id = :me AND b.blocked.id IN :ids
            """)
    Set<UUID> findBlockedIdsAmong(@Param("me") UUID me, @Param("ids") Collection<UUID> ids);
}
