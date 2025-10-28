package com.linkup.Petory.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardDTO {
    private Long idx;
    private Long userId;
    private String username;
    private String title;
    private String content;
    private String category;
    private LocalDateTime createdAt;
    private List<CommentDTO> comments;
}
