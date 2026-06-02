package com.linkup.Petory.domain.activity.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 사용자 활동 내역 응답 DTO. 활동 유형(CARE_REQUEST·BOARD·COMMENT 등)·제목·생성 시각을 포함한다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityDTO {
    private Long idx;
    private String type; // CARE_REQUEST, BOARD, MISSING_PET, CARE_COMMENT, COMMENT, MISSING_COMMENT, LOCATION_REVIEW
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private String status;
    private Boolean deleted;
    private LocalDateTime deletedAt;
    
    // 관련 정보
    private Long relatedId; // 게시글 ID 또는 서비스 ID 등
    private String relatedTitle; // 관련 게시글/서비스 제목
}

