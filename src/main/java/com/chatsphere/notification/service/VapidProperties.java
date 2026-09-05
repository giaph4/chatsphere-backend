package com.chatsphere.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Khóa VAPID cho Web Push (UC-23).
 *
 * <p>VAPID (Voluntary Application Server Identification) là cách server tự chứng minh danh tính
 * với dịch vụ đẩy của trình duyệt: mỗi gói push được ký bằng {@code privateKey}, trình duyệt
 * kiểm tra bằng {@code publicKey} mà nó đã nhận lúc người dùng bấm "cho phép thông báo". Nhờ
 * vậy không ai khác gửi được thông báo vào ứng dụng của bạn dù họ biết endpoint.
 *
 * <p>{@code enabled=false} (mặc định) tắt hẳn tính năng: dự án học tập chạy local thường không
 * sinh cặp khóa, và khi đó push phải im lặng bỏ qua thay vì làm cả luồng thông báo đổ lỗi.
 *
 * @param subject địa chỉ liên hệ ({@code mailto:...}) để dịch vụ đẩy báo khi có sự cố
 */
@ConfigurationProperties(prefix = "app.push")
public record VapidProperties(
        boolean enabled,
        String publicKey,
        String privateKey,
        String subject
) {

    public VapidProperties {
        if (subject == null || subject.isBlank()) {
            subject = "mailto:no-reply@chatsphere.local";
        }
        // Bật mà thiếu khóa thì coi như tắt — sai cấu hình không được phép làm sập ứng dụng,
        // nhưng cũng không được im lặng giả vờ đang hoạt động (PushNotificationService sẽ log).
        if (enabled && (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank())) {
            enabled = false;
        }
    }
}
