package com.linkup.Petory.domain.location.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.location.service.KakaoMapService;
import com.linkup.Petory.domain.location.service.NaverMapService;

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
    private final NaverMapService naverMapService;

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

    /**
     * 네이버맵 길찾기 (Directions API)
     * GET /api/geocoding/directions?start=127.1058342,37.359708&goal=129.075986,35.179470&option=traoptimal
     */
    @GetMapping("/directions")
    public ResponseEntity<Map<String, Object>> getDirections(
            @RequestParam String start, // 경도,위도 형식
            @RequestParam String goal,  // 경도,위도 형식
            @RequestParam(required = false, defaultValue = "traoptimal") String option) {
        try {
            // start와 goal 파싱 (경도,위도 형식)
            String[] startCoords = start.split(",");
            String[] goalCoords = goal.split(",");
            
            if (startCoords.length != 2 || goalCoords.length != 2) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "좌표 형식이 올바르지 않습니다. (경도,위도) 형식으로 입력해주세요.");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            double startLng = Double.parseDouble(startCoords[0].trim());
            double startLat = Double.parseDouble(startCoords[1].trim());
            double endLng = Double.parseDouble(goalCoords[0].trim());
            double endLat = Double.parseDouble(goalCoords[1].trim());

            Map<String, Object> result = naverMapService.getDirections(startLng, startLat, endLng, endLat, option);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("길찾기 API 호출 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
