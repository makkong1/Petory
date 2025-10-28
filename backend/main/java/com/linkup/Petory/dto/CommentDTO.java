package com.linkup.Petory.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {
    private Long idx;
    private Long boardId;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createdAt;
}
