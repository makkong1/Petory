package com.linkup.Petory.domain.care.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 펫케어 댓글을 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class CareCommentNotFoundException extends ApiException {

    public static final String ERROR_CODE = "CARE_COMMENT_NOT_FOUND";

    public CareCommentNotFoundException() {
        super("펫케어 댓글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public CareCommentNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
