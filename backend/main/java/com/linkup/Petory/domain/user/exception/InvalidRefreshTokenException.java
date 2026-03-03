package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * Refresh Token 관련 오류 시 발생하는 예외.
 * HTTP 401 Unauthorized
 */
public class InvalidRefreshTokenException extends ApiException {

    public static final String ERROR_CODE = "INVALID_REFRESH_TOKEN";

    public InvalidRefreshTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }

    public static InvalidRefreshTokenException invalid() {
        return new InvalidRefreshTokenException("유효하지 않은 Refresh Token입니다.");
    }

    public static InvalidRefreshTokenException notFound() {
        return new InvalidRefreshTokenException("Refresh Token을 찾을 수 없습니다.");
    }

    public static InvalidRefreshTokenException expired() {
        return new InvalidRefreshTokenException("Refresh Token이 만료되었습니다.");
    }
}
