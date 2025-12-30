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

        // 사용자별 케어 요청 조회 (최신순) - 작성자도 활성 상태여야 함
        @Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.user = :user AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
        List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(@Param("user") Users user);

        // 전체 케어 요청 조회 - 작성자도 활성 상태여야 함
        @Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
        List<CareRequest> findAllActiveRequests();

        // 상태별 케어 요청 조회 - 작성자도 활성 상태여야 함
        @Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE cr.status = :status AND cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY cr.createdAt DESC")
        List<CareRequest> findByStatusAndIsDeletedFalse(@Param("status") CareRequestStatus status);

        // 위치별 케어 요청 조회 (사용자 위치 기반)
        List<CareRequest> findByUser_LocationContaining(String location);

        // 제목이나 설명에 키워드 포함된 케어 요청 검색 - 작성자도 활성 상태여야 함
        @Query("SELECT cr FROM CareRequest cr JOIN cr.user u WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' AND " +
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

        // 단일 케어 요청 조회 (펫 정보 포함)
        @Query("SELECT cr FROM CareRequest cr LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.user WHERE cr.idx = :idx")
        java.util.Optional<CareRequest> findByIdWithPet(@Param("idx") Long idx);

        // 단일 케어 요청 조회 (펫 정보 및 지원 정보 포함)
        @Query("SELECT cr FROM CareRequest cr LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.user LEFT JOIN FETCH cr.applications WHERE cr.idx = :idx")
        java.util.Optional<CareRequest> findByIdWithApplications(@Param("idx") Long idx);

        // 통계용
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status);
}
