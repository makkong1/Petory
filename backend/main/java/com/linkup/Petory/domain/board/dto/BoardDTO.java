package com.linkup.Petory.domain.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.file.dto.FileDTO;

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
    private String status;          // ACTIVE / BLINDED / DELETED
    private LocalDateTime createdAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;

    // 작성자 정보
    private Long userId;
    private String username;
    private String userLocation;

    // 댓글 정보
    private List<CommentDTO> comments;
    private Integer commentCount;
    private List<FileDTO> attachments;

    // 통계 정보
    private Integer likes;
    private Integer dislikes;
    private Integer views;
    private LocalDateTime lastReactionAt;

    // 파일 경로 정보
    private String boardFilePath;
    private String commentFilePath;
}