package com.chatsphere.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ---------- Chung (mọi module) ----------
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),

    // ---------- Auth (Phase 1 — UC-01..UC-07) ----------
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên người dùng đã tồn tại"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa xác thực email"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa"),
    INVALID_VERIFICATION_TOKEN(HttpStatus.BAD_REQUEST, "Mã xác thực không hợp lệ hoặc đã hết hạn"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau 15 phút"),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"),
    WRONG_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    TOO_MANY_OTP_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Bạn vừa yêu cầu mã xác thực, vui lòng đợi rồi thử lại"),

    // ---------- User & Friend (Phase 2 — UC-08..UC-13) ----------
    CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "Không thể gửi lời mời kết bạn cho chính mình"),
    ALREADY_FRIENDS(HttpStatus.CONFLICT, "Hai người đã là bạn bè"),
    FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "Đã gửi lời mời kết bạn, đang chờ phản hồi"),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời kết bạn"),
    FRIEND_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "Lời mời này đã được xử lý trước đó"),
    NOT_FRIENDS(HttpStatus.NOT_FOUND, "Hai người chưa phải là bạn bè"),
    // Cùng một mã cho cả 2 chiều chặn: nếu phân biệt, người gửi sẽ biết chắc mình bị chặn —
    // đúng thứ mà tính năng chặn cố tình che giấu.
    USER_BLOCKED(HttpStatus.FORBIDDEN, "Không thể thực hiện thao tác với người dùng này"),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "Không thể tự chặn chính mình"),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "Bạn đã chặn người dùng này"),

    // ---------- Chat (Phase 3 — UC-14..UC-25, REST) ----------
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện"),
    NOT_CONVERSATION_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của cuộc trò chuyện này"),
    ALREADY_CONVERSATION_MEMBER(HttpStatus.CONFLICT, "Người này đã ở trong nhóm"),
    GROUP_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "Chỉ trưởng nhóm mới được thực hiện thao tác này"),
    NOT_A_GROUP_CONVERSATION(HttpStatus.BAD_REQUEST, "Thao tác này chỉ áp dụng cho nhóm chat"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"),
    MESSAGE_RECALL_FORBIDDEN(HttpStatus.FORBIDDEN, "Chỉ người gửi mới được thu hồi tin nhắn"),
    MESSAGE_ALREADY_RECALLED(HttpStatus.CONFLICT, "Tin nhắn đã được thu hồi trước đó"),
    MESSAGE_RECALL_WINDOW_EXPIRED(HttpStatus.CONFLICT, "Đã quá thời gian cho phép thu hồi tin nhắn (5 phút)"),
    MESSAGE_NOT_IN_CONVERSATION(HttpStatus.BAD_REQUEST, "Tin nhắn được reply không thuộc cuộc trò chuyện này"),

    // ---------- Real-time / WebSocket (Phase 4 — UC-18, UC-24) ----------
    // HttpStatus ở đây KHÔNG dùng để set mã HTTP (STOMP không có khái niệm này) — chỉ giữ để
    // ErrorCode có duy nhất một hình dạng, và để cùng mã lỗi dùng lại được ở REST khi cần.
    WEBSOCKET_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Kết nối WebSocket thiếu token hoặc token không hợp lệ"),
    WEBSOCKET_SUBSCRIPTION_DENIED(HttpStatus.FORBIDDEN, "Bạn không được phép theo dõi kênh này"),

    // ---------- Media & Notification (Phase 5 — UC-19, UC-22, UC-23, UC-26..UC-28) ----------
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "Chưa chọn file hoặc file rỗng"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt quá dung lượng cho phép"),
    // Thông báo cố ý nói rõ "định dạng thật": người dùng đổi đuôi file vì tưởng lách được cần
    // hiểu vì sao bị chặn, còn kẻ cố tình thì dù sao cũng đã biết mình vừa làm gì.
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng file thật không được phép"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Tải file lên thất bại, vui lòng thử lại"),
    MESSAGE_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "Tin nhắn phải có nội dung hoặc tệp đính kèm"),
    ATTACHMENT_REQUIRED(HttpStatus.BAD_REQUEST, "Loại tin nhắn này bắt buộc phải có tệp đính kèm"),
    INVALID_EMOJI(HttpStatus.BAD_REQUEST, "Biểu tượng cảm xúc không hợp lệ"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"),
    PUSH_SUBSCRIPTION_INVALID(HttpStatus.BAD_REQUEST, "Thông tin đăng ký push notification không hợp lệ");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}