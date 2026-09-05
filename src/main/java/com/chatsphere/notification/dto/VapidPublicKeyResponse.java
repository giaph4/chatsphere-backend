package com.chatsphere.notification.dto;

/**
 * Khóa công khai VAPID cho {@code PushManager.subscribe()} phía trình duyệt.
 *
 * <p>Dùng record chứ KHÔNG dùng {@code Map.of("public_key", ...)}: quy ước đặt tên
 * {@code snake_case} của Jackson chỉ áp dụng cho thuộc tính của POJO, <b>không</b> cho khóa của
 * Map — trả Map sẽ cho ra {@code publicKey} camelCase, lệch với toàn bộ phần còn lại của API và
 * bắt frontend phải nhớ một ngoại lệ.
 *
 * <p>Công khai khóa này là ĐÚNG theo thiết kế Web Push — nó chỉ để trình duyệt xác minh chữ ký
 * của server; gửi được thông báo hay không phụ thuộc khóa bí mật.
 *
 * @param enabled   false khi {@code app.push.enabled=false} hoặc thiếu khóa — frontend ẩn hẳn
 *                  nút bật thông báo thay vì để người dùng bấm vào một tính năng không chạy
 * @param publicKey chuỗi rỗng khi {@code enabled=false}
 */
public record VapidPublicKeyResponse(
        boolean enabled,
        String publicKey
) {
}
