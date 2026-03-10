package com.linkup.Petory.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 채팅 도메인 입력 검증 실패 시 발생하는 예외.
 * HTTP 400 Bad Request
 */
public class ChatValidationException extends ApiException {

    public static final String ERROR_CODE = "CHAT_VALIDATION";

    public ChatValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static ChatValidationException invalidConversation() {
        return new ChatValidationException("유효하지 않은 채팅방입니다.");
    }

    public static ChatValidationException minParticipantsRequired(int min) {
        return new ChatValidationException("최소 " + min + "명의 참여자가 필요합니다.");
    }

    public static ChatValidationException ownReportCannotChat() {
        return new ChatValidationException("본인의 제보에는 채팅을 시작할 수 없습니다.");
    }

    public static ChatValidationException notCareConversation() {
        return new ChatValidationException("펫케어 관련 채팅방이 아닙니다.");
    }
}
