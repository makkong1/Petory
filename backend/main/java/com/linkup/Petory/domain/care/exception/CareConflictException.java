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

    public static CareConflictException offeredAmountChanged(Integer currentAmount) {
        return new CareConflictException(
                "제안 이후 제시 금액이 변경되었습니다. 현재 금액: " + currentAmount);
    }

    public static CareConflictException requestNotOpen(Object currentStatus) {
        return new CareConflictException(
                "이미 다른 제공자와 거래가 확정된 요청입니다. 현재 상태: " + currentStatus);
    }

    public static CareConflictException offerNotPending(Object currentStatus) {
        return new CareConflictException(
                "대기 중인 제안이 아닙니다. 현재 상태: " + currentStatus);
    }

    public static CareConflictException providerMustConfirmFirst() {
        return new CareConflictException(
                "제공자가 이행 완료를 알린 뒤에 확인할 수 있습니다.");
    }

    public static CareConflictException nothingToCancel() {
        return new CareConflictException("되돌릴 이행 완료 확인이 없습니다.");
    }
}
