package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 휴면 처리된 계정이 재활성화 확인 없이 로그인을 시도할 때 발생하는 예외. HTTP 403 Forbidden
 */
public class UserDormantException extends ApiException {

    public static final String ERROR_CODE = "USER_DORMANT";

    public UserDormantException() {
        super("장기간 미접속으로 휴면 처리된 계정입니다. 재활성화 확인이 필요합니다.", HttpStatus.FORBIDDEN, ERROR_CODE);
    }
}
