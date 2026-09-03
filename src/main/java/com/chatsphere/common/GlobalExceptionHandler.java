package com.chatsphere.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Lỗi nghiệp vụ chủ động — status & message lấy từ ErrorCode.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Business exception: {} - {}", code.name(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(ApiError.of(code, ex.getMessage())));
    }

    /**
     * @Valid trên @RequestBody thất bại — gộp lỗi từng field vào message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(ApiError.of(ErrorCode.VALIDATION_ERROR, detail)));
    }

    /**
     * @Validated trên @RequestParam / @PathVariable thất bại.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(ApiError.of(ErrorCode.VALIDATION_ERROR, ex.getMessage())));
    }

    /**
     * Ném từ tầng method-security (@PreAuthorize) hoặc code nghiệp vụ.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(ApiError.of(ErrorCode.ACCESS_DENIED)));
    }

    /**
     * Lưới cuối: mọi lỗi ngoài dự kiến -> 500, log đầy đủ, không lộ chi tiết.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(ApiError.of(ErrorCode.INTERNAL_ERROR)));
    }
}