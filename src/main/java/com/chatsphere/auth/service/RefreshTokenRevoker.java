package com.chatsphere.auth.service;

import com.chatsphere.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thu hồi phiên trong MỘT TRANSACTION RIÊNG.
 *
 * <p>Lý do tồn tại: khi phát hiện refresh token bị dùng lại, {@code AuthService.refresh()} vừa phải
 * thu hồi mọi phiên của user, vừa phải ném lỗi để từ chối request. Nếu cả hai nằm chung một
 * transaction thì exception làm rollback luôn cả lệnh thu hồi — kẻ tấn công bị báo lỗi nhưng các
 * token vẫn sống nguyên. {@code REQUIRES_NEW} tách lệnh thu hồi sang transaction riêng, commit
 * độc lập trước khi exception được ném.
 *
 * <p>Phải là bean riêng chứ không thể là method của {@code AuthService}: {@code @Transactional}
 * hoạt động qua proxy, gọi method của chính mình sẽ đi thẳng vào bản thân object và bỏ qua proxy
 * → propagation không có tác dụng.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllByUserId(userId);
    }
}
