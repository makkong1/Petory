package com.linkup.Petory.domain.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReadStatusDTO {
    private Long idx;
    private Long messageIdx;
    private Long userIdx;
    private String username;
    private LocalDateTime readAt;
}

