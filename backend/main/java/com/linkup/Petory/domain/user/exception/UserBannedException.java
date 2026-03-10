package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 영구 차단된 계정 접근 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class UserBannedException extends ApiException {

    public static final String ERROR_CODE = "USER_BANNED";

    public UserBannedException() {
        super("영구 차단된 계정입니다. 웹사이트 이용이 불가능합니다.", HttpStatus.FORBIDDEN, ERROR_CODE);
    }
}
