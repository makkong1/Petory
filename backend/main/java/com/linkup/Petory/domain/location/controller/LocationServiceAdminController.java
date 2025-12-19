package com.linkup.Petory.domain.location.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.linkup.Petory.domain.location.dto.LocationServiceLoadResponse;
import com.linkup.Petory.domain.location.service.LocationServiceAdminService;
import com.linkup.Petory.domain.location.service.PublicDataLocationService;
import com.linkup.Petory.domain.location.service.PublicDataLocationService.BatchImportResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin/location-services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class LocationServiceAdminController {

    private final LocationServiceAdminService locationServiceAdminService;
    private final PublicDataLocationService publicDataLocationService;

    @PostMapping("/load-data")
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
    @PostMapping(value = "/import-public-data", consumes = "multipart/form-data")
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
    @PostMapping("/import-public-data-path")
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
