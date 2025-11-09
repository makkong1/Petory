package com.linkup.Petory.dto;

import com.linkup.Petory.entity.ReactionType;

import lombok.Data;

@Data
public class ReactionRequest {
    private Long userId;
    private ReactionType reactionType;
}

