package com.chatsphere.user.mapper;

import com.chatsphere.user.domain.FriendRequest;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserSettings;
import com.chatsphere.user.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;

/**
 * Mapping Entity -&gt; DTO, sinh code tại COMPILE-TIME (xem
 * target/generated-sources/annotations/.../UserMapperImpl.java).
 * <p>
 * unmappedTargetPolicy = ERROR: thêm field mới vào DTO mà quên map thì build FAIL ngay,
 * thay vì âm thầm trả về null lúc chạy.
 * <p>
 * Mapper cố ý STATELESS — không đọc SecurityContextHolder để tự quyết định có trả email
 * hay không. Lý do: ở Phase 4 các service này được gọi lại từ luồng STOMP, nơi
 * SecurityContext (ThreadLocal của HTTP request) rỗng. Việc chọn toProfileResponse hay
 * toSummaryResponse là quyết định của service.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface UserMapper {

    UserProfileResponse toProfileResponse(User user);

    UserSummaryResponse toSummaryResponse(User user);

    List<UserSummaryResponse> toSummaryResponses(List<User> users);

    FriendRequestResponse toFriendRequestResponse(FriendRequest request);

    UserSettingsResponse toSettingsResponse(UserSettings settings);

    /** relationship do service tính sẵn cho cả trang rồi truyền vào (tránh N+1). */
    default UserSearchResultResponse toSearchResult(User user, RelationshipStatus relationship) {
        return new UserSearchResultResponse(toSummaryResponse(user), relationship);
    }

    default FriendResponse toFriendResponse(User friend, Instant friendsSince) {
        return new FriendResponse(toSummaryResponse(friend), friendsSince);
    }
}
