package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareRequestCommentRepository extends JpaRepository<CareRequestComment, Long> {

    @RepositoryMethod("펫케어 댓글: 요청별 목록 조회")
    List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest);

    @RepositoryMethod("펫케어 댓글: 요청별 목록 (삭제 제외)")
    @Query("SELECT cc FROM CareRequestComment cc JOIN FETCH cc.user WHERE cc.careRequest = :careRequest AND cc.isDeleted = false ORDER BY cc.createdAt ASC")
    List<CareRequestComment> findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(@Param("careRequest") CareRequest careRequest);

    @RepositoryMethod("펫케어 댓글: 사용자별 목록 조회")
    @Query("SELECT cc FROM CareRequestComment cc JOIN FETCH cc.careRequest WHERE cc.user = :user AND cc.isDeleted = false ORDER BY cc.createdAt DESC")
    List<CareRequestComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);
}

