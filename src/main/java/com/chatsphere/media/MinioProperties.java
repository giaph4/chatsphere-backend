package com.chatsphere.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình object storage, đọc từ {@code app.minio.*} (đã khai sẵn ở application-dev/prod.yaml
 * từ Phase 0).
 *
 * @param endpoint  địa chỉ MinIO/S3, ví dụ {@code http://localhost:9000}
 * @param accessKey khóa truy cập
 * @param secretKey khóa bí mật
 * @param bucket    tên bucket chứa toàn bộ media của ứng dụng
 * @param publicUrl địa chỉ mà TRÌNH DUYỆT dùng để tải file. Thường khác {@code endpoint}: trong
 *                  Docker, backend gọi MinIO qua tên service nội bộ ({@code http://minio:9000})
 *                  còn trình duyệt phải đi qua tên miền công khai. Bỏ trống thì lấy bằng
 *                  {@code endpoint} — đúng cho môi trường dev chạy mọi thứ trên localhost.
 */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String publicUrl
) {

    public MinioProperties {
        if (publicUrl == null || publicUrl.isBlank()) {
            publicUrl = endpoint;
        }
    }
}
