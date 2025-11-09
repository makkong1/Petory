package com.linkup.Petory.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardDTO {
    private Long idx;
    private String title;
    private String content;
    private String category;
    private LocalDateTime createdAt;

    // 작성자 정보
    private Long userId;
    private String username;
    private String userLocation;

    // 댓글 정보
    private List<CommentDTO> comments;
    private Integer commentCount;

    // 통계 정보 (추후 확장용)
    private Integer likes;
    private Integer dislikes;
    private Integer views;

    // 파일 경로 정보
    private String boardFilePath;
    private String commentFilePath;
}