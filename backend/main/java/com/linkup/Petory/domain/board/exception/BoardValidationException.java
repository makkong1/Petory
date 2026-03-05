package com.linkup.Petory.domain.board.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * Board 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class BoardValidationException extends ApiException {

    public static final String ERROR_CODE = "BOARD_VALIDATION";

    public BoardValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    /** userId와 reactionType 필수 */
    public static BoardValidationException reactionParamsRequired() {
        return new BoardValidationException("userId와 reactionType은 필수입니다.");
    }

    /** status 필수 */
    public static BoardValidationException statusRequired() {
        return new BoardValidationException("status는 필수입니다.");
    }

    /** 유효하지 않은 실종 제보 상태 */
    public static BoardValidationException invalidStatus(String validValues) {
        return new BoardValidationException(
                "유효하지 않은 상태입니다. " + validValues + " 중 하나를 선택해주세요.");
    }
}
