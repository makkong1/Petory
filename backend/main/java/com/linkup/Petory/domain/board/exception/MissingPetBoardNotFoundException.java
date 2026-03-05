package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 실종 제보 게시글을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class MissingPetBoardNotFoundException extends ApiException {

    public static final String ERROR_CODE = "MISSING_PET_BOARD_NOT_FOUND";

    public MissingPetBoardNotFoundException() {
        super("실종 제보 게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public MissingPetBoardNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
