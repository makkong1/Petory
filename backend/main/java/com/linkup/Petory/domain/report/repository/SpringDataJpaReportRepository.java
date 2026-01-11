package com.linkup.Petory.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaReportRepository extends JpaRepository<Report, Long> {

    boolean existsByTargetTypeAndTargetIdxAndReporterIdx(ReportTargetType targetType, Long targetIdx, Long reporterIdx);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType);

    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(ReportTargetType targetType, ReportStatus status);

    /**
     * 필터 조건에 맞는 신고 목록 조회
     */
    @Query("SELECT r FROM Report r " +
           "WHERE (:targetType IS NULL OR r.targetType = :targetType) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    List<Report> findReportsWithFilters(
            @Param("targetType") ReportTargetType targetType,
            @Param("status") ReportStatus status);
}

