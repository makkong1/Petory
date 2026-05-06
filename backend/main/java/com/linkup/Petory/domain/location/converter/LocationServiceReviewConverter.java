package com.linkup.Petory.domain.location.converter;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.domain.user.entity.Users;

import org.springframework.stereotype.Component;

@Component
public class LocationServiceReviewConverter {

    public LocationServiceReviewDTO toDTO(LocationServiceReview review) {
        if (review == null)
            return null;

        return LocationServiceReviewDTO.builder()
                .idx(review.getIdx())
                .serviceIdx(review.getService() != null ? review.getService().getIdx() : null)
                .serviceName(review.getService() != null ? review.getService().getName() : null)
                .userIdx(review.getUser() != null ? review.getUser().getIdx() : null)
                .username(review.getUser() != null ? review.getUser().getUsername() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .isDeleted(review.getIsDeleted())
                .deletedAt(review.getDeletedAt())
                .build();
    }

    /**
     * DTO를 Entity로 변환 (service와 user 엔티티 필요)
     * 
     * @param dto     변환할 DTO
     * @param service LocationService 엔티티 (필수)
     * @param user    Users 엔티티 (필수)
     * @return LocationServiceReview 엔티티
     */
    public LocationServiceReview toEntity(LocationServiceReviewDTO dto, LocationService service, Users user) {
        if (dto == null)
            return null;

        return LocationServiceReview.builder()
                .idx(dto.getIdx())
                .service(service)
                .user(user)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .isDeleted(dto.getIsDeleted() != null ? dto.getIsDeleted() : false)
                .deletedAt(dto.getDeletedAt())
                .build();
    }

    /**
     * DTO를 Entity로 변환 (service와 user 없이 - 불완전한 엔티티)
     * 주의: 이 메서드는 service와 user가 설정되지 않은 불완전한 엔티티를 반환합니다.
     * 일반적으로는 toEntity(dto, service, user)를 사용하세요.
     * 
     * @param dto 변환할 DTO
     * @return LocationServiceReview 엔티티 (service와 user가 null)
     */
    @Deprecated
    public LocationServiceReview toEntity(LocationServiceReviewDTO dto) {
        if (dto == null)
            return null;

        return LocationServiceReview.builder()
                .idx(dto.getIdx())
                .rating(dto.getRating())
                .comment(dto.getComment())
                .isDeleted(dto.getIsDeleted() != null ? dto.getIsDeleted() : false)
                .deletedAt(dto.getDeletedAt())
                .build();
    }
}
