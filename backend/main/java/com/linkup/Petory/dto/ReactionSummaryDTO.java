package com.linkup.Petory.dto;

import com.linkup.Petory.entity.ReactionType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReactionSummaryDTO {
    private int likeCount;
    private int dislikeCount;
    private ReactionType userReaction;
}

