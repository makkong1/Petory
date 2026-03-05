package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 코인 지급/환불 처리 중 오류 시 발생하는 예외.
 * HTTP 500 Internal Server Error
 */
public class CarePaymentException extends ApiException {

    public static final String ERROR_CODE = "CARE_PAYMENT_ERROR";

    public CarePaymentException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ERROR_CODE);
        initCause(cause);
    }

    public static CarePaymentException paymentFailed(Throwable cause) {
        return new CarePaymentException("코인 지급 처리 중 오류가 발생했습니다.", cause);
    }

    public static CarePaymentException refundFailed(Throwable cause) {
        return new CarePaymentException("환불 처리 중 오류가 발생했습니다.", cause);
    }
}
