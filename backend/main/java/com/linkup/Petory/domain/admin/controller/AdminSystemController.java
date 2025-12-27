package com.linkup.Petory.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * MASTER 전용: 시스템 설정 관리 컨트롤러
 * - 시스템 주요 설정 변경
 * - 배치 스케줄 설정
 * - 알림 규칙 설정
 * - 포인트 정책 설정
 * - LBS 정책 설정
 */
@RestController
@RequestMapping("/api/master/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
@Slf4j
public class AdminSystemController {

    /**
     * 시스템 설정 조회
     * TODO: 실제 설정값은 DB나 설정 파일에서 관리
     */
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSystemSettings() {
        Map<String, Object> settings = new HashMap<>();
        
        // 스케줄러 설정
        settings.put("popularitySnapshotSchedule", "0 0 18 * * ?"); // 매일 오후 6시
        settings.put("careRequestExpirationCheck", "0 0 0 * * ?"); // 매일 자정
        
        // 알림 설정
        settings.put("notificationEnabled", true);
        settings.put("notificationTTLHours", 24);
        
        // 포인트 정책 (예시)
        settings.put("pointPolicy", Map.of(
            "commentReward", 10,
            "postReward", 50,
            "likeReward", 5
        ));
        
        // LBS 정책 (예시)
        settings.put("lbsPolicy", Map.of(
            "maxRadiusKm", 10,
            "locationUpdateInterval", 300 // 초 단위
        ));
        
        return ResponseEntity.ok(settings);
    }

    /**
     * 시스템 설정 업데이트
     * TODO: 실제 구현 시 설정값을 DB나 설정 파일에 저장
     */
    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(
            @RequestBody Map<String, Object> settings) {
        
        log.info("MASTER가 시스템 설정 변경: {}", settings);
        
        // TODO: 실제 설정값 저장 로직 구현
        // 예: 설정값을 DB에 저장하거나 설정 파일에 반영
        
        return ResponseEntity.ok(Map.of(
            "message", "시스템 설정이 업데이트되었습니다.",
            "settings", settings
        ));
    }

    /**
     * 스케줄러 상태 조회
     */
    @GetMapping("/scheduler/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 스케줄러 활성화 상태
        status.put("schedulingEnabled", true);
        status.put("popularitySnapshotEnabled", true);
        status.put("careRequestExpirationCheckEnabled", true);
        
        return ResponseEntity.ok(status);
    }

    /**
     * 스케줄러 활성화/비활성화
     */
    @PostMapping("/scheduler/{schedulerName}/toggle")
    public ResponseEntity<Map<String, String>> toggleScheduler(
            @PathVariable String schedulerName,
            @RequestBody Map<String, Boolean> request) {
        
        Boolean enabled = request.get("enabled");
        log.info("MASTER가 스케줄러 상태 변경: schedulerName={}, enabled={}", schedulerName, enabled);
        
        // TODO: 실제 스케줄러 활성화/비활성화 로직 구현
        // 예: @ConditionalOnProperty를 사용하거나 동적으로 스케줄러 제어
        
        return ResponseEntity.ok(Map.of(
            "message", String.format("스케줄러 '%s'가 %s되었습니다.", 
                schedulerName, enabled ? "활성화" : "비활성화")
        ));
    }

    /**
     * 공지사항 설정 조회
     */
    @GetMapping("/announcement")
    public ResponseEntity<Map<String, Object>> getAnnouncementSettings() {
        Map<String, Object> announcement = new HashMap<>();
        announcement.put("enabled", true);
        announcement.put("title", "");
        announcement.put("content", "");
        announcement.put("priority", "NORMAL"); // HIGH, NORMAL, LOW
        
        return ResponseEntity.ok(announcement);
    }

    /**
     * 공지사항 설정 업데이트
     */
    @PutMapping("/announcement")
    public ResponseEntity<Map<String, String>> updateAnnouncement(
            @RequestBody Map<String, Object> announcement) {
        
        log.info("MASTER가 공지사항 설정 변경: {}", announcement);
        
        // TODO: 실제 공지사항 저장 로직 구현
        
        return ResponseEntity.ok(Map.of(
            "message", "공지사항이 업데이트되었습니다."
        ));
    }
}

