package com.linkup.Petory.domain.location.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.dto.LocationImportDto;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final LocationImportService locationImportService;
    private final LocationServiceRepository locationServiceRepository;
    private final ObjectMapper objectMapper;

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

            List<LocationService> batchRecords = locationServiceRepository.findByDataSource("BATCH_IMPORT", 200);
            List<Map<String, Object>> records = batchRecords.stream().map(r -> {
                Map<String, Object> m = new HashMap<>();
                m.put("idx",         r.getIdx());
                m.put("name",        r.getName());
                m.put("category",    r.getCategory3() != null ? r.getCategory3() : "");
                m.put("address",     r.getAddress() != null ? r.getAddress() : "");
                m.put("sido",        r.getSido() != null ? r.getSido() : "");
                m.put("sigungu",     r.getSigungu() != null ? r.getSigungu() : "");
                m.put("phone",       r.getPhone() != null ? r.getPhone() : "");
                m.put("lastUpdated", r.getLastUpdated() != null ? r.getLastUpdated().toString() : "");
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("total",   result.getTotal());
            response.put("saved",   result.getSaved());
            response.put("updated", result.getUpdated());
            response.put("skipped", result.getSkipped());
            response.put("records", records);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/json-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> jsonPreview() {
        if (!StringUtils.hasText(importFilePath)) {
            return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 경로 미설정"));
        }
        Path path = Path.of(importFilePath);
        if (!Files.exists(path)) {
            return ResponseEntity.ok(Map.of("exists", false, "reason", "파일 없음: " + importFilePath));
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            List<LocationImportDto> records = objectMapper.readValue(content, new TypeReference<>() {});
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
            Map<String, Object> response = new HashMap<>();
            response.put("exists",       true);
            response.put("lastModified", lastModified.toString());
            response.put("count",        records.size());
            response.put("records",      records);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> importFacilities(
            @RequestPart("file") MultipartFile file) {
        try {
            LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "total",   result.getTotal(),
                    "saved",   result.getSaved(),
                    "updated", result.getUpdated(),
                    "skipped", result.getSkipped()
            ));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 읽기 실패: " + e.getMessage()));
        }
    }
}
