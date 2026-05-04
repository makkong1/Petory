package com.linkup.Petory.domain.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.admin.service.AdminReportFacade;
import com.linkup.Petory.domain.report.dto.ReportAssistSuggestion;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

/**
 * 신고 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 신고 목록 조회, 상세 조회, 처리
 * - 일반 사용자 신고 생성은 ReportController에서 처리
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminReportController {

    private final AdminReportFacade adminReportFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    /**
     * 신고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportDetailDTO> getReportDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminReportFacade.getReportDetail(id));
    }

    /**
     * 신고 보조 에이전트: 요약·심각도·조치 제안 (참고용, 자동 처리 아님)
     * Ollama 연동. 실패 시 null 반환.
     */
    @GetMapping("/{id}/assist")
    public ResponseEntity<ReportAssistSuggestion> getReportAssist(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminReportFacade.getReportAssist(id));
    }

    /**
     * 게시글/댓글/실종 제보 등 신고 목록 조회 (관리자용)
     *
     * 예:
     * - 전체: GET /api/admin/reports
     * - 게시글 신고만: GET /api/admin/reports?targetType=BOARD
     * - 게시글 신고 중 미처리만: GET /api/admin/reports?targetType=BOARD&status=PENDING
     */
    @GetMapping
    public ResponseEntity<List<ReportDTO>> getReports(
            @RequestParam(value = "targetType", required = false) ReportTargetType targetType,
            @RequestParam(value = "status", required = false) ReportStatus status) {
        return ResponseEntity.ok(adminReportFacade.getReports(targetType, status));
    }

    /**
     * 신고 처리
     */
    @PostMapping("/{id}/handle")
    public ResponseEntity<ReportDTO> handleReport(
            @PathVariable("id") Long id,
            @RequestBody ReportHandleRequest request) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminReportFacade.handleReport(id, request, adminIdx));
    }
}
