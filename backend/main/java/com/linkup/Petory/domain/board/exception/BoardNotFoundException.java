package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 게시글을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class BoardNotFoundException extends ApiException {

    public static final String ERROR_CODE = "BOARD_NOT_FOUND";

    public BoardNotFoundException() {
        super("게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public BoardNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
