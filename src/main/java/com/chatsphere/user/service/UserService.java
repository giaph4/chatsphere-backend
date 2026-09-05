package com.chatsphere.user.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.common.PageResponse;
import com.chatsphere.user.domain.FriendRequest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserStatus;
import com.chatsphere.user.dto.UpdateProfileRequest;
import com.chatsphere.user.dto.UserProfileResponse;
import com.chatsphere.user.dto.UserSearchResultResponse;
import com.chatsphere.user.dto.UserSummaryResponse;
import com.chatsphere.user.dto.RelationshipStatus;
import com.chatsphere.user.mapper.UserMapper;
import com.chatsphere.user.repository.BlockedUserRepository;
import com.chatsphere.user.repository.FriendRequestRepository;
import com.chatsphere.user.repository.FriendshipRepository;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserMapper userMapper;

    public UserProfileResponse getMyProfile(UUID currentUserId) {
        return userMapper.toProfileResponse(getActiveUser(currentUserId));
    }

    /** Hồ sơ người KHÁC — trả bản rút gọn, không có email (xem lý do ở UserSummaryResponse). */
    public UserSummaryResponse getPublicProfile(UUID targetId) {
        return userMapper.toSummaryResponse(getActiveUser(targetId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID currentUserId, UpdateProfileRequest request) {
        User user = getActiveUser(currentUserId);
        // Ngữ nghĩa PUT = thay thế toàn bộ: null ở bio/dateOfBirth nghĩa là XÓA giá trị,
        // không phải "giữ nguyên" (xem Javadoc của UpdateProfileRequest).
        user.setDisplayName(request.displayName());
        user.setBio(request.bio());
        user.setDateOfBirth(request.dateOfBirth());
        return userMapper.toProfileResponse(user); // dirty checking tự UPDATE khi commit
    }

    /**
     * Tìm kiếm user kèm quan hệ với người đang tìm.
     * <p><b>Điểm mấu chốt về hiệu năng</b>: quan hệ được tính cho CẢ TRANG bằng đúng
     * 3 query (bạn bè / lời mời / chặn), bất kể trang có 20 hay 100 kết quả. Cách ngây thơ
     * — lặp từng user rồi hỏi "có phải bạn không?", "có lời mời không?" — cho ra 3×N query
     * (N+1 problem): 20 kết quả sẽ là 61 query thay vì 4.
     */
    public PageResponse<UserSearchResultResponse> search(UUID currentUserId, String keyword, Pageable pageable) {
        Page<User> page = userRepository.search(
                currentUserId, escapeLikeWildcards(keyword), UserStatus.ACTIVE, pageable);

        if (page.isEmpty()) {
            return PageResponse.empty(page);
        }

        List<UUID> ids = page.getContent().stream().map(User::getId).toList();

        // 3 query gộp cho cả trang
        Set<UUID> friendIds = friendshipRepository.findFriendIdsAmong(currentUserId, ids);
        Set<UUID> blockedIds = blockedUserRepository.findBlockedIdsAmong(currentUserId, ids);
        List<FriendRequest> pending = friendRequestRepository.findPendingAmong(currentUserId, ids);

        Set<UUID> sentTo = new HashSet<>();       // tôi đã gửi lời mời cho ai
        Set<UUID> receivedFrom = new HashSet<>(); // ai đã gửi lời mời cho tôi
        for (FriendRequest fr : pending) {
            if (fr.getSender().getId().equals(currentUserId)) {
                sentTo.add(fr.getReceiver().getId());
            } else {
                receivedFrom.add(fr.getSender().getId());
            }
        }

        return PageResponse.from(page, user -> userMapper.toSearchResult(
                user, resolveRelationship(user.getId(), friendIds, blockedIds, sentTo, receivedFrom)));
    }

    /** Thứ tự kiểm tra quan trọng: BLOCKED và FRIEND phải đứng trước các trạng thái lời mời. */
    private RelationshipStatus resolveRelationship(UUID targetId, Set<UUID> friendIds,
                                                   Set<UUID> blockedIds, Set<UUID> sentTo,
                                                   Set<UUID> receivedFrom) {
        if (blockedIds.contains(targetId)) return RelationshipStatus.BLOCKED;
        if (friendIds.contains(targetId)) return RelationshipStatus.FRIEND;
        if (sentTo.contains(targetId)) return RelationshipStatus.REQUEST_SENT;
        if (receivedFrom.contains(targetId)) return RelationshipStatus.REQUEST_RECEIVED;
        return RelationshipStatus.NONE;
    }

    /**
     * Vô hiệu hóa ký tự đại diện của LIKE trong từ khóa người dùng nhập.
     * <p>Không escape thì gõ "%" sẽ khớp TOÀN BỘ bảng users — người lạ dump được danh sách
     * người dùng chỉ bằng một ký tự. Phải escape '\' TRƯỚC, nếu không sẽ escape nhầm
     * chính các ký tự escape mình vừa thêm vào.
     */
    private String escapeLikeWildcards(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private User getActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // uploadAvatar() để tới Phase 5 — cần MediaService (MinIO, kiểm tra magic byte).
}
