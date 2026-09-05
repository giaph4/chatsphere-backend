package com.chatsphere.media;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bảo đảm bucket tồn tại ngay khi ứng dụng khởi động (03_CODE_ROADMAP.md 5.1).
 *
 * <p><b>Vì sao không để người vận hành tự tạo tay?</b> Bucket thiếu chỉ lộ ra ở lần upload đầu
 * tiên — nghĩa là lỗi rơi vào mặt người dùng thật, giữa một thao tác nghiệp vụ. Kiểm tra lúc
 * khởi động biến nó thành lỗi của người triển khai, xảy ra đúng lúc deploy, đọc được ngay trong log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer {

    private final MinioClient minioClient;
    private final MinioProperties props;

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
                log.info("Đã tạo bucket MinIO '{}'", props.bucket());
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.bucket())
                    .config(publicReadPolicy(props.bucket()))
                    .build());
        } catch (Exception e) {
            // KHÔNG ném tiếp: MinIO chết không được phép làm sập cả ứng dụng chat. Chat chữ vẫn
            // chạy bình thường, chỉ upload file là hỏng — và MediaService sẽ báo lỗi rõ ràng
            // cho đúng thao tác đó thay vì cả hệ thống không khởi động được.
            log.error("Không khởi tạo được bucket MinIO '{}' — chức năng upload sẽ không dùng được: {}",
                    props.bucket(), e.getMessage());
        }
    }

    /**
     * Mở quyền đọc ẩn danh cho object trong bucket.
     *
     * <p><b>Đánh đổi có ý thức:</b> ai có URL đều tải được file, kể cả người ngoài hội thoại.
     * Chấp nhận được ở phạm vi học tập vì tên object là UUID ngẫu nhiên 128 bit — không đoán
     * được, cũng không liệt kê được (policy chỉ cho {@code GetObject}, không cho
     * {@code ListBucket}) — đổi lại thẻ {@code <img src>} hoạt động thẳng, không cần proxy hay
     * ký URL. Hệ thống thật chứa dữ liệu nhạy cảm phải dùng bucket private + presigned URL có
     * hạn; xem 04_PRODUCTION_DEPLOYMENT.md.
     */
    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }
}
