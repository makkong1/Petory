package com.linkup.Petory.domain.location.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이미 삭제된 리뷰에 접근할 때 발생하는 예외.
 * HTTP 409 Conflict
 */
public class LocationReviewAlreadyDeletedException extends ApiException {

    public static final String ERROR_CODE = "LOCATION_REVIEW_ALREADY_DELETED";

    public LocationReviewAlreadyDeletedException() {
        super("이미 삭제된 리뷰입니다.", HttpStatus.CONFLICT, ERROR_CODE);
    }

    public LocationReviewAlreadyDeletedException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }
}
