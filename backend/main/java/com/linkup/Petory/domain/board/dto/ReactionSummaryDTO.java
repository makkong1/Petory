package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.ReactionType;

/**
 * 좋아요/싫어요 요약 응답 DTO
 * record: 불변 데이터 캐리어. Jackson 직렬화, 반응 카운트 및 사용자 반응 상태 전달용.
 */
public record ReactionSummaryDTO(
                int likeCount,
                int dislikeCount,
                ReactionType userReaction) {
}
