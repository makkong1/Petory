package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 결제 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class PaymentValidationException extends ApiException {

    public static final String ERROR_CODE = "PAYMENT_VALIDATION";

    public PaymentValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static PaymentValidationException userIdRequired() {
        return new PaymentValidationException("사용자 ID가 필요합니다.");
    }

    public static PaymentValidationException chargeAmountInvalid() {
        return new PaymentValidationException("충전 금액은 0보다 커야 합니다.");
    }

    public static PaymentValidationException deductAmountInvalid() {
        return new PaymentValidationException("차감 금액은 0보다 커야 합니다.");
    }

    public static PaymentValidationException payoutAmountInvalid() {
        return new PaymentValidationException("지급 금액은 0보다 커야 합니다.");
    }

    public static PaymentValidationException refundAmountInvalid() {
        return new PaymentValidationException("환불 금액은 0보다 커야 합니다.");
    }

    public static PaymentValidationException escrowAmountInvalid() {
        return new PaymentValidationException("에스크로 금액은 0보다 커야 합니다.");
    }
}
