package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.location.entity.LocationServiceReview;

/**
 * LocationServiceReview 도메인 Repository 인터페이스입니다.
 */
public interface LocationServiceReviewRepository {

    // 기본 CRUD 메서드
    LocationServiceReview save(LocationServiceReview review);

    Optional<LocationServiceReview> findById(Long id);

    void delete(LocationServiceReview review);

    void deleteById(Long id);

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
     * 특정 서비스의 리뷰 개수
     */
    Long countByServiceIdx(Long serviceIdx);

    /**
     * 특정 사용자가 특정 서비스에 리뷰를 작성했는지 확인
     */
    boolean existsByServiceIdxAndUserIdx(Long serviceIdx, Long userIdx);
}
