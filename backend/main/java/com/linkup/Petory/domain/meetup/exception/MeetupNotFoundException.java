package com.linkup.Petory.domain.meetup.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 모임을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class MeetupNotFoundException extends ApiException {

    public static final String ERROR_CODE = "MEETUP_NOT_FOUND";

    public MeetupNotFoundException() {
        super("모임을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public MeetupNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
