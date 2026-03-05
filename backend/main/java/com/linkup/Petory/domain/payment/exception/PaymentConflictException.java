package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 결제 도메인 충돌(중복, 상태 불일치) 시 발생하는 예외.
 * HTTP 409 Conflict
 */
public class PaymentConflictException extends ApiException {

    public static final String ERROR_CODE = "PAYMENT_CONFLICT";

    public PaymentConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }

    public static PaymentConflictException escrowAlreadyExists() {
        return new PaymentConflictException("이미 에스크로가 생성되어 있습니다.");
    }

    public static PaymentConflictException holdStatusRequiredForRelease() {
        return new PaymentConflictException("HOLD 상태의 에스크로만 지급할 수 있습니다.");
    }

    public static PaymentConflictException holdStatusRequiredForRefund() {
        return new PaymentConflictException("HOLD 상태의 에스크로만 환불할 수 있습니다.");
    }
}
