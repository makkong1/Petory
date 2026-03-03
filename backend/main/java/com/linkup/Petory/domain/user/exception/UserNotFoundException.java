package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class UserNotFoundException extends ApiException {

    public static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
