package com.linkup.Petory.dto;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {
    private Long idx;
    private String content;
    private LocalDateTime createdAt;

    // 게시글 정보
    private Long boardId;

    // 작성자 정보
    private Long userId;
    private String username;
    private String userLocation;

    private String commentFilePath;
    private Integer likeCount;
    private Integer dislikeCount;
}