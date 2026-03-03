package com.linkup.Petory.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * API 비즈니스 예외 베이스 클래스.
 * HTTP 상태 코드와 에러 코드를 포함하여 프론트엔드에서 일관된 에러 처리 가능.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public ApiException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = null;
    }

    public ApiException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public ApiException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = null;
    }
}
