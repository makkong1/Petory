package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫코인 잔액 부족 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class InsufficientBalanceException extends ApiException {

    public static final String ERROR_CODE = "INSUFFICIENT_BALANCE";

    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static InsufficientBalanceException of(int current, int required) {
        return new InsufficientBalanceException(
                String.format("잔액이 부족합니다. 현재 잔액: %d, 필요 금액: %d", current, required));
    }
}
