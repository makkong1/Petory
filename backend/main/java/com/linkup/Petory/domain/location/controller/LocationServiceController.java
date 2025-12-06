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

    /**
     * DB에서 위치 서비스 검색
     * 지역 계층별 검색만 수행 (내 위치는 거리 계산/길찾기용으로만 사용)
     * 
     * @param sido         시도 (선택, 예: "서울특별시", "경기도")
     * @param sigungu      시군구 (선택, 예: "노원구", "고양시 덕양구")
     * @param eupmyeondong 읍면동 (선택, 예: "상계동", "동산동")
     * @param roadName     도로명 (선택, 예: "상계로", "동세로")
     * @param category     카테고리 (선택, 예: "동물약국", "미술관")
     * @param size         최대 결과 수 (선택, 기본값: 500)
     * @return 검색 결과
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLocationServices(
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String eupmyeondong,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer size) {
        try {
            // 지역 계층별 검색만 수행
            List<LocationServiceDTO> services = locationServiceService.searchLocationServicesByRegion(
                    sido,
                    sigungu,
                    eupmyeondong,
                    roadName,
                    category,
                    size);

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
}
