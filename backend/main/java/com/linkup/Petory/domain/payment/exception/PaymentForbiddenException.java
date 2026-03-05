package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 결제 도메인 권한 부족 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class PaymentForbiddenException extends ApiException {

    public static final String ERROR_CODE = "PAYMENT_FORBIDDEN";

    public PaymentForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static PaymentForbiddenException ownTransactionOnly() {
        return new PaymentForbiddenException("본인의 거래 내역만 조회할 수 있습니다.");
    }
}
