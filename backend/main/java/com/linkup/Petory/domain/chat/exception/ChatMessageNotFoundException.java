package com.linkup.Petory.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 채팅 메시지를 찾을 수 없을 때 발생하는 예외.
 * HTTP 404 Not Found
 */
public class ChatMessageNotFoundException extends ApiException {

    public static final String ERROR_CODE = "CHAT_MESSAGE_NOT_FOUND";

    public ChatMessageNotFoundException() {
        super("메시지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND, ERROR_CODE);
    }

    public ChatMessageNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
