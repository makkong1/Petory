package com.linkup.Petory.domain.meetup.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 모임 참가 정보를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class MeetupParticipantNotFoundException extends ApiException {

    public static final String ERROR_CODE = "MEETUP_PARTICIPANT_NOT_FOUND";

    public MeetupParticipantNotFoundException() {
        super("참가 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public MeetupParticipantNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
