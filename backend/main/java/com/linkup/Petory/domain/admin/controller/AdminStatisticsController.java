package com.linkup.Petory.domain.admin.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.service.StatisticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 통계 조회 - ADMIN과 MASTER 모두 접근 가능
     */
    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<List<DailyStatistics>> getDailyStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // 기본값: 최근 30일
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(29);
        }

        return ResponseEntity.ok(statisticsService.getDailyStatistics(startDate, endDate));
    }

    /**
     * 통계 초기화 - MASTER만 접근 가능 (시스템 설정 변경)
     */
    @PostMapping("/init")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<String> initStatistics(@RequestParam(defaultValue = "30") int days) {
        statisticsService.initStatistics(days);
        return ResponseEntity.ok("지난 " + days + "일간의 통계 집계가 완료되었습니다.");
    }
}

