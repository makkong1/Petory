package com.linkup.Petory.domain.location.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 위치 서비스 리뷰에 대한 권한이 없을 때 (타인 리뷰 수정/삭제 등).
 * HTTP 403 Forbidden
 */
public class LocationServiceReviewForbiddenException extends ApiException {

    public static final String ERROR_CODE = "LOCATION_SERVICE_REVIEW_FORBIDDEN";

    public LocationServiceReviewForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static LocationServiceReviewForbiddenException notOwner() {
        return new LocationServiceReviewForbiddenException("본인이 작성한 리뷰만 수정하거나 삭제할 수 있습니다.");
    }
}
