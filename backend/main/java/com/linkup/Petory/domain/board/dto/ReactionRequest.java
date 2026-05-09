package com.linkup.Petory.domain.board.dto;

import jakarta.validation.constraints.NotNull;

import com.linkup.Petory.domain.board.entity.ReactionType;

/**
 * 좋아요/싫어요 반응 요청 DTO
 * record: 불변 데이터 캐리어. Jackson 역직렬화(@RequestBody) 지원. 필드 2개로 단순.
 */
public record ReactionRequest(
        @NotNull Long userId,
        @NotNull ReactionType reactionType) {
}
