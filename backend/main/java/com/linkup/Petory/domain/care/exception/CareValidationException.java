package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class CareValidationException extends ApiException {

    public static final String ERROR_CODE = "CARE_VALIDATION";

    public CareValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static CareValidationException careApplicationIdRequired() {
        return new CareValidationException("CareApplication ID가 필요합니다.");
    }

    public static CareValidationException coinMustBePositive() {
        return new CareValidationException("제공 코인은 0보다 커야 합니다.");
    }

    public static CareValidationException insufficientBalance() {
        return new CareValidationException("펫코인 잔액이 부족합니다.");
    }

    public static CareValidationException invalidUserId() {
        return new CareValidationException("유효하지 않은 사용자 ID입니다.");
    }
}
