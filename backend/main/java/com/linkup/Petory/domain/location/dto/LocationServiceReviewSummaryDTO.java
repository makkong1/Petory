package com.linkup.Petory.domain.location.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 특정 사용자가 작성한 장소 리뷰 목록 + 평균 평점 + 개수 요약
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceReviewSummaryDTO {
    private List<LocationServiceReviewDTO> reviews;
    private Double averageRating;
    private Integer reviewCount;
}
