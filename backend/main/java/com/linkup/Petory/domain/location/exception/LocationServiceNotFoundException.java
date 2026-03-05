package com.linkup.Petory.domain.location.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 위치 서비스를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class LocationServiceNotFoundException extends ApiException {

    public static final String ERROR_CODE = "LOCATION_SERVICE_NOT_FOUND";

    public LocationServiceNotFoundException() {
        super("서비스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public LocationServiceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
