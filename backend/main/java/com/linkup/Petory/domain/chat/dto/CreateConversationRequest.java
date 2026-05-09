package com.linkup.Petory.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 채팅방 생성 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 */
public record CreateConversationRequest(
        @NotBlank String conversationType,
        String relatedType,
        Long relatedIdx,
        String title,
        @NotEmpty List<Long> participantUserIds) {
}
