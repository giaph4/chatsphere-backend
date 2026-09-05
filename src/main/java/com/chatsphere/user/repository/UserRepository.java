package com.chatsphere.user.repository;

import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Tìm user theo username/displayName (UC-10).
     * <p>Loại khỏi kết quả: chính mình, user đã xóa mềm, user chưa kích hoạt, và
     * <b>những người đã chặn mình</b> — người chặn không muốn bị mình tìm thấy.
     * Ngược lại, người MÌNH chặn vẫn hiện (để còn bỏ chặn được).
     * <p>LIKE '%keyword%' KHÔNG dùng được index → Phase 8.1 sẽ thay bằng full-text tsvector.
     * ESCAPE '\' đi kèm việc escape ở service: không escape thì người dùng gõ '%'
     * sẽ khớp toàn bộ bảng users.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND u.status = :status
              AND u.id <> :me
              AND (LOWER(u.username)    LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
              AND NOT EXISTS (
                    SELECT 1 FROM BlockedUser b
                    WHERE b.blocker.id = u.id AND b.blocked.id = :me)
            """)
    Page<User> search(@Param("me") UUID me,
                      @Param("keyword") String keyword,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
