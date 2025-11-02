package com.linkup.Petory.controller;

import com.linkup.Petory.dto.LocationServiceDTO;
import com.linkup.Petory.service.LocationServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/location-services")
@RequiredArgsConstructor
public class LocationServiceController {

    private final LocationServiceService serviceService;

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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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

    // 키워드로 서비스 검색 (이름 또는 설명)
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
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

    // 지역(주소)으로 서비스 검색
    @GetMapping("/search/address")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> searchServicesByAddress(@RequestParam String address) {
        try {
            List<LocationServiceDTO> services = serviceService.searchServicesByAddress(address);

            Map<String, Object> response = new HashMap<>();
            response.put("services", services);
            response.put("count", services.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("지역 검색 실패: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 평점순 서비스 조회
    @GetMapping("/top-rated")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> createService(@RequestBody LocationServiceDTO serviceDTO) {
        try {
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
    @PreAuthorize("hasRole('MASTER')")
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
    @PreAuthorize("hasRole('MASTER')")
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
}
