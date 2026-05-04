package com.linkup.Petory.domain.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.report.dto.ReportAssistSuggestion;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.service.ReportAssistAgentService;
import com.linkup.Petory.domain.report.service.ReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportFacade {

    private final ReportService reportService;
    private final ReportAssistAgentService reportAssistAgentService;
    private final AdminAuditService auditService;

    public ReportDetailDTO getReportDetail(Long reportId) {
        return reportService.getReportDetail(reportId);
    }

    public ReportAssistSuggestion getReportAssist(Long reportId) {
        log.warn("[AI보조] API 진입 reportId={}", reportId);
        try {
            ReportDetailDTO detail = reportService.getReportDetail(reportId);
            java.util.Optional<ReportAssistSuggestion> opt = reportAssistAgentService.getAssistSuggestions(detail);
            log.warn("[AI보조] API 완료 reportId={} result={}", reportId, opt.isPresent() ? "있음" : "없음(null)");
            return opt.orElse(null);
        } catch (Exception e) {
            log.error("[AI보조] API 예외 reportId=" + reportId + " " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            throw e;
        }
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
