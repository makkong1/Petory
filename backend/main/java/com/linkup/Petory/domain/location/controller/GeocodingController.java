package com.linkup.Petory.domain.location.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final NaverMapService naverMapService;

    /**
     * 주소를 위도/경도로 변환 (네이버맵 Geocoding API)
     * GET /api/geocoding/address?address=서울시 강남구
     */
    @GetMapping("/address")
    public ResponseEntity<Map<String, Object>> addressToCoordinates(@RequestParam String address) {
        // URL 디코딩 및 + 문자를 공백으로 변환
        if (address != null) {
            // + 문자가 있으면 공백으로 변환 (URL 인코딩에서 +는 공백을 의미)
            address = address.replace("+", " ");
            // URL 디코딩 (이미 Spring이 자동으로 디코딩하지만 확실하게)
            try {
                address = java.net.URLDecoder.decode(address, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                log.warn("주소 디코딩 실패: {}", e.getMessage());
            }
        }
        log.info("🚀 [지오코딩] 요청 수신 - 원본 주소: {}", address);
        log.info("🚀 [지오코딩] 주소 길이: {}, 공백 포함 여부: {}", address != null ? address.length() : 0,
                address != null && address.contains(" "));
        log.info("🚀 [지오코딩] NaverMapService 호출 시작");
        try {
            Double[] coordinates = naverMapService.addressToCoordinates(address);
            log.info("🚀 [지오코딩] NaverMapService 호출 완료 - 결과: {}", coordinates != null ? "성공" : "null");

            Map<String, Object> response = new HashMap<>();
            if (coordinates != null && coordinates.length == 2) {
                response.put("latitude", coordinates[0]);
                response.put("longitude", coordinates[1]);
                response.put("success", true);
                log.info("✅ 지오코딩 성공 - 주소: {}, 좌표: ({}, {})", address, coordinates[0], coordinates[1]);
            } else {
                response.put("success", false);
                response.put("message", "주소를 좌표로 변환할 수 없습니다. 네이버 클라우드 플랫폼에서 Geocoding API 구독이 필요할 수 있습니다.");
                log.warn("⚠️ 주소 변환 실패 - 주소: {}, 좌표: {}", address, coordinates);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ 주소 변환 실패 - 주소: {}, 에러: {}", address, e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "주소 변환 중 오류가 발생했습니다. 네이버 클라우드 플랫폼에서 Geocoding API 구독을 확인해주세요.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 네이버맵 길찾기 (Directions API)
     * GET
     * /api/geocoding/directions?start=127.1058342,37.359708&goal=129.075986,35.179470&option=traoptimal
     */
    @GetMapping("/directions")
    public ResponseEntity<Map<String, Object>> getDirections(
            @RequestParam String start, // 경도,위도 형식
            @RequestParam String goal, // 경도,위도 형식
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

    /**
     * 위도/경도를 주소로 변환 (역지오코딩)
     * GET /api/geocoding/coordinates?lat=37.5665&lng=126.9780
     */
    @GetMapping("/coordinates")
    public ResponseEntity<Map<String, Object>> coordinatesToAddress(
            @RequestParam double lat,
            @RequestParam double lng) {
        try {
            Map<String, Object> result = naverMapService.coordinatesToAddress(lat, lng);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("역지오코딩 실패: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
