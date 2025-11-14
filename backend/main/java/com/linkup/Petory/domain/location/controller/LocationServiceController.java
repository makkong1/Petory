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
    public ResponseEntity<Map<String, Object>> searchKakaoPlaces(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String categoryType) {
        try {
            List<LocationServiceDTO> services = locationServiceService.searchKakaoPlaces(
                    keyword,
                    region,
                    latitude,
                    longitude,
                    radius,
                    size,
                    categoryType);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("카카오 장소 검색 요청이 유효하지 않습니다: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("카카오 장소 검색 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", "카카오 장소 검색 중 오류가 발생했습니다.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
