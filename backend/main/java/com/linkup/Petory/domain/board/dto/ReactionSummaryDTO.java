package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.ReactionType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReactionSummaryDTO {
    private int likeCount;
    private int dislikeCount;
    private ReactionType userReaction;
}
