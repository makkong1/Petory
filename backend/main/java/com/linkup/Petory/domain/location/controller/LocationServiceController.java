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

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLocationServices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String categoryType) {
        try {
            // 카테고리 타입을 실제 카테고리명으로 매핑
            String category = mapCategoryTypeToCategory(categoryType);
            
            // DB에서 반경 기반 검색
            List<LocationServiceDTO> services = locationServiceService.searchLocationServices(
                    latitude,
                    longitude,
                    radius,
                    size,
                    category);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

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

    // 카테고리 타입을 실제 카테고리명으로 매핑
    private String mapCategoryTypeToCategory(String categoryType) {
        if (categoryType == null || categoryType.isEmpty()) {
            return null;
        }
        
        String upperType = categoryType.toUpperCase();
        switch (upperType) {
            case "HOSPITAL":
                return "동물병원";
            case "CAFE":
                return "애견카페";
            case "PLAYGROUND":
                return "애견놀이터";
            default:
                // 소문자로 들어온 경우도 처리
                String lowerType = categoryType.toLowerCase();
                if ("hospital".equals(lowerType)) {
                    return "동물병원";
                } else if ("cafe".equals(lowerType)) {
                    return "애견카페";
                } else if ("playground".equals(lowerType)) {
                    return "애견놀이터";
                }
                return categoryType; // 그대로 사용
        }
    }
}
