package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketReadRequest {
    private Long conversationIdx;
    private Long lastMessageIdx;
}
