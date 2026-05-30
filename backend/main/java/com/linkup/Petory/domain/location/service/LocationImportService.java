package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.converter.LocationImportConverter;
import com.linkup.Petory.domain.location.dto.LocationImportDto;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationImportService {

    private final LocationServiceRepository locationServiceRepository;
    private final LocationImportConverter locationImportConverter;
    private final ObjectMapper objectMapper;

    // InputStream으로 전달된 JSON을 파싱해 DB 적재 (CSV 업로드 경로에서 호출)
    public SyncResult importFromStream(InputStream in) throws IOException {
        List<LocationImportDto> dtos = objectMapper.readValue(
                in, new TypeReference<List<LocationImportDto>>() {});
        return processEntries(dtos);
    }

    // 설정된 파일 경로에서 읽어 importFromStream에 위임 (배치 스케줄러·관리자 수동 동기화에서 호출)
    public SyncResult importFromFile(String filePath) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(filePath))) {
            return importFromStream(in);
        }
    }

    // DTO 목록을 순회하며 기존(UPDATE) 처리. 신규는 place_candidates로만 적재.
    private SyncResult processEntries(List<LocationImportDto> dtos) {
        int total = dtos.size(), saved = 0, updated = 0, skipped = 0;

        for (LocationImportDto dto : dtos) {
            try {
                if (!isValid(dto)) { skipped++; continue; }

                Optional<LocationService> existing = locationServiceRepository
                        .findByAddressAndDataSource(dto.getAddress(), "BATCH_IMPORT");

                if (existing.isPresent()) {
                    LocationService entity = existing.get();
                    entity.setName(dto.getName()); // 이름 변경 시 최신값으로 갱신
                    entity.setPhone(dto.getPhone());
                    entity.setLatitude(dto.getLat());
                    entity.setLongitude(dto.getLng());
                    entity.setSido(dto.getSido());
                    entity.setSigungu(dto.getSigungu());
                    entity.setCategory3(locationImportConverter.categoryLabel(dto.getCategory()));
                    entity.setLastUpdated(LocalDate.now());
                    if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                        entity.setIsDeleted(false);
                        entity.setDeletedAt(null);
                    }
                    locationServiceRepository.save(entity);
                    updated++;
                } else {
                    // [WRITE GUARD] 신규 장소 후보는 place_candidates로만 적재.
                    // pet-data-api는 POST /api/admin/place-candidates/batch-ingest 사용.
                    log.warn("[LocationImportService] 신규 장소 INSERT 차단 name={} address={} — place_candidates 사용",
                        dto.getName(), dto.getAddress());
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("[LocationImportService] 변환 실패 name={}", dto.getName(), e);
                skipped++;
            }
        }

        log.info("[LocationImportService] 완료 total={} saved={} updated={} skipped={}",
                total, saved, updated, skipped);
        return new SyncResult(total, saved, updated, skipped);
    }

    // 필수 필드(name·address·lat·lng) 존재 여부 및 폐업 상태 검증
    private boolean isValid(LocationImportDto dto) {
        if (!StringUtils.hasText(dto.getName())) return false;
        if (!StringUtils.hasText(dto.getAddress())) return false;
        if (dto.getLat() == null || dto.getLng() == null) return false;
        return !"폐업".equals(dto.getStatus());
    }

    @Data
    public static class SyncResult {
        private final int total;
        private final int saved;
        private final int updated;
        private final int skipped;
    }
}
