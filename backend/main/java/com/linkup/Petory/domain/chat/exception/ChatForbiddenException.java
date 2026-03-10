package com.linkup.Petory.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 채팅 도메인 권한 부족 시 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class ChatForbiddenException extends ApiException {

    public static final String ERROR_CODE = "CHAT_FORBIDDEN";

    public ChatForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }

    public static ChatForbiddenException notParticipant() {
        return new ChatForbiddenException("채팅방 참여자가 아닙니다.");
    }

    public static ChatForbiddenException notActiveParticipant() {
        return new ChatForbiddenException("채팅방에 참여 중이 아닙니다.");
    }

    public static ChatForbiddenException ownMessageOnly() {
        return new ChatForbiddenException("본인 메시지만 삭제할 수 있습니다.");
    }

    public static ChatForbiddenException deletedUserCannotSend() {
        return new ChatForbiddenException("탈퇴한 사용자는 메시지를 보낼 수 없습니다.");
    }
}
