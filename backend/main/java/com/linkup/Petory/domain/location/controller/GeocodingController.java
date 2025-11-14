package com.linkup.Petory.domain.location.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.location.service.KakaoMapService;

import java.util.HashMap;
import java.util.Map;

/**
 * 주소를 위도/경도로 변환하는 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final KakaoMapService kakaoMapService;

    /**
     * 주소를 위도/경도로 변환
     * GET /api/geocoding/address?address=서울시 강남구
     */
    @GetMapping("/address")
    public ResponseEntity<Map<String, Object>> addressToCoordinates(@RequestParam String address) {
        try {
            Double[] coordinates = kakaoMapService.addressToCoordinates(address);

            Map<String, Object> response = new HashMap<>();
            if (coordinates != null && coordinates.length == 2) {
                response.put("latitude", coordinates[0]);
                response.put("longitude", coordinates[1]);
                response.put("success", true);
            } else {
                response.put("success", false);
                response.put("message", "주소를 좌표로 변환할 수 없습니다.");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("주소 변환 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }
}
