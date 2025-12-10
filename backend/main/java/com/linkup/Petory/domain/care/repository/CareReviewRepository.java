package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareReview;

@Repository
public interface CareReviewRepository extends JpaRepository<CareReview, Long> {
    
    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(Long revieweeIdx);
    
    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(Long reviewerIdx);
    
    /**
     * 특정 CareApplication에 대한 리뷰 조회
     */
    List<CareReview> findByCareApplicationIdx(Long careApplicationIdx);
}

