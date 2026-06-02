package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** WebSocket 메시지 전송 요청 DTO. 대화방 ID·내용·메시지 유형·답장 대상을 담는다. */
@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketMessageRequest {
    private Long conversationIdx;
    private String content;
    private String messageType;
    private Long replyToMessageIdx;
}
