package com.linkup.Petory.domain.report.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaReportRepository extends JpaRepository<Report, Long> {

    @RepositoryMethod("신고: 중복 신고 여부 확인")
    boolean existsByTargetTypeAndTargetIdxAndReporterIdx(ReportTargetType targetType, Long targetIdx, Long reporterIdx);

    @RepositoryMethod("신고: 전체 목록 조회")
    List<Report> findAllByOrderByCreatedAtDesc();

    @RepositoryMethod("신고: 상태별 목록 조회")
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    @RepositoryMethod("신고: 대상 유형별 목록 조회")
    List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType);

    @RepositoryMethod("신고: 대상 유형+상태별 목록 조회")
    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(ReportTargetType targetType, ReportStatus status);

    /**
     * 신고 목록 projection 페이징 조회 (관리자용). reporter/handledBy를 통째로 로딩하던 것을
     * idx/username 2컬럼만 SELECT하도록 축소하고, 전건 인메모리 조회 대신 DB 페이징으로 바꿔 데이터 누적에도 조회량이
     * 일정하다. (reportCount는 소비처가 없어 채우지 않음 — null)
     */
    @RepositoryMethod("신고: 필터 조건별 목록 projection 페이징")
    @Query("SELECT new com.linkup.Petory.domain.report.dto.ReportDTO("
            + "  r.idx, r.targetType, r.targetIdx, reporter.idx, reporter.username, "
            + "  r.reason, r.status, r.actionTaken, handler.idx, handler.username, "
            + "  r.handledAt, r.adminNote, r.createdAt, r.updatedAt, CAST(NULL AS integer)) "
            + "FROM Report r "
            + "JOIN r.reporter reporter "
            + "LEFT JOIN r.handledBy handler "
            + "WHERE (:targetType IS NULL OR r.targetType = :targetType) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "ORDER BY r.createdAt DESC")
    Page<ReportDTO> findReportListItems(
            @Param("targetType") ReportTargetType targetType,
            @Param("status") ReportStatus status,
            Pageable pageable);

    @RepositoryMethod("신고: 기간별 통계")
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @RepositoryMethod("신고: 상태+기간별 통계 (처리 신고 집계용)")
    long countByStatusAndUpdatedAtBetween(ReportStatus status, LocalDateTime start, LocalDateTime end);
}
