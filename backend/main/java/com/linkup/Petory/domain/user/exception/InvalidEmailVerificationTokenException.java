package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이메일 인증 토큰이 유효하지 않거나 만료된 경우 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class InvalidEmailVerificationTokenException extends ApiException {

    public static final String ERROR_CODE = "INVALID_EMAIL_VERIFICATION_TOKEN";

    public InvalidEmailVerificationTokenException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static InvalidEmailVerificationTokenException invalidOrExpired() {
        return new InvalidEmailVerificationTokenException("유효하지 않거나 만료된 인증 토큰입니다.");
    }

    public static InvalidEmailVerificationTokenException cannotExtract() {
        return new InvalidEmailVerificationTokenException("토큰에서 정보를 추출할 수 없습니다.");
    }
}
