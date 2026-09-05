package com.chatsphere.user.domain;

public enum FriendRequestStatus {

    PENDING,   // đang chờ người nhận phản hồi
    ACCEPTED,  // đã chấp nhận → sinh ra 1 bản ghi Friendship
    REJECTED,  // người nhận từ chối
    CANCELLED  // người gửi tự thu hồi lời mời
}
