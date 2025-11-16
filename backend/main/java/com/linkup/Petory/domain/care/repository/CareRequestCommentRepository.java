package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;

public interface CareRequestCommentRepository extends JpaRepository<CareRequestComment, Long> {

    List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest);

    // soft-deleted 제외
    List<CareRequestComment> findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(CareRequest careRequest);
}
