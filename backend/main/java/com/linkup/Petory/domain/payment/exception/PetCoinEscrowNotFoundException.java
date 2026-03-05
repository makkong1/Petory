package com.linkup.Petory.domain.payment.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 에스크로를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class PetCoinEscrowNotFoundException extends ApiException {

    public static final String ERROR_CODE = "PET_COIN_ESCROW_NOT_FOUND";

    public PetCoinEscrowNotFoundException() {
        super("에스크로를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public PetCoinEscrowNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
