package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** WebSocket 읽음 처리 요청 DTO. 어느 대화방의 어디까지 읽었는지를 서버에 전달한다. */
@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketReadRequest {
    private Long conversationIdx;
    private Long lastMessageIdx;
}
