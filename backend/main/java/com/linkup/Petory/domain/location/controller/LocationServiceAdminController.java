package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.service.LocationImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final LocationImportService locationImportService;

    @Value("${app.location.import.file-path:}")
    private String importFilePath;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> syncFromConfiguredPath() {
        if (!StringUtils.hasText(importFilePath)) {
            return ResponseEntity.badRequest().body(Map.of("error", "app.location.import.file-path 미설정"));
        }
        try {
            LocationImportService.SyncResult result = locationImportService.importFromFile(importFilePath);
            return ResponseEntity.ok(Map.of(
                    "total", result.getTotal(),
                    "saved", result.getSaved(),
                    "updated", result.getUpdated(),
                    "skipped", result.getSkipped()
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> importFacilities(
            @RequestPart("file") MultipartFile file) {
        try {
            LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "total", result.getTotal(),
                    "saved", result.getSaved(),
                    "updated", result.getUpdated(),
                    "skipped", result.getSkipped()
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }
}
