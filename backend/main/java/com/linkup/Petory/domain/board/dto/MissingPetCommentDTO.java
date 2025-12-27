package com.linkup.Petory.domain.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.file.dto.FileDTO;

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
    private String nickname;
    private String content;
    private String address; // 목격 위치 주소
    private Double latitude; // 목격 위치 위도
    private Double longitude; // 목격 위치 경도
    private LocalDateTime createdAt;
    private String status; // ACTIVE / BLINDED / DELETED
    private Boolean deleted;
    private LocalDateTime deletedAt;
    private String imageUrl;
    private List<FileDTO> attachments;
}
