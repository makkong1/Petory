package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 반려동물을 찾을 수 없거나 삭제된 경우 발생하는 예외.
 * HTTP 404 Not Found
 */
public class PetNotFoundException extends ApiException {

    public static final String ERROR_CODE = "PET_NOT_FOUND";

    public PetNotFoundException() {
        super("반려동물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public PetNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
