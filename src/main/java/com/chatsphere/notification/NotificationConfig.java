package com.chatsphere.notification;

import com.chatsphere.notification.service.VapidProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bật binding cho {@code app.push.*} — cùng quy ước với {@code SecurityConfig} (JwtProperties)
 * và {@code MinioConfig} (MinioProperties): mỗi nhóm cấu hình được kích hoạt tại đúng module
 * sở hữu nó, không gom hết vào một class chung.
 */
@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class NotificationConfig {
}
