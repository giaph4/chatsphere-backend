package com.chatsphere.common;

import java.util.List;
import java.util.UUID;

/**
 * Phân trang kiểu cursor — dùng cho danh sách tin nhắn (01_SYSTEM_DESIGN.md §8.1:
 * "cursor-based pagination hiệu quả hơn offset-based khi dữ liệu lớn và liên tục thay đổi").
 * <p>
 * Khác {@link PageResponse}: không có tổng số trang/tổng số phần tử (COUNT(*) trên bảng
 * messages — bảng lớn nhất hệ thống — mỗi lần lấy lịch sử là quá đắt và vô nghĩa với UI
 * dạng cuộn vô hạn). {@code nextCursor} là ID của tin nhắn cuối cùng trong trang này,
 * truyền lại ở lần gọi sau để lấy trang kế tiếp; {@code null} nghĩa là đã hết.
 */
public record CursorPageResponse<T>(
        List<T> items,
        UUID nextCursor,
        boolean hasNext
) {
}
