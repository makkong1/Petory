package com.linkup.Petory.domain.location.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이미 삭제된 위치 서비스에 접근할 때 발생하는 예외.
 * HTTP 409 Conflict
 */
public class LocationServiceAlreadyDeletedException extends ApiException {

    public static final String ERROR_CODE = "LOCATION_SERVICE_ALREADY_DELETED";

    public LocationServiceAlreadyDeletedException() {
        super("이미 삭제된 서비스입니다.", HttpStatus.CONFLICT, ERROR_CODE);
    }

    public LocationServiceAlreadyDeletedException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }
}
