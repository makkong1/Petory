package com.linkup.Petory.domain.location.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
     * 통합 검색 — 파라미터 조합에 따라 4가지 경로로 분기
     *
     * 1) latitude + longitude 있음  → 반경 검색   (지도 초기 로드 / "이 지역 검색")
     * 2) sido / sigungu 있음        → 지역 검색   (강남구, 서울특별시 등 지역명)
     * 3) keyword만 있음             → FULLTEXT    (시설명 전국 검색, fallback)
     * 4) 아무것도 없음              → 전체 평점순
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLocationServices(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer size) {
        try {
            // size: null → 100건 상한 / ≤0 → 제한 없음
            Integer effectiveSize = (size == null) ? 100 : (size <= 0 ? null : size);
            long t0 = System.currentTimeMillis();

            List<LocationServiceDTO> services;

            if (latitude != null && longitude != null) {
                // ① 반경 검색: 지도 초기 로드, "이 지역 검색" 버튼
                int radiusM = (radius != null && radius > 0) ? radius : 10_000;
                services = locationServiceService.searchLocationServicesByLocation(
                        latitude, longitude, radiusM, keyword, category, sort, effectiveSize);

            } else if (StringUtils.hasText(sigungu) || StringUtils.hasText(sido)) {
                // ② 지역 검색: 강남구·서울특별시 등 지역명 직접 검색
                services = locationServiceService.searchLocationServicesByRegion(
                        sido, sigungu, keyword, category, effectiveSize);

            } else if (StringUtils.hasText(keyword)) {
                // ③ FULLTEXT 키워드 검색: 시설명 전국 검색 (위치·지역 없을 때)
                services = locationServiceService.searchLocationServicesByKeyword(
                        keyword, category, effectiveSize);

            } else {
                // ④ 기본: 전체 평점순 (카테고리 필터만 적용)
                services = locationServiceService.searchLocationServicesByRegion(
                        null, null, null, category, effectiveSize);
            }

            // score 정렬: DB ORDER BY 미지원 → 결과 Java 후처리
            if ("score".equalsIgnoreCase(sort)) {
                services = new ArrayList<>(services);
                services.sort(Comparator.comparingDouble(
                        (LocationServiceDTO dto) -> dto.getScore() != null ? dto.getScore() : 0.0
                ).reversed());
            }

            log.info("[location-services/search] {}ms, {}건", System.currentTimeMillis() - t0, services.size());

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("잘못된 검색 요청: {}", e.getMessage());
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

    @DeleteMapping("/{serviceIdx}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
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
