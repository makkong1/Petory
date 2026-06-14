package com.linkup.Petory.domain.location.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.service.LocationServiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/location-services")
@RequiredArgsConstructor
public class LocationServiceController {

    private final LocationServiceService locationServiceService;

    /**
     * DB에서 위치 서비스 검색 위치 기반 검색 또는 지역 계층별 검색 수행
     *
     * @param latitude 위도 (선택, 위치 기반 검색 시 필수)
     * @param longitude 경도 (선택, 위치 기반 검색 시 필수)
     * @param radius 반경 (미터 단위, 선택, 기본값: 10000m = 10km)
     * @param sido 시도 (선택, 예: "서울특별시", "경기도")
     * @param sigungu 시군구 (선택, 예: "노원구", "고양시 덕양구")
     * @param category 카테고리 (선택). DB의 category1·category2·category3 와 문자열 동일해야
     * 매칭됨. 소분류 예: "동물약국","동물병원","카페","미술관" / 중분류 예:
     * "반려의료","반려동반여행","반려동물식당카페","반려동물 서비스","반려문화시설"
     * @param keyword 키워드 (선택, 이름/설명/카테고리 검색, 예: "동물병원", "카페")
     * @param sort 반경 검색 정렬 기준 (선택, stable|distance|rating|reviews|score, 기본값:
     * distance)
     * @param size 최대 결과 수 (선택, 기본값: 100, 0 이하이면 서비스 기본 제한)
     * @return 검색 결과
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLocationServices(
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "radius", required = false) Integer radius,
            @RequestParam(value = "sido", required = false) String sido,
            @RequestParam(value = "sigungu", required = false) String sigungu,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            // ========== 성능 측정 시작 ==========
            long startTime = System.currentTimeMillis();

            // 기본 결과 수 제한 (size 파라미터 없으면 100개로 제한)
            // 단, size가 명시적으로 0이거나 음수면 서비스 계층의 기본 제한을 사용한다.
            Integer effectiveSize = size;
            if (effectiveSize == null) {
                effectiveSize = 100; // 기본값: 100개
            } else if (effectiveSize <= 0) {
                effectiveSize = null; // 0 이하면 전체 조회
            }

            log.info(
                    "🚀 [성능 측정] 위치 서비스 검색 시작 - latitude={}, longitude={}, radius={}, sido={}, sigungu={}, category={}, keyword={}, sort={}, size={} (effectiveSize={})",
                    latitude, longitude, radius, sido, sigungu, category, keyword, sort, size, effectiveSize);

            List<LocationServiceDTO> services = locationServiceService.searchLocationServices(
                    keyword,
                    latitude,
                    longitude,
                    radius,
                    sido,
                    sigungu,
                    category,
                    sort,
                    effectiveSize);

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
     * 위치 서비스 삭제 (Soft Delete)
     *
     * @param serviceIdx 서비스 ID
     * @return 삭제 결과
     */
    @DeleteMapping("/{serviceIdx}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable("serviceIdx") Long serviceIdx) {
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
