package com.linkup.Petory.domain.report.repository;

import java.util.List;
import java.util.Optional;

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
     * 전체 신고 목록 조회 (최신순)
     */
    List<Report> findAllByOrderByCreatedAtDesc();

    /**
     * 상태별 신고 목록 조회
     */
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    /**
     * 타겟 타입별 신고 목록 조회
     */
    List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType);

    /**
     * 타겟 타입과 상태별 신고 목록 조회
     */
    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(ReportTargetType targetType, ReportStatus status);

    /**
     * 필터 조건에 맞는 신고 목록 조회
     */
    List<Report> findReportsWithFilters(ReportTargetType targetType, ReportStatus status);
}

