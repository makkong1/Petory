package com.linkup.Petory.controller;

import com.linkup.Petory.service.LocationServiceDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LocationService 초기 데이터 로딩을 위한 관리자 컨트롤러
 * 실제 운영 환경에서는 보안 설정을 추가해야 합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/location-services")
@RequiredArgsConstructor
public class LocationServiceDataController {

    private final LocationServiceDataLoader dataLoader;

    /**
     * 특정 지역의 초기 데이터를 수동으로 로드
     * 예: POST /api/admin/location-services/load-data?region=서울특별시&maxResults=50
     */
    @PostMapping("/load-data")
    public ResponseEntity<Map<String, Object>> loadInitialData(
            @RequestParam(required = false, defaultValue = "서울특별시") String region,
            @RequestParam(required = false, defaultValue = "50") int maxResults) {
        try {
            dataLoader.loadInitialDataManually(region, maxResults);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "초기 데이터 로딩이 완료되었습니다.");
            response.put("region", region);
            response.put("maxResultsPerKeyword", maxResults);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("초기 데이터 로딩 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

