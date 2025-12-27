package com.linkup.Petory.domain.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.service.ReportService;

import lombok.RequiredArgsConstructor;

/**
 * 신고 컨트롤러 (일반 사용자용)
 * - 일반 사용자가 신고를 생성할 때 사용
 * - 관리자 기능은 AdminReportController에서 처리
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 신고 생성 (일반 사용자용)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportDTO> createReport(@RequestBody ReportRequestDTO request) {
        return ResponseEntity.ok(reportService.createReport(request));
    }
}
