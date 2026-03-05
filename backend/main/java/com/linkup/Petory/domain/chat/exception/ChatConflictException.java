package com.linkup.Petory.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 채팅 도메인 충돌(중복, 상태 불일치) 시 발생하는 예외.
 * HTTP 409 Conflict
 */
public class ChatConflictException extends ApiException {

    public static final String ERROR_CODE = "CHAT_CONFLICT";

    public ChatConflictException(String message) {
        super(message, HttpStatus.CONFLICT, ERROR_CODE);
    }

    public static ChatConflictException dealAlreadyConfirmed() {
        return new ChatConflictException("이미 거래 확정을 완료했습니다.");
    }
}
