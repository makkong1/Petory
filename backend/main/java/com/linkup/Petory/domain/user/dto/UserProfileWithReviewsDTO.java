package com.linkup.Petory.domain.user.dto;

import java.util.List;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupHistoryDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/** 사용자 프로필 + 케어 리뷰 + 위치 서비스 리뷰 + 모임 이력 통합 응답 DTO. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileWithReviewsDTO {
    private UsersDTO user;
    private List<CareReviewDTO> reviews;
    private String careReviewMode;
    private List<LocationServiceReviewDTO> locationServiceReviews;
    private Double averageRating;
    private Double locationServiceAverageRating;
    private Integer reviewCount;
    private Integer completedCareCount;
    private Integer locationServiceReviewCount;
    private List<MeetupHistoryDTO> meetupHistories;
    private Integer meetupHistoryCount;
    private Integer meetupLikedCount;
}
