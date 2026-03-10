package com.linkup.Petory.domain.meetup.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 모임 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class MeetupValidationException extends ApiException {

    public static final String ERROR_CODE = "MEETUP_VALIDATION";

    public MeetupValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static MeetupValidationException dateMustBeFuture() {
        return new MeetupValidationException("모임 일시는 현재 시간 이후여야 합니다.");
    }
}
