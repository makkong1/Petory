package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 거래 내역을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class PetCoinTransactionNotFoundException extends ApiException {

    public static final String ERROR_CODE = "PET_COIN_TRANSACTION_NOT_FOUND";

    public PetCoinTransactionNotFoundException() {
        super("거래 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public PetCoinTransactionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
