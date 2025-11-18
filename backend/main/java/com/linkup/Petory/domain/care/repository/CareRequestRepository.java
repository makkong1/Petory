package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.user.entity.Users;

@Repository
public interface CareRequestRepository extends JpaRepository<CareRequest, Long> {

    // 사용자별 케어 요청 조회 (최신순)
    List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

    // 상태별 케어 요청 조회
    List<CareRequest> findByStatusAndIsDeletedFalse(CareRequestStatus status);

    // 위치별 케어 요청 조회 (사용자 위치 기반)
    List<CareRequest> findByUser_LocationContaining(String location);

    // 제목이나 설명에 키워드 포함된 케어 요청 검색
    @Query("SELECT cr FROM CareRequest cr WHERE cr.isDeleted = false AND " +
           "(LOWER(cr.title) LIKE LOWER(CONCAT('%', :titleKeyword, '%')) OR " +
           "LOWER(CAST(cr.description AS string)) LIKE LOWER(CONCAT('%', :descKeyword, '%')))")
    List<CareRequest> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
            @Param("titleKeyword") String titleKeyword, 
            @Param("descKeyword") String descKeyword);

    // 날짜가 지났고 특정 상태인 요청 조회 (스케줄러용)
    @Query("SELECT cr FROM CareRequest cr WHERE cr.date < :now AND cr.status IN :statuses")
    List<CareRequest> findByDateBeforeAndStatusIn(
            @Param("now") LocalDateTime now,
            @Param("statuses") List<CareRequestStatus> statuses);
}
