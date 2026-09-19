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
            "JOIN FETCH r.reviewer rv " +
            "JOIN FETCH r.reviewee " +
            "WHERE r.reviewee.idx = :revieweeIdx " +
            "AND rv.isDeleted = false AND rv.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED " +
            "ORDER BY r.createdAt DESC")
    List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(@Param("revieweeIdx") Long revieweeIdx);

    @RepositoryMethod("펫케어 리뷰: 리뷰어별 목록 조회")
    @Query("SELECT r FROM CareReview r " +
            "JOIN FETCH r.careApplication " +
            "JOIN FETCH r.reviewer rv " +
            "JOIN FETCH r.reviewee " +
            "WHERE r.reviewer.idx = :reviewerIdx " +
            "AND rv.isDeleted = false AND rv.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED " +
            "ORDER BY r.createdAt DESC")
    List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(@Param("reviewerIdx") Long reviewerIdx);

    @RepositoryMethod("펫케어 리뷰: 작성 여부 확인")
    boolean existsByCareApplicationIdxAndReviewerIdx(Long careApplicationIdx, Long reviewerIdx);

    /**
     * 여러 제공자의 평점·리뷰수를 한 번에. 목록 화면에서 1인당 한 번씩 부르면 N+1 이 된다.
     * 반환 각 행: [revieweeIdx(Long), avgRating(Double), reviewCount(Long)]
     */
    @Query("""
            SELECT r.reviewee.idx, AVG(r.rating), COUNT(r)
            FROM CareReview r
            WHERE r.reviewee.idx IN :revieweeIdxs
            GROUP BY r.reviewee.idx
            """)
    List<Object[]> aggregateByRevieweeIdxs(@Param("revieweeIdxs") List<Long> revieweeIdxs);
}
