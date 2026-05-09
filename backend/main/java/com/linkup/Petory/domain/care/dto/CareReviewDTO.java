package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareReviewDTO {
    private Long idx;
    @NotNull @Min(1) @Max(5) private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 케어 지원 정보
    @NotNull private Long careApplicationId;

    // 리뷰 작성자 정보
    private Long reviewerId;
    private String reviewerName;

    // 리뷰 대상자 정보
    private Long revieweeId;
    private String revieweeName;
}