package com.linkup.Petory.domain.user.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이용제한 중인 계정 접근 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class UserSuspendedException extends ApiException {

    public static final String ERROR_CODE = "USER_SUSPENDED";

    private final LocalDateTime suspendedUntil;

    public UserSuspendedException(LocalDateTime suspendedUntil) {
        super(
            String.format("이용제한 중인 계정입니다. 해제일: %s", suspendedUntil),
            HttpStatus.FORBIDDEN,
            ERROR_CODE
        );
        this.suspendedUntil = suspendedUntil;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }
}
