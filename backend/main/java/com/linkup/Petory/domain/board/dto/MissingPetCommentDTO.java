package com.linkup.Petory.domain.board.dto;

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
    private String address; // 목격 위치 주소
    private Double latitude; // 목격 위치 위도
    private Double longitude; // 목격 위치 경도
    private LocalDateTime createdAt;
}
