package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.location.entity.LocationServiceReview;

/**
 * LocationServiceReview 도메인 Repository 인터페이스입니다.
 */
public interface LocationServiceReviewRepository {

    LocationServiceReview save(LocationServiceReview review);

    /**
     * 단건 조회 (user, service 포함) - 수정/삭제 시 권한 확인용
     */
    Optional<LocationServiceReview> findByIdWithUserAndService(Long idx);

    /**
     * 특정 서비스의 모든 리뷰 조회
     */
    List<LocationServiceReview> findByServiceIdxOrderByCreatedAtDesc(Long serviceIdx);

    /**
     * 특정 사용자의 리뷰 조회
     */
    List<LocationServiceReview> findByUserIdxOrderByCreatedAtDesc(Long userIdx);

    /**
     * 특정 서비스의 평균 평점 계산
     */
    Optional<Double> findAverageRatingByServiceIdx(Long serviceIdx);

    /**
     * 특정 사용자가 특정 서비스에 리뷰를 작성했는지 확인
     */
    boolean existsByServiceIdxAndUserIdx(Long serviceIdx, Long userIdx);
}
