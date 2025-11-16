package com.linkup.Petory.domain.report.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportDTO> createReport(@RequestBody ReportRequestDTO request) {
        return ResponseEntity.ok(reportService.createReport(request));
    }

    /**
     * 게시글/댓글/실종 제보 등 신고 목록 조회 (관리자용)
     *
     * 예:
     * - 전체: GET /api/reports
     * - 게시글 신고만: GET /api/reports?targetType=BOARD
     * - 게시글 신고 중 미처리만: GET /api/reports?targetType=BOARD&status=PENDING
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<List<ReportDTO>> getReports(
            @RequestParam(value = "targetType", required = false) ReportTargetType targetType,
            @RequestParam(value = "status", required = false) ReportStatus status) {
        return ResponseEntity.ok(reportService.getReports(targetType, status));
    }
}
