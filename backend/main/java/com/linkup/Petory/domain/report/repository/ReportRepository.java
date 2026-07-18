package com.linkup.Petory.domain.report.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;

/**
 * Report 도메인 Repository 인터페이스입니다.
 */
public interface ReportRepository {

    // 기본 CRUD 메서드
    Report save(Report report);

    Optional<Report> findById(Long id);

    void delete(Report report);

    void deleteById(Long id);

    /**
     * 중복 신고 확인
     */
    boolean existsByTargetTypeAndTargetIdxAndReporterIdx(ReportTargetType targetType, Long targetIdx, Long reporterIdx);

    /**
     * 필터 조건에 맞는 신고 목록 projection 페이징 조회 (관리자용)
     */
    Page<ReportDTO> findReportListItems(ReportTargetType targetType, ReportStatus status, Pageable pageable);

    /**
     * 통계용: 특정 기간 동안 접수된 신고 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 통계용: 특정 기간 동안 특정 상태로 처리된 신고 수 (updatedAt 기준)
     */
    long countByStatusAndUpdatedAtBetween(ReportStatus status, LocalDateTime start, LocalDateTime end);
}
