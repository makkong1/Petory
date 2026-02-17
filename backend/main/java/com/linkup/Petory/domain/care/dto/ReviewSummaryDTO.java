package com.linkup.Petory.domain.care.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 리뷰 목록 + 평균 평점 + 개수를 한 번의 쿼리로 조회한 결과
 * getReviewsByReviewee + getAverageRating 중복 쿼리 방지용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryDTO {
    private List<CareReviewDTO> reviews;
    private Double averageRating;
    private Integer reviewCount;
}
