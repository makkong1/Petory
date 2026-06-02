package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.statistics.dto.DailyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.MonthlyStatisticsResponse;
import com.linkup.Petory.domain.statistics.dto.TodaySnapshotResponse;
import com.linkup.Petory.domain.statistics.dto.WeeklyStatisticsResponse;
import com.linkup.Petory.domain.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
/** 관리자용 일별·주간·월간 통계 조회 및 backfill API. [MASTER] */
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/daily")
    public ResponseEntity<List<DailyStatisticsResponse>> getDailyStatistics(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);
        return ResponseEntity.ok(statisticsService.getDailyStatistics(startDate, endDate));
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyStatisticsResponse>> getWeeklyStatistics(
            @RequestParam(name = "year", defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        return ResponseEntity.ok(statisticsService.getWeeklyStatistics(year));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyStatisticsResponse>> getMonthlyStatistics(
            @RequestParam(name = "year", defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        return ResponseEntity.ok(statisticsService.getMonthlyStatistics(year));
    }

    @GetMapping("/summary")
    public ResponseEntity<TodaySnapshotResponse> getTodaySnapshot() {
        return ResponseEntity.ok(statisticsService.getTodaySnapshot());
    }

    @PostMapping("/backfill")
    public ResponseEntity<String> backfill(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        statisticsService.backfill(startDate, endDate);
        return ResponseEntity.ok(startDate + " ~ " + endDate + " 통계 집계가 완료되었습니다.");
    }
}
