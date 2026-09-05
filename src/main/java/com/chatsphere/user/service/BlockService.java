package com.chatsphere.user.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.user.domain.BlockedUser;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.repository.BlockedUserRepository;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final BlockedUserRepository blockedUserRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    /**
     * Có quan hệ chặn giữa 2 người theo BẤT KỲ chiều nào không.
     * <p>Là chốt chặn dùng lại ở Phase 3 (gửi tin nhắn) và Phase 6 (gọi video),
     * nên phải rẻ: 1 query, không load entity.
     */
    public boolean isBlockedBetween(UUID a, UUID b) {
        return blockedUserRepository.existsBlockBetween(a, b);
    }

    /** Ném lỗi nếu bị chặn — gọi ở đầu mọi hành động tương tác giữa 2 người. */
    public void assertNotBlocked(UUID a, UUID b) {
        if (isBlockedBetween(a, b)) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        User blocker = getActiveUser(blockerId);
        User blocked = getActiveUser(blockedId);

        // Chặn thì hủy luôn quan hệ bạn bè — nếu vẫn là bạn, người bị chặn vẫn thấy mình
        // trong danh sách bạn của họ rồi cứ thử nhắn tin và nhận lỗi, trải nghiệm rất tệ.
        // Xóa TRƯỚC khi insert: sau khi DataIntegrityViolationException xảy ra thì
        // transaction đã bị đánh dấu rollback-only, mọi thao tác ghi tiếp theo sẽ hỏng.
        friendshipRepository.deleteBetween(blockerId, blockedId);

        try {
            blockedUserRepository.saveAndFlush(BlockedUser.of(blocker, blocked));
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng lúc hoặc user double-click: unique index (blocker_id, blocked_id)
            // chặn dòng thứ 2. Trạng thái cuối cùng vẫn ĐÚNG (đã chặn), báo 409 là đủ.
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        // Idempotent: bỏ chặn người chưa từng chặn KHÔNG phải lỗi — kết quả mong muốn
        // ("không còn chặn") đã đạt được. Ném lỗi ở đây chỉ bắt frontend xử lý thừa.
        blockedUserRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockedUserRepository::delete);
    }

    private User getActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
