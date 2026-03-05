package com.linkup.Petory.domain.file.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 파일 저장소 IO/시스템 오류 시 발생하는 예외.
 * HTTP 500 Internal Server Error
 */
public class FileStorageException extends ApiException {

    public static final String ERROR_CODE = "FILE_STORAGE";

    public FileStorageException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static FileStorageException initFailed(Throwable cause) {
        return new FileStorageException("업로드 디렉터리를 초기화할 수 없습니다.", cause);
    }

    public static FileStorageException prepareFailed(Throwable cause) {
        return new FileStorageException("업로드 디렉터리를 준비하지 못했습니다.", cause);
    }

    public static FileStorageException saveFailed(Throwable cause) {
        return new FileStorageException("파일 저장에 실패했습니다. 잠시 후 다시 시도해주세요.", cause);
    }
}
