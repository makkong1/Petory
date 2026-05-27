package com.linkup.Petory.domain.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final long MAX_CSV_BYTES = 200 * 1024 * 1024L; // 200 MB (공공데이터 대용량 허용)
    private static final Set<String> ALLOWED_CSV_EXTENSIONS = Set.of(".csv");
    private static final Set<String> ALLOWED_CSV_CONTENT_TYPES = Set.of(
            "text/csv", "application/csv", "text/plain", "application/octet-stream",
            "application/vnd.ms-excel");

    private final LocationServiceAdminService locationServiceAdminService;
    private final LocationServiceService locationServiceService;
    private final PublicDataLocationService publicDataLocationService;

    /**
     * 지역서비스 목록 조회 (관리자용)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MASTER')")
    public ResponseEntity<Map<String, Object>> listLocationServices(
            @RequestParam(value = "sido", required = false) String sido,
            @RequestParam(value = "sigungu", required = false) String sigungu,
            @RequestParam(value = "eupmyeondong", required = false) String eupmyeondong,
            @RequestParam(value = "roadName", required = false) String roadName,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "q", required = false) String q) {

        // keyword(q)는 SQL WHERE에서 처리 — Java 후처리 불필요
        List<LocationServiceDTO> services = locationServiceService.searchLocationServicesByRegion(
                sido, sigungu, eupmyeondong, roadName, q, category, size);

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
            @RequestParam(value = "region", defaultValue = "서울특별시") String region,
            @RequestParam(value = "maxResultsPerKeyword", defaultValue = "10") Integer maxResultsPerKeyword,
            @RequestParam(value = "customKeywords", required = false) String customKeywords) {

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

        // 파일 크기 검증
        if (file.getSize() > MAX_CSV_BYTES) {
            log.warn("CSV 파일 크기 초과: {} bytes (최대 {} bytes)", file.getSize(), MAX_CSV_BYTES);
            return ResponseEntity.badRequest()
                    .body(BatchImportResult.builder()
                            .error(1)
                            .build());
        }

        // 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            boolean validExt = ALLOWED_CSV_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (!validExt) {
                log.warn("CSV 파일 확장자 불허: {}", originalFilename.replaceAll("[/\\\\]", "_"));
                return ResponseEntity.badRequest()
                        .body(BatchImportResult.builder()
                                .error(1)
                                .build());
            }
        }

        // Content-Type 검증 (null 허용)
        String contentType = file.getContentType();
        if (contentType != null) {
            String baseType = contentType.split(";")[0].trim().toLowerCase();
            if (!ALLOWED_CSV_CONTENT_TYPES.contains(baseType)) {
                log.warn("CSV Content-Type 불허: {}", contentType);
                return ResponseEntity.badRequest()
                        .body(BatchImportResult.builder()
                                .error(1)
                                .build());
            }
        }

        String safeFilename = originalFilename != null
                ? originalFilename.replaceAll("[/\\\\]", "_") : "unknown";
        log.info("공공데이터 CSV 파일 업로드 임포트 요청: {} ({} bytes)", safeFilename, file.getSize());

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
            @RequestParam("csvFilePath") String csvFilePath) {

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
