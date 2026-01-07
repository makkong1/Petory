package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareRequestCommentRepository extends JpaRepository<CareRequestComment, Long> {

    List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest);

    // soft-deleted 제외
    List<CareRequestComment> findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(CareRequest careRequest);

    // 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순) - JOIN FETCH로 N+1 문제 해결
    @Query("SELECT cc FROM CareRequestComment cc JOIN FETCH cc.careRequest WHERE cc.user = :user AND cc.isDeleted = false ORDER BY cc.createdAt DESC")
    List<CareRequestComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);
}

