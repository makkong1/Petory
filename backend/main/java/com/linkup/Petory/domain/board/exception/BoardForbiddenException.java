package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/** 게시글/댓글에 대한 권한 없음 예외. 본인 소유가 아닌 콘텐츠 수정·삭제 시 발생한다. */
public class BoardForbiddenException extends ApiException {

    public static final String ERROR_CODE = "BOARD_FORBIDDEN";

    public BoardForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static BoardForbiddenException boardOwnerOnly() {
        return new BoardForbiddenException("본인의 게시글만 수정/삭제할 수 있습니다.");
    }

    public static BoardForbiddenException commentOwnerOnly() {
        return new BoardForbiddenException("본인의 댓글만 수정/삭제할 수 있습니다.");
    }
}
