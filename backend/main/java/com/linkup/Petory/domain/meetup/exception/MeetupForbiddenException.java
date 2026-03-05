package com.linkup.Petory.domain.meetup.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 모임 도메인 권한 부족 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class MeetupForbiddenException extends ApiException {

    public static final String ERROR_CODE = "MEETUP_FORBIDDEN";

    public MeetupForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static MeetupForbiddenException organizerCannotCancel() {
        return new MeetupForbiddenException("주최자는 참가 취소할 수 없습니다.");
    }
}
