package com.linkup.Petory.domain.location.converter;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;

import org.springframework.stereotype.Component;

@Component
public class LocationServiceReviewConverter {

    public LocationServiceReviewDTO toDTO(LocationServiceReview review) {
        if (review == null)
            return null;

        return LocationServiceReviewDTO.builder()
                .idx(review.getIdx())
                .serviceIdx(review.getService().getIdx())
                .userIdx(review.getUser().getIdx())
                .username(review.getUser().getUsername())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    public LocationServiceReview toEntity(LocationServiceReviewDTO dto) {
        if (dto == null)
            return null;

        return LocationServiceReview.builder()
                .idx(dto.getIdx())
                .rating(dto.getRating())
                .comment(dto.getComment())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
