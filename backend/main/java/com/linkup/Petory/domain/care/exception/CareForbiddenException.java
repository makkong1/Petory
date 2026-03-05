package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 도메인 권한 부족 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class CareForbiddenException extends ApiException {

    public static final String ERROR_CODE = "CARE_FORBIDDEN";

    public CareForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static CareForbiddenException ownRequestOnly() {
        return new CareForbiddenException("본인의 케어 요청만 수정/삭제할 수 있습니다.");
    }

    public static CareForbiddenException ownerOrApprovedProvider() {
        return new CareForbiddenException("작성자 또는 승인된 제공자만 상태를 변경할 수 있습니다.");
    }

    public static CareForbiddenException requesterOnly() {
        return new CareForbiddenException("요청자만 리뷰를 작성할 수 있습니다.");
    }

    public static CareForbiddenException providerOnly() {
        return new CareForbiddenException("제공자에게만 리뷰를 작성할 수 있습니다.");
    }

    public static CareForbiddenException commentNotAllowed() {
        return new CareForbiddenException("당신은 댓글 작성 불가입니다.");
    }

    public static CareForbiddenException petOwnerOnly() {
        return new CareForbiddenException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
    }
}
