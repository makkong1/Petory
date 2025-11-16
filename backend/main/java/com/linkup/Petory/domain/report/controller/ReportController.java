package com.linkup.Petory.domain.report.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.service.ReportService;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UsersRepository usersRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportDTO> createReport(@RequestBody ReportRequestDTO request) {
        return ResponseEntity.ok(reportService.createReport(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<ReportDetailDTO> getReportDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reportService.getReportDetail(id));
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

    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<ReportDTO> handleReport(
            @PathVariable("id") Long id,
            @RequestBody ReportHandleRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        if (username == null) {
            throw new IllegalArgumentException("관리자 식별자를 확인할 수 없습니다.");
        }
        Long adminIdx = usersRepository.findByUsername(username)
                .map(u -> u.getIdx())
                .orElseThrow(() -> new IllegalArgumentException("관리자 식별자를 확인할 수 없습니다."));

        return ResponseEntity.ok(reportService.handleReport(id, adminIdx, request));
    }
}
