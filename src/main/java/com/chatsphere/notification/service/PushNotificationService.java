package com.chatsphere.notification.service;

import com.chatsphere.notification.domain.PushSubscription;
import com.chatsphere.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.UUID;

/**
 * Gửi thông báo tới trình duyệt ngay cả khi người dùng đã đóng tab (UC-23,
 * 03_CODE_ROADMAP.md 5.3).
 *
 * <p><b>Web Push khác thông báo trong app ở điểm căn bản:</b> nó không đi qua kết nối WebSocket
 * của ta (kết nối đó đã đứt khi tab đóng) mà qua dịch vụ đẩy của chính hãng trình duyệt —
 * FCM với Chrome, Mozilla autopush với Firefox. Server ta gửi một gói ĐÃ MÃ HÓA tới endpoint đó;
 * dịch vụ trung gian chuyển tiếp mà không đọc được nội dung, Service Worker phía máy người dùng
 * mới giải mã và hiển thị.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final VapidProperties vapidProperties;

    private PushService pushService;

    /**
     * Thư viện web-push dùng đường cong elliptic P-256 mà JDK tiêu chuẩn không cung cấp đủ —
     * phải nạp BouncyCastle làm nhà cung cấp thuật toán. Chỉ nạp một lần cho cả JVM.
     */
    @PostConstruct
    void init() {
        if (!vapidProperties.enabled()) {
            log.info("Web Push đang TẮT (app.push.enabled=false hoặc thiếu khóa VAPID) — "
                    + "thông báo trong ứng dụng vẫn hoạt động bình thường");
            return;
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(
                    vapidProperties.publicKey(), vapidProperties.privateKey(), vapidProperties.subject());
            log.info("Web Push đã sẵn sàng");
        } catch (Exception e) {
            log.error("Khóa VAPID không hợp lệ — Web Push sẽ bị bỏ qua: {}", e.getMessage());
        }
    }

    /**
     * Gửi tới MỌI thiết bị của một người.
     *
     * <p>Không bao giờ ném lỗi ra ngoài: push là tính năng phụ trợ, hỏng nó không được phép làm
     * hỏng luồng thông báo chính (thông báo trong app đã được lưu DB trước đó rồi).
     */
    @Transactional
    public void sendToUser(UUID userId, String title, String body) {
        if (pushService == null) {
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        String payload = """
                {"title":%s,"body":%s}""".formatted(jsonString(title), jsonString(body));

        for (PushSubscription subscription : subscriptions) {
            sendOne(subscription, payload);
        }
    }

    private void sendOne(PushSubscription subscription, String payload) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dhKey(),
                    subscription.getAuthKey(),
                    payload);

            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            // 404/410 = trình duyệt đã hủy đăng ký này (gỡ app, xóa dữ liệu site). Đây là cách
            // DUY NHẤT ta biết được điều đó, nên phải dọn ngay — giữ lại thì mỗi thông báo về
            // sau đều tốn một lượt gọi mạng chắc chắn thất bại.
            if (status == 404 || status == 410) {
                subscriptionRepository.delete(subscription);
                log.debug("Đã xóa push subscription hết hiệu lực (HTTP {})", status);
            } else if (status >= 400) {
                log.warn("Dịch vụ đẩy trả về HTTP {} cho endpoint {}", status, subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("Gửi Web Push thất bại tới endpoint {}: {}", subscription.getEndpoint(), e.getMessage());
        }
    }

    /**
     * Tự escape thay vì kéo cả ObjectMapper vào: payload chỉ có 2 trường cố định, nhưng nội dung
     * tin nhắn của người dùng CHẮC CHẮN sẽ có dấu ngoặc kép và xuống dòng — ghép chuỗi thô sẽ
     * tạo ra JSON hỏng và Service Worker im lặng không hiện gì.
     */
    private String jsonString(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
