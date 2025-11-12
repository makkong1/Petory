package com.linkup.Petory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.entity.CareRequest;
import com.linkup.Petory.entity.CareRequestComment;

public interface CareRequestCommentRepository extends JpaRepository<CareRequestComment, Long> {

    List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest);
}

