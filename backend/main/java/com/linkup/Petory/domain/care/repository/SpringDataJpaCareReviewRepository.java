package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.care.entity.CareReview;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareReviewRepository extends JpaRepository<CareReview, Long> {

    @RepositoryMethod("펫케어 리뷰: 피리뷰어별 목록 조회")
    List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(Long revieweeIdx);

    @RepositoryMethod("펫케어 리뷰: 리뷰어별 목록 조회")
    List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(Long reviewerIdx);

    @RepositoryMethod("펫케어 리뷰: 지원별 리뷰 조회")
    List<CareReview> findByCareApplicationIdx(Long careApplicationIdx);

    @RepositoryMethod("펫케어 리뷰: 작성 여부 확인")
    boolean existsByCareApplicationIdxAndReviewerIdx(Long careApplicationIdx, Long reviewerIdx);
}

