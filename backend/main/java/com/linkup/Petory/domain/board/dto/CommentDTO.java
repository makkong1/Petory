package com.linkup.Petory.domain.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import com.linkup.Petory.domain.file.dto.FileDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/** 댓글 응답/요청 DTO. 작성자·좋아요/싫어요 수·첨부파일을 포함한다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {
    private Long idx;
    @NotBlank private String content;
    private LocalDateTime createdAt;
    private String status;          // ACTIVE / BLINDED / DELETED
    private Boolean deleted;
    private LocalDateTime deletedAt;

    // 게시글 정보
    private Long boardId;

    // 작성자 정보
    private Long userId;
    private String username;
    private String userLocation;

    private String commentFilePath;
    private Integer likeCount;
    private Integer dislikeCount;
    private List<FileDTO> attachments;
}