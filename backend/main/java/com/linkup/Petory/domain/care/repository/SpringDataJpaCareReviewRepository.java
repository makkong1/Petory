package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareReview;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareReviewRepository extends JpaRepository<CareReview, Long> {

    @RepositoryMethod("펫케어 리뷰: 피리뷰어별 목록 조회")
    @Query("SELECT r FROM CareReview r " +
            "JOIN FETCH r.careApplication " +
            "JOIN FETCH r.reviewer " +
            "JOIN FETCH r.reviewee " +
            "WHERE r.reviewee.idx = :revieweeIdx " +
            "ORDER BY r.createdAt DESC")
    List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(@Param("revieweeIdx") Long revieweeIdx);

    @RepositoryMethod("펫케어 리뷰: 리뷰어별 목록 조회")
    @Query("SELECT r FROM CareReview r " +
            "JOIN FETCH r.careApplication " +
            "JOIN FETCH r.reviewer " +
            "JOIN FETCH r.reviewee " +
            "WHERE r.reviewer.idx = :reviewerIdx " +
            "ORDER BY r.createdAt DESC")
    List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(@Param("reviewerIdx") Long reviewerIdx);

    @RepositoryMethod("펫케어 리뷰: 작성 여부 확인")
    boolean existsByCareApplicationIdxAndReviewerIdx(Long careApplicationIdx, Long reviewerIdx);
}
