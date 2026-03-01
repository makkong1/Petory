package com.linkup.Petory.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @RepositoryMethod("신고: 필터 조건별 목록 조회")
    @Query("SELECT r FROM Report r " +
           "WHERE (:targetType IS NULL OR r.targetType = :targetType) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.createdAt DESC")
    List<Report> findReportsWithFilters(
            @Param("targetType") ReportTargetType targetType,
            @Param("status") ReportStatus status);
}

