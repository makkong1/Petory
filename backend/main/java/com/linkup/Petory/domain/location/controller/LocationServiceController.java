package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.service.LocationRecommendAgentService;
import com.linkup.Petory.domain.location.service.LocationServiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final LocationRecommendAgentService locationRecommendAgentService;

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
     * @param keyword      키워드 (선택, 이름/설명/카테고리 검색, 예: "동물병원", "카페")
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
            @RequestParam(required = false) String keyword,
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

            log.info(
                    "🚀 [성능 측정] 위치 서비스 검색 시작 - latitude={}, longitude={}, radius={}, sido={}, sigungu={}, eupmyeondong={}, category={}, keyword={}, size={} (effectiveSize={})",
                    latitude, longitude, radius, sido, sigungu, eupmyeondong, category, keyword, size, effectiveSize);

            // 하이브리드 전략: 키워드 검색 > 위치 기반 검색 > 지역 계층별 검색
            List<LocationServiceDTO> services;
            if (StringUtils.hasText(keyword)) {
                // 키워드 검색 우선 (FULLTEXT 인덱스 활용)
                services = locationServiceService.searchLocationServicesByKeyword(keyword, category, effectiveSize);
            } else if (latitude != null && longitude != null && radius != null) {
                // 초기 로드: 위치 기반 반경 검색 (빠르고 적은 데이터)
                int radiusInMeters = radius > 0 ? radius : 10000; // 기본값 10km
                services = locationServiceService.searchLocationServicesByLocation(
                        latitude, longitude, radiusInMeters, category, effectiveSize);
            } else {
                // 이후 검색: 시도/시군구 기반 검색 (일관성 유지)
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

    /**
     * AI 추천 (에이전트 2)
     * 검색 결과를 LLM에 넘겨 상위 10개 재순위화 + 각 1줄 추천 이유 추가.
     * 권한: 로그인 필요.
     *
     * @param latitude     위도 (위치 기반 검색 시)
     * @param longitude    경도 (위치 기반 검색 시)
     * @param radius       반경 (미터, 기본 10000)
     * @param sido         시도 (지역 검색 시)
     * @param sigungu      시군구 (지역 검색 시)
     * @param eupmyeondong 읍면동 (지역 검색 시)
     * @param roadName     도로명 (지역 검색 시)
     * @param category     카테고리 (선택)
     * @param keyword      키워드 (선택)
     * @return 상위 10개 + recommendationReason
     */
    @GetMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommendLocationServices(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String eupmyeondong,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        try {
            Integer effectiveSize = 30; // AI에 넘길 후보 수
            List<LocationServiceDTO> services;
            if (StringUtils.hasText(keyword)) {
                services = locationServiceService.searchLocationServicesByKeyword(keyword, category, effectiveSize);
            } else if (latitude != null && longitude != null && radius != null) {
                int radiusInMeters = radius > 0 ? radius : 10000;
                services = locationServiceService.searchLocationServicesByLocation(
                        latitude, longitude, radiusInMeters, category, effectiveSize);
            } else {
                services = locationServiceService.searchLocationServicesByRegion(
                        sido, sigungu, eupmyeondong, roadName, category, effectiveSize);
            }

            List<LocationServiceDTO> recommended = locationRecommendAgentService.enrichWithRecommendations(
                    services, category);

            Map<String, Object> response = new HashMap<>();
            response.put("services", recommended);
            response.put("count", recommended.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI 추천 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "AI 추천 중 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 위치 서비스 삭제 (Soft Delete)
     * 
     * @param serviceIdx 서비스 ID
     * @return 삭제 결과
     */
    @DeleteMapping("/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable Long serviceIdx) {
        try {
            locationServiceService.deleteService(serviceIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "서비스가 성공적으로 삭제되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 삭제 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
