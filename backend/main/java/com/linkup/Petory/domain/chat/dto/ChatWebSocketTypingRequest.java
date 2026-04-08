package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketTypingRequest {
    private Long conversationIdx;
    private boolean typing;
}
