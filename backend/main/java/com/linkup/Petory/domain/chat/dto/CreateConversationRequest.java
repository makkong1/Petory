package com.linkup.Petory.domain.chat.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConversationRequest {
    private String conversationType;  // DIRECT, GROUP, CARE_REQUEST, etc.
    private String relatedType;      // CARE_APPLICATION, MEETUP, etc.
    private Long relatedIdx;
    private String title;            // 그룹 채팅용
    private List<Long> participantUserIds;
}

