package com.linkup.Petory.domain.file.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 요청한 파일을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class FileNotFoundException extends ApiException {

    public static final String ERROR_CODE = "FILE_NOT_FOUND";

    public FileNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public static FileNotFoundException forPath(String path) {
        return new FileNotFoundException("요청한 파일을 찾을 수 없습니다: " + path);
    }
}
