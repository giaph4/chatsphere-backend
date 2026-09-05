package com.chatsphere.user.service;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import com.chatsphere.user.domain.User;
import com.chatsphere.user.domain.UserSettings;
import com.chatsphere.user.dto.UpdateSettingsRequest;
import com.chatsphere.user.dto.UserSettingsResponse;
import com.chatsphere.user.mapper.UserMapper;
import com.chatsphere.user.repository.UserRepository;
import com.chatsphere.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserSettingsResponse getSettings(UUID currentUserId) {
        return userMapper.toSettingsResponse(getOrCreate(currentUserId));
    }

    @Transactional
    public UserSettingsResponse updateSettings(UUID currentUserId, UpdateSettingsRequest request) {
        UserSettings settings = getOrCreate(currentUserId);
        settings.setOnlineVisibility(request.onlineVisibility());
        settings.setCallPermission(request.callPermission());
        settings.setNotificationEnabled(request.notificationEnabled());
        return userMapper.toSettingsResponse(settings); // dirty checking tự UPDATE khi commit
    }

    /**
     * Tạo settings mặc định khi user đọc lần đầu (lazy), thay vì tạo sẵn lúc đăng ký.
     * <p>Chọn lazy vì: (1) không phải viết migration backfill cho user đã đăng ký ở Phase 1,
     * (2) không phải sửa AuthService.register() đã test xong.
     */
    @Transactional
    public UserSettings getOrCreate(UUID userId) {
        return settingsRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            try {
                return settingsRepository.saveAndFlush(UserSettings.defaultsFor(user));
            } catch (DataIntegrityViolationException e) {
                // 2 request đồng thời của cùng user (mở 2 tab): PK trùng, một bên thua.
                // Đọc lại bản ghi bên thắng vừa tạo.
                return settingsRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
            }
        });
    }
}
