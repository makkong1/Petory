package com.linkup.Petory.domain.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceLoadResponse;
import com.linkup.Petory.domain.location.service.LocationServiceAdminService;
import com.linkup.Petory.domain.location.service.LocationServiceService;
import com.linkup.Petory.domain.location.service.PublicDataLocationService;
import com.linkup.Petory.domain.location.service.PublicDataLocationService.BatchImportResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지역서비스 관리 컨트롤러 (관리자용)
 * - MASTER만 접근 가능 (데이터 임포트)
 * - ADMIN과 MASTER 모두 접근 가능 (목록 조회)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/location-services")
@RequiredArgsConstructor
public class AdminLocationController {

    private final LocationServiceAdminService locationServiceAdminService;
    private final LocationServiceService locationServiceService;
    private final PublicDataLocationService publicDataLocationService;

    /**
     * 지역서비스 목록 조회 (관리자용)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<Map<String, Object>> listLocationServices(
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sigungu,
            @RequestParam(required = false) String eupmyeondong,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q) {
        
        List<LocationServiceDTO> services = locationServiceService.searchLocationServicesByRegion(
                sido, sigungu, eupmyeondong, roadName, category, size);
        
        // 검색어 필터
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            services = services.stream()
                    .filter(s -> (s.getName() != null && s.getName().toLowerCase().contains(keyword))
                            || (s.getAddress() != null && s.getAddress().toLowerCase().contains(keyword))
                            || (s.getCategory1() != null && s.getCategory1().toLowerCase().contains(keyword))
                            || (s.getCategory2() != null && s.getCategory2().toLowerCase().contains(keyword))
                            || (s.getCategory3() != null && s.getCategory3().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("services", services);
        response.put("count", services.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 초기 데이터 로드 (MASTER만)
     */
    @PostMapping("/load-data")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<LocationServiceLoadResponse> loadInitialData(
            @RequestParam(defaultValue = "서울특별시") String region,
            @RequestParam(defaultValue = "10") Integer maxResultsPerKeyword,
            @RequestParam(required = false) String customKeywords) {

        LocationServiceLoadResponse response = locationServiceAdminService.loadInitialData(
                region,
                maxResultsPerKeyword,
                customKeywords);
        return ResponseEntity.ok(response);
    }

    /**
     * 공공데이터 CSV 파일을 업로드하여 임포트 (파일 업로드 방식)
     * 
     * @param file 업로드된 CSV 파일
     * @return 배치 임포트 결과
     */
    /**
     * 공공데이터 CSV 파일 임포트 (MASTER만)
     */
    @PostMapping(value = "/import-public-data", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<BatchImportResult> importPublicData(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BatchImportResult.builder()
                            .error(1)
                            .build());
        }

        log.info("공공데이터 CSV 파일 업로드 임포트 요청: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            BatchImportResult result = publicDataLocationService.importFromCsv(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("공공데이터 CSV 임포트 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(BatchImportResult.builder()
                            .error(1)
                            .build());
        }
    }

    /**
     * 공공데이터 CSV 파일 경로로 임포트 (기존 방식 - 하위 호환성)
     * 
     * @param csvFilePath CSV 파일의 절대 경로 또는 상대 경로
     * @return 배치 임포트 결과
     */
    /**
     * 공공데이터 CSV 파일 경로 임포트 (MASTER만)
     */
    @PostMapping("/import-public-data-path")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<BatchImportResult> importPublicDataByPath(
            @RequestParam String csvFilePath) {

        log.info("공공데이터 CSV 경로 임포트 요청: {}", csvFilePath);

        try {
            BatchImportResult result = publicDataLocationService.importFromCsv(csvFilePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("공공데이터 CSV 임포트 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(BatchImportResult.builder()
                            .error(1)
                            .build());
        }
    }
}

