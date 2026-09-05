package com.chatsphere.user.dto;

/**
 * Quan hệ giữa user đang đăng nhập và user được xem — KHÔNG lưu DB,
 * tính tại thời điểm truy vấn để frontend biết nên hiển thị nút gì.
 * <p>BLOCKED ở đây chỉ mang nghĩa "TÔI chặn họ"; trường hợp "họ chặn tôi" bị lọc
 * khỏi kết quả tìm kiếm ngay ở tầng query, không bao giờ tới được đây.
 */
public enum RelationshipStatus {

    SELF,             // chính mình
    FRIEND,           // đã là bạn          → nút "Nhắn tin" / "Hủy kết bạn"
    REQUEST_SENT,     // tôi đã gửi lời mời  → nút "Thu hồi lời mời"
    REQUEST_RECEIVED, // họ gửi cho tôi      → nút "Chấp nhận" / "Từ chối"
    BLOCKED,          // tôi đã chặn họ      → nút "Bỏ chặn"
    NONE              // chưa có quan hệ     → nút "Kết bạn"
}
