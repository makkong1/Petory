package com.linkup.Petory.domain.admin.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.service.StatisticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Slf4j
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    // application.properties에서 기본값 읽기 (없으면 기본값 사용)
    @Value("${statistics.scheduler.hour:18}")
    private int defaultSchedulerHour;

    @Value("${statistics.scheduler.minute:30}")
    private int defaultSchedulerMinute;

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

    /**
     * 통계 집계 스케줄러 시간 조회 - ADMIN과 MASTER 모두 접근 가능
     */
    @GetMapping("/scheduler/time")
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<Map<String, Object>> getSchedulerTime() {
        Map<String, Object> response = new HashMap<>();

        // 현재 설정된 시간 (application.properties에서 읽거나 기본값)
        int hour = defaultSchedulerHour;
        int minute = defaultSchedulerMinute;

        response.put("hour", hour);
        response.put("minute", minute);
        response.put("time", String.format("%02d:%02d", hour, minute));
        response.put("cron", String.format("0 %d %d * * ?", minute, hour));
        response.put("description", String.format("매일 %02d시 %02d분에 실행", hour, minute));

        return ResponseEntity.ok(response);
    }

    /**
     * 통계 집계 스케줄러 시간 설정 - MASTER만 접근 가능
     * 
     * @param request {"hour": 18, "minute": 30} 형식
     * @return 설정 완료 메시지 및 주의사항
     */
    @PutMapping("/scheduler/time")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> setSchedulerTime(@RequestBody Map<String, Integer> request) {
        Integer hour = request.get("hour");
        Integer minute = request.get("minute");

        // 유효성 검사
        if (hour == null || minute == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "hour와 minute는 필수입니다.",
                    "example", Map.of("hour", 18, "minute", 30)));
        }

        if (hour < 0 || hour > 23) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "hour는 0-23 사이의 값이어야 합니다."));
        }

        if (minute < 0 || minute > 59) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "minute는 0-59 사이의 값이어야 합니다."));
        }

        log.info("MASTER가 통계 집계 스케줄러 시간 변경 요청: {}:{}", hour, minute);

        // TODO: 실제로는 application.properties 파일을 업데이트하거나 DB에 저장해야 함
        // 현재는 로그만 남기고, 실제 변경은 application.properties를 수동으로 수정 후 서버 재시작 필요
        // 향후 구현: 설정값을 DB에 저장하고 동적 스케줄러로 변경

        return ResponseEntity.ok(Map.of(
                "message", "스케줄러 시간이 설정되었습니다.",
                "hour", hour,
                "minute", minute,
                "time", String.format("%02d:%02d", hour, minute),
                "cron", String.format("0 %d %d * * ?", minute, hour),
                "warning",
                "실제 적용을 위해서는 application.properties에 'statistics.scheduler.hour'와 'statistics.scheduler.minute' 값을 설정하고 서버를 재시작해야 합니다.",
                "note", "동적 스케줄러 변경 기능은 향후 구현 예정입니다."));
    }
}
