package com.linkup.Petory.controller;

import com.linkup.Petory.service.LocationServiceDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LocationService 초기 데이터 로딩을 위한 관리자 컨트롤러
 * ADMIN 또는 MASTER 권한이 필요합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/location-services")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MASTER')")
public class LocationServiceDataController {

    private final LocationServiceDataLoader dataLoader;

    /**
     * 특정 지역의 초기 데이터를 수동으로 로드
     * 예: POST /api/admin/location-services/load-data
     * Body: { "region": "서울특별시", "maxResultsPerKeyword": 10, "customKeywords":
     * ["키워드1", "키워드2"] }
     */
    @PostMapping("/load-data")
    public ResponseEntity<Map<String, Object>> loadInitialData(
            @RequestParam(required = false, defaultValue = "서울특별시") String region,
            @RequestParam(required = false, defaultValue = "10") int maxResultsPerKeyword,
            @RequestParam(required = false) String customKeywords) {
        try {
            // 커스텀 키워드가 있으면 파싱, 없으면 null
            java.util.List<String> keywords = null;
            if (customKeywords != null && !customKeywords.trim().isEmpty()) {
                keywords = java.util.Arrays.asList(customKeywords.split(","));
                keywords = keywords.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }

            dataLoader.loadInitialDataManually(region, maxResultsPerKeyword, keywords);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "초기 데이터 로딩이 완료되었습니다.");
            response.put("region", region);
            response.put("maxResultsPerKeyword", maxResultsPerKeyword);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("초기 데이터 로딩 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
