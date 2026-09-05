package com.chatsphere.media;

import com.chatsphere.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload media (03_CODE_ROADMAP.md 5.1).
 *
 * <p>Endpoint yêu cầu đăng nhập (mặc định {@code anyRequest().authenticated()} của SecurityConfig)
 * nhưng KHÔNG gắn với hội thoại nào: cùng một file có thể được gửi lại vào nhiều hội thoại, và
 * lúc upload người dùng còn chưa chắc sẽ gửi cho ai. Quyền gửi vào hội thoại nào vẫn được
 * {@code MessageService} kiểm tra ở bước đính kèm.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Tải ảnh/file/voice lên object storage (Phase 5)")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải 1 file lên — kiểm tra kiểu thật bằng magic byte, không tin đuôi file")
    public ApiResponse<UploadedFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "FILE") MediaCategory category) {
        return ApiResponse.success(mediaService.upload(file, category));
    }
}
