package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareReview;

/**
 * CareReview 도메인 Repository 인터페이스입니다.
 */
public interface CareReviewRepository {

    CareReview save(CareReview careReview);

    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(Long revieweeIdx);

    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(Long reviewerIdx);

    /**
     * 특정 CareApplication과 Reviewer로 리뷰 작성 여부 확인
     */
    boolean existsByCareApplicationIdxAndReviewerIdx(Long careApplicationIdx, Long reviewerIdx);

    /**
     * 리뷰 존재 여부 확인 (신고 대상 유효성 검증용)
     */
    boolean existsById(Long idx);

    /**
     * 리뷰 단건 조회 (신고 상세 미리보기용)
     */
    Optional<CareReview> findById(Long idx);
}
