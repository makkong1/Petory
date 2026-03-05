package com.linkup.Petory.domain.file.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 파일 업로드 검증 실패 시 발생하는 예외 (용량, 타입, 확장자).
 * HTTP 400 Bad Request
 */
public class FileUploadValidationException extends ApiException {

    public static final String ERROR_CODE = "FILE_UPLOAD_VALIDATION";

    public FileUploadValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static FileUploadValidationException sizeExceeded() {
        return new FileUploadValidationException("파일은 최대 5MB까지 업로드할 수 있습니다.");
    }

    public static FileUploadValidationException invalidContentType() {
        return new FileUploadValidationException("이미지 파일(jpg, png, gif, webp)만 업로드할 수 있습니다.");
    }

    public static FileUploadValidationException invalidExtension() {
        return new FileUploadValidationException("허용되지 않은 이미지 확장자입니다.");
    }
}
