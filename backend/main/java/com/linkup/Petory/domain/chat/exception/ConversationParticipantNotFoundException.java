package com.linkup.Petory.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 채팅방 참여자 정보를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class ConversationParticipantNotFoundException extends ApiException {

    public static final String ERROR_CODE = "CONVERSATION_PARTICIPANT_NOT_FOUND";

    public ConversationParticipantNotFoundException() {
        super("참여자 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public ConversationParticipantNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
