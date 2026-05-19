package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

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
