package com.linkup.Petory.domain.care.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.entity.CareReview;

@Component
public class CareReviewConverter {

    public CareReviewDTO toDTO(CareReview review) {
        return CareReviewDTO.builder()
                .idx(review.getIdx())
                .careApplicationId(review.getCareApplication().getIdx())
                .reviewerId(review.getReviewer().getIdx())
                .reviewerName(review.getReviewer().getUsername())
                .revieweeId(review.getReviewee().getIdx())
                .revieweeName(review.getReviewee().getUsername())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
