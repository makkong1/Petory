package com.linkup.Petory.repository;

import com.linkup.Petory.entity.LocationServiceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationServiceReviewRepository extends JpaRepository<LocationServiceReview, Long> {

    // 특정 서비스의 모든 리뷰 조회
    List<LocationServiceReview> findByServiceIdxOrderByCreatedAtDesc(Long serviceIdx);

    // 특정 사용자의 리뷰 조회
    List<LocationServiceReview> findByUserIdxOrderByCreatedAtDesc(Long userIdx);

    // 특정 서비스의 평균 평점 계산
    @Query("SELECT AVG(r.rating) FROM LocationServiceReview r WHERE r.service.idx = :serviceIdx")
    Optional<Double> findAverageRatingByServiceIdx(@Param("serviceIdx") Long serviceIdx);

    // 특정 서비스의 리뷰 개수
    Long countByServiceIdx(Long serviceIdx);

    // 특정 사용자가 특정 서비스에 리뷰를 작성했는지 확인
    boolean existsByServiceIdxAndUserIdx(Long serviceIdx, Long userIdx);
}
