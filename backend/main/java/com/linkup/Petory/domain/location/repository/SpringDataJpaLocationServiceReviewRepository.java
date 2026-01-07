package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.location.entity.LocationServiceReview;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaLocationServiceReviewRepository extends JpaRepository<LocationServiceReview, Long> {

    // 특정 서비스의 모든 리뷰 조회
    @Query("SELECT r FROM LocationServiceReview r WHERE " +
            "r.service.idx = :serviceIdx AND " +
            "(r.isDeleted IS NULL OR r.isDeleted = false) " +
            "ORDER BY r.createdAt DESC")
    List<LocationServiceReview> findByServiceIdxOrderByCreatedAtDesc(@Param("serviceIdx") Long serviceIdx);

    // 특정 사용자의 리뷰 조회
    @Query("SELECT r FROM LocationServiceReview r WHERE " +
            "r.user.idx = :userIdx AND " +
            "(r.isDeleted IS NULL OR r.isDeleted = false) " +
            "ORDER BY r.createdAt DESC")
    List<LocationServiceReview> findByUserIdxOrderByCreatedAtDesc(@Param("userIdx") Long userIdx);

    // 특정 서비스의 평균 평점 계산
    @Query("SELECT AVG(r.rating) FROM LocationServiceReview r WHERE " +
            "r.service.idx = :serviceIdx AND " +
            "(r.isDeleted IS NULL OR r.isDeleted = false)")
    Optional<Double> findAverageRatingByServiceIdx(@Param("serviceIdx") Long serviceIdx);

    // 특정 서비스의 리뷰 개수
    @Query("SELECT COUNT(r) FROM LocationServiceReview r WHERE " +
            "r.service.idx = :serviceIdx AND " +
            "(r.isDeleted IS NULL OR r.isDeleted = false)")
    Long countByServiceIdx(@Param("serviceIdx") Long serviceIdx);

    // 특정 사용자가 특정 서비스에 리뷰를 작성했는지 확인
    @Query("SELECT COUNT(r) > 0 FROM LocationServiceReview r WHERE " +
            "r.service.idx = :serviceIdx AND " +
            "r.user.idx = :userIdx AND " +
            "(r.isDeleted IS NULL OR r.isDeleted = false)")
    boolean existsByServiceIdxAndUserIdx(@Param("serviceIdx") Long serviceIdx, @Param("userIdx") Long userIdx);
}

