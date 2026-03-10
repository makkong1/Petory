package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 도메인 충돌(중복, 상태 불일치) 시 발생하는 예외.
 * HTTP 409 Conflict
 */
public class CareConflictException extends ApiException {

    public static final String ERROR_CODE = "CARE_CONFLICT";

    public CareConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }

    public static CareConflictException approvedServiceOnly() {
        return new CareConflictException("승인된 펫케어 서비스에만 리뷰를 작성할 수 있습니다.");
    }

    public static CareConflictException alreadyReviewed() {
        return new CareConflictException("이미 해당 서비스에 리뷰를 작성하셨습니다.");
    }
}
