package com.chatsphere.notification.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.notification.domain.PushSubscription;
import com.chatsphere.notification.dto.PushSubscriptionRequest;
import com.chatsphere.notification.repository.PushSubscriptionRepository;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Quản lý danh sách thiết bị đã cho phép nhận Web Push (UC-23).
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * Đăng ký (hoặc cập nhật) một thiết bị.
     *
     * <p>{@code endpoint} là duy nhất theo thiết bị nên thao tác này là upsert, không phải
     * insert: người dùng bấm "cho phép" lại trên cùng máy, hoặc đăng nhập tài khoản KHÁC trên
     * máy đó, đều gửi lên đúng endpoint cũ. Trường hợp thứ hai bắt buộc phải chuyển chủ sở hữu
     * sang tài khoản mới — nếu không, thiết bị sẽ tiếp tục nhận thông báo của người đã đăng xuất.
     */
    @Transactional
    public void subscribe(UUID userId, PushSubscriptionRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        subscriptionRepository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> {
                            existing.setUser(user);
                            existing.setP256dhKey(request.p256dhKey());
                            existing.setAuthKey(request.authKey());
                        },
                        () -> subscriptionRepository.save(PushSubscription.of(
                                user, request.endpoint(), request.p256dhKey(), request.authKey())));
    }

    /**
     * Hủy đăng ký. Idempotent, và KHÔNG kiểm tra endpoint có thuộc về người gọi hay không:
     * biết được endpoint nghĩa là đang ngồi trên chính thiết bị đó. Bắt buộc đúng chủ sẽ làm
     * hỏng ca dùng thật — đăng xuất tài khoản cũ rồi mới gỡ đăng ký.
     */
    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }
}
