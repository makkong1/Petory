package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 댓글을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class CommentNotFoundException extends ApiException {

    public static final String ERROR_CODE = "COMMENT_NOT_FOUND";

    public CommentNotFoundException() {
        super("댓글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public CommentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
