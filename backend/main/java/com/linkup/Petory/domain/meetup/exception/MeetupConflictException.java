package com.linkup.Petory.domain.meetup.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 모임 도메인 충돌(중복, 상태 불일치) 시 발생하는 예외.
 * HTTP 409 Conflict
 */
public class MeetupConflictException extends ApiException {

    public static final String ERROR_CODE = "MEETUP_CONFLICT";

    public MeetupConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }

    public static MeetupConflictException alreadyJoined() {
        return new MeetupConflictException("이미 참가한 모임입니다.");
    }

    public static MeetupConflictException fullCapacity() {
        return new MeetupConflictException("모임 인원이 가득 찼습니다.");
    }
}
