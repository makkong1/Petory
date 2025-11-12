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
public class CareRequestDTO {
    private Long idx;
    private String title;
    private String description;
    private LocalDateTime date;
    private String status; // OPEN, IN_PROGRESS, COMPLETED, CANCELLED
    private LocalDateTime createdAt;

    // 요청자 정보
    private Long userId;
    private String username;
    private String userLocation;

    // 지원자 정보
    private List<CareApplicationDTO> applications;
    private Integer applicationCount;

    // 댓글 정보
    private List<CareRequestCommentDTO> comments;
    private Integer commentCount;
}