package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 요청을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class CareRequestNotFoundException extends ApiException {

    public static final String ERROR_CODE = "CARE_REQUEST_NOT_FOUND";

    public CareRequestNotFoundException() {
        super("펫케어 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public CareRequestNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
