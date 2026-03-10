package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 지원을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class CareApplicationNotFoundException extends ApiException {

    public static final String ERROR_CODE = "CARE_APPLICATION_NOT_FOUND";

    public CareApplicationNotFoundException() {
        super("펫케어 지원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public CareApplicationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
