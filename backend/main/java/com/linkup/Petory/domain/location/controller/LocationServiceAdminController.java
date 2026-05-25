package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.service.LocationImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final LocationImportService locationImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> importFacilities(
            @RequestPart("file") MultipartFile file) {
        try {
            LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "total", result.getTotal(),
                    "saved", result.getSaved(),
                    "duplicate", result.getDuplicate(),
                    "skipped", result.getSkipped()
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }
}
