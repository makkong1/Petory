package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.service.LocationServiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/location-services")
@RequiredArgsConstructor
public class LocationServiceController {

    private final LocationServiceService locationServiceService;

    /**
     * DB에서 위치 서비스 검색
     * 위치 기반 검색 또는 지역 계층별 검색 수행
     * 
     * @param latitude     위도 (선택, 위치 기반 검색 시 필수)
     * @param longitude    경도 (선택, 위치 기반 검색 시 필수)
     * @param radius       반경 (미터 단위, 선택, 기본값: 10000m = 10km)
     * @param sido         시도 (선택, 예: "서울특별시", "경기도")
     * @param sigungu      시군구 (선택, 예: "노원구", "고양시 덕양구")
     * @param eupmyeondong 읍면동 (선택, 예: "상계동", "동산동")
     * @param roadName     도로명 (선택, 예: "상계로", "동세로")
     * @param category     카테고리 (선택, 예: "동물약국", "미술관")
     * @param size         최대 결과 수 (선택, 기본값: 500)
     * @return 검색 결과
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLocationServices(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String eupmyeondong,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer size) {
        try {
            // ========== 성능 측정 시작 ==========
            long startTime = System.currentTimeMillis();
            
            // 기본 결과 수 제한 (size 파라미터 없으면 100개로 제한)
            // 단, size가 명시적으로 0이거나 음수면 전체 조회 (null 전달)
            Integer effectiveSize = size;
            if (effectiveSize == null) {
                effectiveSize = 100; // 기본값: 100개
            } else if (effectiveSize <= 0) {
                effectiveSize = null; // 0 이하면 전체 조회
            }
            
            log.info("🚀 [성능 측정] 위치 서비스 검색 시작 - latitude={}, longitude={}, radius={}, sido={}, sigungu={}, eupmyeondong={}, category={}, size={} (effectiveSize={})",
                    latitude, longitude, radius, sido, sigungu, eupmyeondong, category, size, effectiveSize);

            // 위치 기반 검색 또는 지역 계층별 검색 수행
            List<LocationServiceDTO> services;
            if (latitude != null && longitude != null) {
                // 위치 기반 검색 (반경 검색)
                int radiusInMeters = (radius != null && radius > 0) ? radius : 10000; // 기본값 10km
                services = locationServiceService.searchLocationServicesByLocation(
                        latitude, longitude, radiusInMeters, category, effectiveSize);
            } else {
                // 지역 계층별 검색 (기존 로직)
                services = locationServiceService.searchLocationServicesByRegion(
                        sido,
                        sigungu,
                        eupmyeondong,
                        roadName,
                        category,
                        effectiveSize);
            }

            long queryTime = System.currentTimeMillis() - startTime;
            log.info("⏱️  [성능 측정] 위치 서비스 조회 완료 - 실행 시간: {}ms, 결과 수: {}개", queryTime, services.size());

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("✅ [성능 측정] 전체 처리 시간: {}ms", totalTime);
            // ========== 성능 측정 종료 ==========

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("위치 서비스 검색 요청이 유효하지 않습니다: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("위치 서비스 검색 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "위치 서비스 검색 중 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
