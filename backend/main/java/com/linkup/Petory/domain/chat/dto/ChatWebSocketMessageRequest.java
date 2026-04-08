package com.linkup.Petory.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatWebSocketMessageRequest {
    private Long conversationIdx;
    private String content;
    private String messageType;
    private Long replyToMessageIdx;
}
