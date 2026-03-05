package com.linkup.Petory.domain.file.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 파일 도메인 일반 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class FileValidationException extends ApiException {

    public static final String ERROR_CODE = "FILE_VALIDATION";

    public FileValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static FileValidationException emptyFile() {
        return new FileValidationException("파일을 선택해주세요.");
    }

    public static FileValidationException emptyPath() {
        return new FileValidationException("파일 경로가 비어 있습니다.");
    }

    public static FileValidationException invalidPath(String path) {
        return new FileValidationException("잘못된 파일 경로 요청입니다.");
    }
}
