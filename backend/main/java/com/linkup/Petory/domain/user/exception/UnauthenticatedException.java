package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 인증되지 않은 사용자 접근 시 발생하는 예외.
 * HTTP 401 Unauthorized
 */
public class UnauthenticatedException extends ApiException {

    public static final String ERROR_CODE = "UNAUTHENTICATED";

    public UnauthenticatedException() {
        super("인증되지 않은 사용자입니다.", HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }

    public UnauthenticatedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
}
