package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 사용자 권한 부족 시 발생하는 예외 (예: 본인 프로필만 수정 가능).
 * HTTP 403 Forbidden
 */
public class UserForbiddenException extends ApiException {

    public static final String ERROR_CODE = "USER_FORBIDDEN";

    public UserForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static UserForbiddenException ownProfileOnly() {
        return new UserForbiddenException("본인의 프로필만 수정할 수 있습니다.");
    }
}
