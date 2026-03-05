package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 댓글이 해당 게시글에 속하지 않을 때 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class CommentNotBelongToBoardException extends ApiException {

    public static final String ERROR_CODE = "COMMENT_NOT_BELONG_TO_BOARD";

    public CommentNotBelongToBoardException() {
        super("해당 게시글에 속한 댓글이 아닙니다.", HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public CommentNotBelongToBoardException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }
}
