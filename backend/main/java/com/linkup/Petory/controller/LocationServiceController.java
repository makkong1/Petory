package com.linkup.Petory.controller;

import com.linkup.Petory.dto.LocationServiceDTO;
import com.linkup.Petory.service.LocationServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/location-services")
@RequiredArgsConstructor
public class LocationServiceController {

    private final LocationServiceService serviceService;

    @Value("${kakao.rest-api-key}")
    private String kakaoKey; // application.properties 에 저장

    // 모든 서비스 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllServices() {
        try {
            List<LocationServiceDTO> services = serviceService.getAllServices();

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 목록 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 특정 서비스 조회
    @GetMapping("/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> getServiceById(@PathVariable Long serviceIdx) {
        try {
            LocationServiceDTO service = serviceService.getServiceById(serviceIdx);

            Map<String, Object> response = new HashMap<>();
            response.put("service", service);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 카테고리별 서비스 조회
    @GetMapping("/category/{category}")
    public ResponseEntity<Map<String, Object>> getServicesByCategory(@PathVariable String category) {
        try {
            List<LocationServiceDTO> services = serviceService.getServicesByCategory(category);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("카테고리별 서비스 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 지역별 서비스 조회
    @GetMapping("/location")
    public ResponseEntity<Map<String, Object>> getServicesByLocation(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng) {
        try {
            List<LocationServiceDTO> services = serviceService.getServicesByLocation(minLat, maxLat, minLng, maxLng);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("지역별 서비스 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 키워드로 서비스 검색
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchServicesByKeyword(@RequestParam String keyword) {
        try {
            List<LocationServiceDTO> services = serviceService.searchServicesByKeyword(keyword);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 검색 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 평점순 서비스 조회
    @GetMapping("/top-rated")
    public ResponseEntity<Map<String, Object>> getTopRatedServices() {
        try {
            List<LocationServiceDTO> services = serviceService.getServicesByRating();

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("평점순 서비스 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 특정 평점 이상의 서비스 조회
    @GetMapping("/rating")
    public ResponseEntity<Map<String, Object>> getServicesByMinRating(@RequestParam Double minRating) {
        try {
            List<LocationServiceDTO> services = serviceService.getServicesByMinRating(minRating);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("평점별 서비스 조회 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 서비스 생성
    @PostMapping
    public ResponseEntity<Map<String, Object>> createService(@RequestBody LocationServiceDTO serviceDTO) {
        try {
            // 주소에서 위도/경도 자동 설정
            if (serviceDTO.getAddress() != null) {
                Map<String, Double> latLng = geocodeAddress(serviceDTO.getAddress());
                serviceDTO.setLatitude(latLng.get("latitude"));
                serviceDTO.setLongitude(latLng.get("longitude"));
            }

            LocationServiceDTO createdService = serviceService.createService(serviceDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("service", createdService);
            response.put("message", "서비스가 성공적으로 생성되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 생성 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 서비스 수정
    @PutMapping("/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> updateService(@PathVariable Long serviceIdx,
            @RequestBody LocationServiceDTO serviceDTO) {
        try {
            LocationServiceDTO updatedService = serviceService.updateService(serviceIdx, serviceDTO);

            Map<String, Object> response = new HashMap<>();
            response.put("service", updatedService);
            response.put("message", "서비스가 성공적으로 수정되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("서비스 수정 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 서비스 삭제
    @DeleteMapping("/{serviceIdx}")
    public ResponseEntity<Map<String, Object>> deleteService(@PathVariable Long serviceIdx) {
        try {
            serviceService.deleteService(serviceIdx);

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

    @SuppressWarnings("unchecked")
    // 주소 → 위도/경도 조회
    private Map<String, Double> geocodeAddress(String address) throws Exception {
        System.err.println("현재 address: " + address);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoKey);
        System.err.println("현재 headers: " + headers);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://dapi.kakao.com/v2/local/search/address.json?query="
                + URLEncoder.encode(address, StandardCharsets.UTF_8);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && body.get("documents") != null) {
            List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("documents");
            if (!docs.isEmpty()) {
                Map<String, Object> first = docs.get(0);
                double lat = Double.parseDouble((String) first.get("y"));
                double lng = Double.parseDouble((String) first.get("x"));
                Map<String, Double> result = new HashMap<>();
                result.put("latitude", lat);
                result.put("longitude", lng);
                return result;
            }
        }
        throw new Exception("주소를 찾을 수 없습니다.");
    }
}
