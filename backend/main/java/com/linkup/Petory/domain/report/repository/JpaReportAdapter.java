package com.linkup.Petory.domain.report.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;

import lombok.RequiredArgsConstructor;

/**
 * ReportRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaReportAdapter implements ReportRepository {

    private final SpringDataJpaReportRepository jpaRepository;

    @Override
    public Report save(Report report) {
        return jpaRepository.save(report);
    }

    @Override
    public Optional<Report> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(Report report) {
        jpaRepository.delete(report);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByTargetTypeAndTargetIdxAndReporterIdx(ReportTargetType targetType, Long targetIdx, Long reporterIdx) {
        return jpaRepository.existsByTargetTypeAndTargetIdxAndReporterIdx(targetType, targetIdx, reporterIdx);
    }

    @Override
    public List<Report> findAllByOrderByCreatedAtDesc() {
        return jpaRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public List<Report> findByTargetTypeOrderByCreatedAtDesc(ReportTargetType targetType) {
        return jpaRepository.findByTargetTypeOrderByCreatedAtDesc(targetType);
    }

    @Override
    public List<Report> findByTargetTypeAndStatusOrderByCreatedAtDesc(ReportTargetType targetType, ReportStatus status) {
        return jpaRepository.findByTargetTypeAndStatusOrderByCreatedAtDesc(targetType, status);
    }

    @Override
    public List<Report> findReportsWithFilters(ReportTargetType targetType, ReportStatus status) {
        return jpaRepository.findReportsWithFilters(targetType, status);
    }
}

