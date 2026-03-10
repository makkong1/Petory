package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 비밀번호 관련 오류 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class InvalidPasswordException extends ApiException {

    public static final String ERROR_CODE = "INVALID_PASSWORD";

    public InvalidPasswordException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static InvalidPasswordException required() {
        return new InvalidPasswordException("비밀번호는 필수입니다.");
    }

    public static InvalidPasswordException mismatch() {
        return new InvalidPasswordException("현재 비밀번호가 일치하지 않습니다.");
    }

    public static InvalidPasswordException bothRequired() {
        return new InvalidPasswordException("현재 비밀번호와 새 비밀번호를 모두 입력해주세요.");
    }
}
