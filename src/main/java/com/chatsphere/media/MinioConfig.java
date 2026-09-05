package com.chatsphere.media;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khởi tạo {@link MinioClient} từ {@link MinioProperties} (03_CODE_ROADMAP.md 5.1).
 *
 * <p>Việc bảo đảm bucket tồn tại nằm ở {@link MinioBucketInitializer} — một tác vụ khởi động có
 * thể thất bại, không nên trộn vào hàm dựng client vốn phải luôn thành công.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}
