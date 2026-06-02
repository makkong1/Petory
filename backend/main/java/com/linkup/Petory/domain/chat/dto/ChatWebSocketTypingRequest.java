package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** WebSocket 타이핑 상태 요청 DTO. 사용자가 입력 중인지 여부를 대화방에 브로드캐스트할 때 사용한다. */
@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketTypingRequest {
    private Long conversationIdx;
    private boolean typing;
}
