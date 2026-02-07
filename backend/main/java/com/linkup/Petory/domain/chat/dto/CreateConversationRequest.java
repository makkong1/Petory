package com.linkup.Petory.domain.chat.dto;

import java.util.List;

/**
 * 채팅방 생성 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record CreateConversationRequest(
        String conversationType, // DIRECT, GROUP, CARE_REQUEST, etc.
        String relatedType, // CARE_APPLICATION, MEETUP, etc.
        Long relatedIdx,
        String title, // 그룹 채팅용
        List<Long> participantUserIds) {
}
