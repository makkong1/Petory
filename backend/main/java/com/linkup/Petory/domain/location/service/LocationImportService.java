package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.dto.LocationImportDto;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationImportService {

    private final LocationServiceRepository locationServiceRepository;
    private final LocationServiceBatchWriter batchWriter;
    private final ObjectMapper objectMapper;

    @Value("${app.location.import.batch-size:500}")
    private int batchSize;

    public SyncResult importFromStream(InputStream in) throws IOException {
        List<LocationImportDto> dtos = objectMapper.readValue(
                in, new TypeReference<List<LocationImportDto>>() {});
        return processEntries(dtos);
    }

    public SyncResult importFromFile(String filePath) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(filePath))) {
            return importFromStream(in);
        }
    }

    private SyncResult processEntries(List<LocationImportDto> dtos) {
        int total = dtos.size(), saved = 0, duplicate = 0, skipped = 0;
        List<LocationService> batch = new ArrayList<>();

        for (LocationImportDto dto : dtos) {
            try {
                if (!isValid(dto)) { skipped++; continue; }
                if (locationServiceRepository.existsByNameAndAddress(dto.getName(), dto.getAddress())) {
                    duplicate++; continue;
                }
                batch.add(toEntity(dto));
                if (batch.size() >= batchSize) {
                    saved += batchWriter.saveBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.warn("[LocationImportService] 변환 실패 name={} err={}", dto.getName(), e.getMessage());
                skipped++;
            }
        }
        if (!batch.isEmpty()) saved += batchWriter.saveBatch(batch);

        log.info("[LocationImportService] 완료 total={} saved={} duplicate={} skipped={}",
                total, saved, duplicate, skipped);
        return new SyncResult(total, saved, duplicate, skipped);
    }

    private boolean isValid(LocationImportDto dto) {
        if (!StringUtils.hasText(dto.getName())) return false;
        if (!StringUtils.hasText(dto.getAddress())) return false;
        if (dto.getLat() == null || dto.getLng() == null) return false;
        return !"폐업".equals(dto.getStatus());
    }

    private LocationService toEntity(LocationImportDto dto) {
        return LocationService.builder()
                .name(dto.getName())
                .category1("반려동물 서비스")
                .category2("반려동물")
                .category3(categoryLabel(dto.getCategory()))
                .sido(dto.getSido())
                .sigungu(dto.getSigungu())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .latitude(dto.getLat())
                .longitude(dto.getLng())
                .petFriendly(true)
                .dataSource("BATCH_IMPORT")
                .build();
    }

    private String categoryLabel(String category) {
        if (!StringUtils.hasText(category)) return "반려동물 시설";
        return switch (category) {
            case "grooming"    -> "미용";
            case "hospital"    -> "동물병원";
            case "pharmacy"    -> "동물약국";
            case "cafe"        -> "카페";
            case "restaurant"  -> "식당";
            case "pension"     -> "펜션";
            case "boarding"    -> "위탁관리";
            case "hotel"       -> "호텔";
            case "supplies"    -> "반려동물용품";
            default            -> category;
        };
    }

    @Data
    public static class SyncResult {
        private final int total;
        private final int saved;
        private final int duplicate;
        private final int skipped;
    }
}
