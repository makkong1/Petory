package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.ReactionType;

import lombok.Data;

@Data
public class ReactionRequest {
    private Long userId;
    private ReactionType reactionType;
}
