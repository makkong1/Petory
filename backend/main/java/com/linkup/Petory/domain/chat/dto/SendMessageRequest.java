package com.linkup.Petory.domain.chat.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {
    private Long conversationIdx;
    private String content;
    private String messageType;  // TEXT, IMAGE, FILE, etc.
    private Long replyToMessageIdx;  // 답장 기능
}

