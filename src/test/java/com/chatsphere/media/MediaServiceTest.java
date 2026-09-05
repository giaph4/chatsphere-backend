package com.chatsphere.media;

import com.chatsphere.common.BusinessException;
import com.chatsphere.common.ErrorCode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Kiểm tra chốt bảo mật quan trọng nhất của Phase 5 (03_CODE_ROADMAP.md 5.5): tin NỘI DUNG
 * file, không tin tên file hay Content-Type do client khai.
 *
 * <p>Unit test thuần (mock {@link MinioClient}, không Spring context, không container) — hoàn
 * toàn đủ vì mọi kiểm tra đều xảy ra TRƯỚC khi chạm tới storage. Chính điều đó cũng được khẳng
 * định luôn: file bị chặn thì {@code putObject} không được gọi lấy một lần.
 */
class MediaServiceTest {

    /** Chữ ký thật của file PNG: 89 50 4E 47 0D 0A 1A 0A. */
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13
    };

    /** Chữ ký thật của file thực thi Windows (.exe): "MZ". */
    private static final byte[] EXE_MAGIC = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

    private MinioClient minioClient;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        MinioProperties props = new MinioProperties(
                "http://localhost:9000", "key", "secret", "chatsphere-test", null);
        mediaService = new MediaService(minioClient, props);
    }

    // ---------- Magic byte ----------

    @Test
    void chan_file_exe_doi_duoi_thanh_jpg() throws Exception {
        // Kịch bản tấn công kinh điển: đổi tên virus.exe -> anh.jpg và khai Content-Type ảnh.
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "anh.jpg", "image/jpeg", EXE_MAGIC);

        assertThatThrownBy(() -> mediaService.upload(disguised, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);

        // Không một byte nào được đẩy lên storage.
        verify(minioClient, never()).putObject(any());
    }

    @Test
    void chap_nhan_file_png_that() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "anh.png", "image/png", PNG_MAGIC);

        UploadedFile uploaded = mediaService.upload(png, MediaCategory.IMAGE);

        assertThat(uploaded.fileType()).isEqualTo("image/png");
        assertThat(uploaded.fileName()).isEqualTo("anh.png");
        // Tên object là UUID, KHÔNG phải tên gốc — chống ghi đè và path traversal.
        assertThat(uploaded.fileUrl())
                .startsWith("http://localhost:9000/chatsphere-test/")
                .doesNotContain("anh.png")
                .endsWith(".png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void chan_file_dung_dinh_dang_nhung_sai_nhom() throws Exception {
        // PNG là ảnh hợp lệ, nhưng gửi vào nhóm VOICE thì vẫn phải bị chặn: allowlist tính theo
        // TỪNG nhóm, không phải "hễ là định dạng quen thuộc thì cho qua".
        MockMultipartFile png = new MockMultipartFile("file", "ghi-am.mp3", "audio/mpeg", PNG_MAGIC);

        assertThatThrownBy(() -> mediaService.upload(png, MediaCategory.VOICE))
                .isInstanceOf(BusinessException.class);
        verify(minioClient, never()).putObject(any());
    }

    // ---------- Kích thước & rỗng ----------

    @Test
    void chan_file_vuot_qua_gioi_han_cua_nhom() throws Exception {
        byte[] tooBig = new byte[(int) MediaCategory.IMAGE.maxBytes() + 1];
        System.arraycopy(PNG_MAGIC, 0, tooBig, 0, PNG_MAGIC.length);
        MockMultipartFile file = new MockMultipartFile("file", "to.png", "image/png", tooBig);

        assertThatThrownBy(() -> mediaService.upload(file, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        verify(minioClient, never()).putObject(any());
    }

    @Test
    void chan_file_rong() {
        MockMultipartFile empty = new MockMultipartFile("file", "rong.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> mediaService.upload(empty, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_EMPTY);
    }

    // ---------- Tên object ----------

    @Test
    void ten_object_khong_bao_gio_mang_theo_ky_tu_duong_dan() throws Exception {
        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../../../etc/passwd.png", "image/png", PNG_MAGIC);

        mediaService.upload(traversal, MediaCategory.IMAGE);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().object()).doesNotContain("..").doesNotContain("etc");
    }

    // ---------- assertManagedUrl ----------

    @Test
    void tu_choi_url_dinh_kem_tro_ra_ngoai_he_thong() {
        assertThatThrownBy(() -> mediaService.assertManagedUrl("https://evil.example.com/malware.png"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void chap_nhan_url_thuoc_bucket_cua_he_thong() {
        String ourUrl = "http://localhost:9000/chatsphere-test/2026/09/05/"
                + java.util.UUID.randomUUID() + ".png";

        mediaService.assertManagedUrl(ourUrl); // không ném lỗi
    }

    @Test
    void text_thuan_van_duoc_nhan_dien_dung_kieu() throws Exception {
        MockMultipartFile text = new MockMultipartFile(
                "file", "ghichu.txt", "text/plain", "xin chao".getBytes(StandardCharsets.UTF_8));

        UploadedFile uploaded = mediaService.upload(text, MediaCategory.FILE);

        assertThat(uploaded.fileType()).startsWith("text/plain");
    }
}
