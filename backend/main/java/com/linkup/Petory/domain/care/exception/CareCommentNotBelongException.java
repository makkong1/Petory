package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 댓글이 해당 펫케어 요청에 속하지 않을 때 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class CareCommentNotBelongException extends ApiException {

    public static final String ERROR_CODE = "CARE_COMMENT_NOT_BELONG";

    public CareCommentNotBelongException() {
        super("해당 펫케어 요청에 속한 댓글이 아닙니다.", HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public CareCommentNotBelongException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }
}
