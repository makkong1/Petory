package com.linkup.Petory.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByTargetTypeAndTargetIdxAndReporterIdx(ReportTargetType targetType, Long targetIdx, Long reporterIdx);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType);

    List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(ReportTargetType targetType, ReportStatus status);
}

