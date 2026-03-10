package com.linkup.Petory.domain.location.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 이미 해당 서비스에 리뷰를 작성했을 때 발생하는 예외.
 * HTTP 409 Conflict
 */
public class LocationReviewDuplicateException extends ApiException {

    public static final String ERROR_CODE = "LOCATION_REVIEW_DUPLICATE";

    public LocationReviewDuplicateException() {
        super("이미 해당 서비스에 리뷰를 작성하셨습니다.", HttpStatus.CONFLICT, ERROR_CODE);
    }

    public LocationReviewDuplicateException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }
}
