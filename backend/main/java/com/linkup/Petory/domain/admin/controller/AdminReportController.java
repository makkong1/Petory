package com.linkup.Petory.domain.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.service.ReportService;
import com.linkup.Petory.domain.user.repository.UsersRepository;

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

    private final ReportService reportService;
    private final UsersRepository usersRepository;

    /**
     * 신고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportDetailDTO> getReportDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reportService.getReportDetail(id));
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
        return ResponseEntity.ok(reportService.getReports(targetType, status));
    }

    /**
     * 신고 처리
     */
    @PostMapping("/{id}/handle")
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

