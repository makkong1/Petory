package com.linkup.Petory.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPetCommentDTO {
    private Long idx;
    private Long boardId;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createdAt;
}

