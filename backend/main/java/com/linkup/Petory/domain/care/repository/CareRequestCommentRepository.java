package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * CareRequestComment 도메인 Repository 인터페이스입니다.
 */
public interface CareRequestCommentRepository {

    CareRequestComment save(CareRequestComment comment);

    Optional<CareRequestComment> findById(Long id);

    void delete(CareRequestComment comment);

    void deleteById(Long id);

    List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest);

    /**
     * soft-deleted 제외
     */
    List<CareRequestComment> findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(CareRequest careRequest);

    /**
     * 사용자별 댓글 조회 (삭제되지 않은 것만, 최신순)
     */
    List<CareRequestComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);
}
