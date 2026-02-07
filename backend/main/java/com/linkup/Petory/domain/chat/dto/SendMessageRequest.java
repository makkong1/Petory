package com.linkup.Petory.domain.chat.dto;

/**
 * 메시지 전송 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record SendMessageRequest(
        Long conversationIdx,
        String content,
        String messageType, // TEXT, IMAGE, FILE, etc.
        Long replyToMessageIdx // 답장 기능
) {
}
