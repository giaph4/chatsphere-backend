package com.chatsphere.media;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Nhận file từ người dùng, kiểm tra thật kỹ rồi mới đẩy lên object storage
 * (03_CODE_ROADMAP.md 5.1).
 *
 * <p><b>Điểm học thuật quan trọng của Phase này — không bao giờ tin phần mở rộng tên file.</b>
 * {@code Content-Type} trong request và đuôi {@code .jpg} đều do CLIENT tự khai, sửa lại dễ như
 * đổi tên file. Cách duy nhất đáng tin là đọc vài byte đầu của nội dung thật ("magic byte" /
 * chữ ký định dạng): PNG luôn mở đầu bằng {@code 89 50 4E 47}, JPEG bằng {@code FF D8 FF},
 * PDF bằng {@code %PDF}. Apache Tika giữ sẵn bảng tra các chữ ký này.
 *
 * <p>Vì sao nghiêm trọng: một file {@code .exe} đổi tên thành {@code .jpg} mà lọt lên storage
 * rồi phát tán qua link chat chính là kênh phát tán mã độc, và server đã ký tên bảo chứng cho
 * nó bằng tên miền của mình.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    /** Đủ để Tika nhận diện mọi chữ ký định dạng; không cần đọc cả file vào RAM. */
    private static final int MAGIC_BYTE_SAMPLE = 64;

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final Tika tika = new Tika();

    /**
     * Kiểm tra và tải 1 file lên storage.
     *
     * <p>Thứ tự kiểm tra CÓ CHỦ Ý — rẻ trước, đắt sau: rỗng → kích thước → kiểu thật → mới upload.
     * Đọc kiểu file của một file 25MB rồi mới phát hiện nó vượt hạn mức là lãng phí; và không
     * bao giờ được đẩy byte nào lên storage trước khi mọi kiểm tra đã qua.
     */
    public UploadedFile upload(MultipartFile file, MediaCategory category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > category.maxBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "File vượt quá giới hạn %d MB cho loại %s"
                            .formatted(category.maxBytes() / (1024 * 1024), category));
        }

        String detectedMimeType = detectRealMimeType(file);
        if (!category.allows(detectedMimeType)) {
            // Log kiểu KHAI BÁO lẫn kiểu THẬT: chênh lệch giữa hai giá trị này chính là dấu hiệu
            // của một lần thử vượt rào, đáng để người vận hành nhìn thấy.
            log.warn("Chặn upload: khai báo '{}' (tên '{}') nhưng nội dung thật là '{}'",
                    file.getContentType(), file.getOriginalFilename(), detectedMimeType);
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Định dạng '%s' không được phép cho loại %s".formatted(detectedMimeType, category));
        }

        String objectName = buildObjectName(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectName)
                    // -1 + partSize: SDK stream thẳng lên MinIO theo từng phần, không nạp cả
                    // file vào RAM — 20 người cùng gửi file 25MB sẽ không thổi bay heap.
                    .stream(in, -1, 10L * 1024 * 1024)
                    .contentType(detectedMimeType)
                    .build());
        } catch (Exception e) {
            log.error("Upload lên MinIO thất bại (object '{}')", objectName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return new UploadedFile(
                "%s/%s/%s".formatted(props.publicUrl(), props.bucket(), objectName),
                safeFileName(file.getOriginalFilename()),
                detectedMimeType,
                file.getSize(),
                category);
    }

    /**
     * Xác nhận một URL đính kèm thật sự trỏ vào bucket của hệ thống.
     *
     * <p>Không có chốt này, client gửi tin nhắn kèm {@code fileUrl} là địa chỉ bất kỳ trên
     * Internet — giao diện sẽ hiển thị nó y như một tệp nội bộ đã được kiểm duyệt, trong khi
     * nội dung nằm ngoài tầm kiểm soát và có thể đổi bất cứ lúc nào sau khi gửi.
     */
    public void assertManagedUrl(String fileUrl) {
        String expectedPrefix = "%s/%s/".formatted(props.publicUrl(), props.bucket());
        if (fileUrl == null || !fileUrl.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Tệp đính kèm phải là tệp đã tải lên qua /api/v1/media/upload");
        }
    }

    /**
     * Đọc {@value #MAGIC_BYTE_SAMPLE} byte đầu và tra bảng chữ ký định dạng.
     *
     * <p>CỐ Ý chỉ truyền nội dung, KHÔNG truyền tên file cho Tika: {@code Tika.detect(bytes, name)}
     * sẽ ưu tiên đuôi file khi nội dung mơ hồ — đúng thứ ta đang muốn loại bỏ.
     */
    private String detectRealMimeType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(MAGIC_BYTE_SAMPLE);
            return tika.detect(head);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * Tên object: {@code yyyy/MM/dd/<uuid>.<ext>}.
     *
     * <p>UUID thay cho tên gốc để tránh cả 3 vấn đề cùng lúc: hai người gửi {@code anh.jpg} ghi
     * đè nhau, tên file chứa {@code ../} leo ra ngoài thư mục (path traversal), và tên file tiết
     * lộ thông tin riêng tư. Tiền tố ngày tháng chỉ để con người dễ tìm và dễ đặt luật dọn dẹp
     * theo thời gian — object storage không có thư mục thật.
     */
    private String buildObjectName(String originalFilename) {
        String extension = extensionOf(originalFilename);
        String base = LocalDate.now().format(DATE_PREFIX) + "/" + UUID.randomUUID();
        return extension.isEmpty() ? base : base + "." + extension;
    }

    /** Chỉ giữ đuôi thuần chữ-số để nó không bao giờ tự mang theo ký tự đường dẫn. */
    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "";
    }

    /**
     * Tên hiển thị cho người nhận. Cắt bỏ mọi thành phần đường dẫn (IE cũ gửi nguyên
     * {@code C:\Users\...\anh.jpg}) và giới hạn độ dài đúng bằng cột DB.
     */
    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        String name = Arrays.stream(originalFilename.split("[/\\\\]"))
                .reduce((first, second) -> second)
                .orElse(originalFilename);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
