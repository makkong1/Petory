package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.recommendation.client.PetDataApiClient;
import com.linkup.Petory.domain.recommendation.dto.PetFacilityDto;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilitySyncService {

    private final PetDataApiClient petDataApiClient;
    private final LocationServiceRepository locationServiceRepository;
    private final LocationServiceBatchWriter batchWriter;

    @Value("${app.location.import.batch-size:500}")
    private int batchSize;

    private static final int PAGE_SIZE = 100;

    public SyncResult syncFromPetDataApi() {
        log.info("[FacilitySyncService] pet-data-api 동기화 시작");
        int saved = 0, duplicate = 0, skipped = 0;

        List<PetFacilityDto> facilities = petDataApiClient.fetchAllFacilities(PAGE_SIZE);
        int total = facilities.size();
        log.info("[FacilitySyncService] 수신 total={}", total);

        List<LocationService> batch = new ArrayList<>();
        for (PetFacilityDto dto : facilities) {
            try {
                if (!isValid(dto)) {
                    skipped++;
                    continue;
                }
                if (locationServiceRepository.existsByNameAndAddress(dto.getName(), dto.getAddress())) {
                    duplicate++;
                    continue;
                }
                batch.add(toEntity(dto));
                if (batch.size() >= batchSize) {
                    saved += batchWriter.saveBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.warn("[FacilitySyncService] 변환 실패 name={} err={}", dto.getName(), e.getMessage());
                skipped++;
            }
        }

        if (!batch.isEmpty()) {
            saved += batchWriter.saveBatch(batch);
        }

        log.info("[FacilitySyncService] 완료 total={} saved={} duplicate={} skipped={}",
                total, saved, duplicate, skipped);
        return new SyncResult(total, saved, duplicate, skipped);
    }

    private boolean isValid(PetFacilityDto dto) {
        if (!StringUtils.hasText(dto.getName())) return false;
        if (!StringUtils.hasText(dto.getAddress())) return false;
        if ("폐업".equals(dto.getStatus())) return false;
        return true;
    }

    private LocationService toEntity(PetFacilityDto dto) {
        return LocationService.builder()
                .name(dto.getName())
                .category1("반려동물 서비스")
                .category2("반려동물")
                .category3(categoryLabel(dto.getCategory(), dto.getType()))
                .sido(dto.getRegionCity())
                .sigungu(dto.getRegionDistrict())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .latitude(dto.getLat())
                .longitude(dto.getLng())
                .petFriendly(true)
                .dataSource("PET_DATA_API")
                .build();
    }

    private String categoryLabel(String category, String type) {
        if (StringUtils.hasText(category)) {
            return switch (category) {
                case "grooming" -> "동물미용";
                case "hospital" -> "동물병원";
                case "pharmacy" -> "동물약국";
                default -> category;
            };
        }
        return "HOSPITAL".equals(type) ? "동물병원" : "반려동물 시설";
    }

    @Data
    public static class SyncResult {
        private final int total;
        private final int saved;
        private final int duplicate;
        private final int skipped;
    }
}
