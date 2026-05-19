package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

public class MissingPetForbiddenException extends ApiException {

    public static final String ERROR_CODE = "MISSING_PET_FORBIDDEN";

    public MissingPetForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static MissingPetForbiddenException boardOwnerOnly() {
        return new MissingPetForbiddenException("본인의 실종 제보만 수정/삭제할 수 있습니다.");
    }

    public static MissingPetForbiddenException commentOwnerOnly() {
        return new MissingPetForbiddenException("본인의 댓글만 삭제할 수 있습니다.");
    }
}
