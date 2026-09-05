package com.chatsphere.user.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.PageResponse;
import com.chatsphere.user.domain.FriendRequest;
import com.chatsphere.user.domain.FriendRequestStatus;
import com.chatsphere.user.domain.Friendship;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.dto.FriendRequestResponse;
import com.chatsphere.user.dto.FriendResponse;
import com.chatsphere.user.mapper.UserMapper;
import com.chatsphere.user.repository.FriendRequestRepository;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final UserMapper userMapper;

    // ---------- Gửi lời mời ----------

    @Transactional
    public FriendRequestResponse sendRequest(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.CANNOT_FRIEND_SELF);
        }

        User sender = getActiveUser(senderId);
        User receiver = getActiveUser(receiverId);

        blockService.assertNotBlocked(senderId, receiverId);

        if (friendshipRepository.existsBetween(senderId, receiverId)) {
            throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
        }

        // TRƯỜNG HỢP CHÉO: B đã gửi lời mời cho A từ trước, giờ A lại gửi cho B.
        // Partial unique index KHÔNG chặn được vì đây là cặp (sender, receiver) khác nhau.
        // Xử lý như Facebook: coi hành động này là "đồng ý" lời mời của B.
        Optional<FriendRequest> incoming = friendRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(receiverId, senderId, FriendRequestStatus.PENDING);
        if (incoming.isPresent()) {
            log.debug("Lời mời chéo — tự động chấp nhận thay vì tạo request thứ 2");
            return acceptRequest(senderId, incoming.get().getId());
        }

        FriendRequest request = FriendRequest.of(sender, receiver);
        try {
            // saveAndFlush chứ không save: save() chỉ đưa entity vào persistence context,
            // INSERT thật chạy lúc commit — tức là SAU khi khối try/catch này kết thúc,
            // nên catch sẽ không bao giờ chạy và client nhận 500 thay vì 409.
            friendRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Partial unique index idx_friend_requests_pending đánh chặn: đã có 1 lời mời
            // PENDING cho đúng cặp này (double-click, hoặc 2 tab). Không cần khóa gì cả.
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }
        return userMapper.toFriendRequestResponse(request);
    }

    // ---------- Chấp nhận ----------

    /**
     * Chấp nhận lời mời. Đây là nơi tập trung nhiều race condition nhất của Phase 2:
     * <ol>
     *   <li>User bấm "Chấp nhận" 2 lần liên tiếp → 2 request song song.</li>
     *   <li>Lời mời chéo A→B và B→A cùng được chấp nhận → 2 Friendship cho cùng 1 cặp.</li>
     * </ol>
     * Cả hai đều được chặn bằng <b>ràng buộc DB</b>, không phải bằng {@code synchronized}:
     * synchronized chỉ khóa trong MỘT JVM, chạy 2 instance backend (rất bình thường khi
     * deploy thật) là vô tác dụng. Ràng buộc DB là nguồn chân lý duy nhất mà mọi instance
     * đều tôn trọng.
     */
    @Transactional
    public FriendRequestResponse acceptRequest(UUID currentUserId, UUID requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        // Chỉ NGƯỜI NHẬN mới được chấp nhận. Trả NOT_FOUND thay vì ACCESS_DENIED để không
        // tiết lộ "id lời mời này có tồn tại" cho người ngoài dò.
        if (!request.getReceiver().getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        blockService.assertNotBlocked(request.getSender().getId(), currentUserId);

        // Compare-and-set: chỉ người ĐẦU TIÊN đổi được PENDING -> ACCEPTED mới đi tiếp.
        int updated = friendRequestRepository.updateStatusIfPending(
                requestId, FriendRequestStatus.ACCEPTED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }

        // Friendship.between() tự sắp xếp user1 < user2 theo thứ tự UUID của PostgreSQL
        // (KHÔNG dùng UUID.compareTo — xem Javadoc của Friendship).
        // Kiểm tra tồn tại trước khi insert: nếu để unique index bắn
        // DataIntegrityViolationException thì transaction bị đánh dấu rollback-only và
        // toàn bộ hàm này hỏng, kể cả phần UPDATE trạng thái đã chạy thành công ở trên.
        if (!friendshipRepository.existsBetween(request.getSender().getId(), currentUserId)) {
            try {
                friendshipRepository.saveAndFlush(
                        Friendship.between(request.getSender(), request.getReceiver()));
            } catch (DataIntegrityViolationException e) {
                // Khe hở giữa exists và insert (2 lời mời chéo cùng được accept trong tích tắc).
                // Trạng thái cuối cùng vẫn ĐÚNG — nhưng transaction đã hỏng, phải để lỗi
                // nổi lên cho client thử lại thay vì commit dữ liệu nửa vời.
                log.warn("Friendship trùng khi accept requestId={}", requestId, e);
                throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
            }
        }

        request.setStatus(FriendRequestStatus.ACCEPTED); // đồng bộ entity trong persistence context
        return userMapper.toFriendRequestResponse(request);
    }

    // ---------- Từ chối / thu hồi ----------

    @Transactional
    public void rejectRequest(UUID currentUserId, UUID requestId) {
        changeStatus(currentUserId, requestId, FriendRequestStatus.REJECTED, Actor.RECEIVER);
    }

    @Transactional
    public void cancelRequest(UUID currentUserId, UUID requestId) {
        changeStatus(currentUserId, requestId, FriendRequestStatus.CANCELLED, Actor.SENDER);
    }

    private enum Actor {SENDER, RECEIVER}

    private void changeStatus(UUID currentUserId, UUID requestId,
                              FriendRequestStatus newStatus, Actor allowedActor) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));

        UUID allowedId = allowedActor == Actor.SENDER
                ? request.getSender().getId()
                : request.getReceiver().getId();
        if (!allowedId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        if (friendRequestRepository.updateStatusIfPending(requestId, newStatus) == 0) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }
    }

    // ---------- Danh sách ----------

    public PageResponse<FriendResponse> getFriends(UUID currentUserId, Pageable pageable) {
        return PageResponse.from(
                friendshipRepository.findAllWithUsersByUserId(currentUserId, pageable),
                friendship -> {
                    // Mỗi cặp chỉ lưu 1 dòng → phải tự xác định "người kia" là ai.
                    User other = friendship.getUser1().getId().equals(currentUserId)
                            ? friendship.getUser2()
                            : friendship.getUser1();
                    return userMapper.toFriendResponse(other, friendship.getCreatedAt());
                });
    }

    public PageResponse<FriendRequestResponse> getReceivedRequests(UUID currentUserId, Pageable pageable) {
        return PageResponse.from(
                friendRequestRepository.findReceivedWithUsers(
                        currentUserId, FriendRequestStatus.PENDING, pageable),
                userMapper::toFriendRequestResponse);
    }

    public PageResponse<FriendRequestResponse> getSentRequests(UUID currentUserId, Pageable pageable) {
        return PageResponse.from(
                friendRequestRepository.findSentWithUsers(
                        currentUserId, FriendRequestStatus.PENDING, pageable),
                userMapper::toFriendRequestResponse);
    }

    @Transactional
    public void removeFriend(UUID currentUserId, UUID friendId) {
        if (friendshipRepository.deleteBetween(currentUserId, friendId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FRIENDS);
        }
    }

    private User getActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
