package com.chatsphere.user.repository;

import com.chatsphere.user.domain.Friendship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /**
     * Danh sách bạn bè. Phải dùng OR vì mỗi cặp chỉ lưu 1 dòng theo quy ước
     * user_id_1 &lt; user_id_2 — mình có thể nằm ở cột nào cũng được.
     * <p>JOIN FETCH cả 2 phía: không có nó, mapper gọi f.getUser1().getUsername() sẽ bắn
     * thêm 2 query cho MỖI dòng (N+1). Đây là to-one fetch nên kết hợp Pageable an toàn
     * (khác fetch collection — Hibernate sẽ phân trang trong RAM).
     * <p>countQuery phải viết tay: Spring Data không suy ra được câu đếm từ query có JOIN FETCH.
     */
    @Query(value = """
            SELECT f FROM Friendship f
            JOIN FETCH f.user1
            JOIN FETCH f.user2
            WHERE f.user1.id = :me OR f.user2.id = :me
            """,
            countQuery = """
            SELECT COUNT(f) FROM Friendship f
            WHERE f.user1.id = :me OR f.user2.id = :me
            """)
    Page<Friendship> findAllWithUsersByUserId(@Param("me") UUID me, Pageable pageable);

    @Query("""
            SELECT COUNT(f) > 0 FROM Friendship f
            WHERE (f.user1.id = :a AND f.user2.id = :b)
               OR (f.user1.id = :b AND f.user2.id = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Modifying
    @Query("""
            DELETE FROM Friendship f
            WHERE (f.user1.id = :a AND f.user2.id = :b)
               OR (f.user1.id = :b AND f.user2.id = :a)
            """)
    int deleteBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Trong trang kết quả tìm kiếm, ai đã là bạn tôi — 1 query cho cả trang. */
    @Query("""
            SELECT CASE WHEN f.user1.id = :me THEN f.user2.id ELSE f.user1.id END
            FROM Friendship f
            WHERE (f.user1.id = :me AND f.user2.id IN :ids)
               OR (f.user2.id = :me AND f.user1.id IN :ids)
            """)
    Set<UUID> findFriendIdsAmong(@Param("me") UUID me, @Param("ids") Collection<UUID> ids);
}
