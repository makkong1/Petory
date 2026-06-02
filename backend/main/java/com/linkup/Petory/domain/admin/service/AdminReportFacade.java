package com.linkup.Petory.domain.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.service.ReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/** 관리자용 신고 조회·처리를 담당하는 퍼사드. */
public class AdminReportFacade {

    private final ReportService reportService;
    private final AdminAuditService auditService;

    public ReportDetailDTO getReportDetail(Long reportId) {
        return reportService.getReportDetail(reportId);
    }

    public List<ReportDTO> getReports(ReportTargetType targetType, ReportStatus status) {
        return reportService.getReports(targetType, status);
    }

    @Transactional
    public ReportDTO handleReport(Long reportId, ReportHandleRequest request, Long adminIdx) {
        ReportDTO result = reportService.handleReport(reportId, adminIdx, request);
        auditService.log(adminIdx, "REPORT_HANDLE", "REPORT", reportId,
                "status=" + request.getStatus() + ",action=" + request.getActionTaken());
        return result;
    }
}
