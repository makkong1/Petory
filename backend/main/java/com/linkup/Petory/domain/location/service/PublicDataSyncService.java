package com.linkup.Petory.domain.location.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationSyncLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터 시설을 location_service 에 멱등 upsert 하는 동기화 서비스.
 * 배치 저장은 LocationServiceBatchWriter(REQUIRES_NEW)에 위임하고, 결과를 location_sync_log 에 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncService {

    private static final int BATCH_SIZE = 1000;

    private final PublicDataApiClient apiClient;
    private final PublicDataLocationService conversion; // convertToEntity/isValid/buildDedupKey 재사용
    private final LocationServiceRepository locationServiceRepository;
    private final LocationServiceBatchWriter batchWriter;
    private final LocationSyncLogRepository syncLogRepository;

    /**
     * 공공데이터 API에서 전체 시설을 받아 upsert 하고, 실행 이력을 저장해 반환한다.
     */
    public LocationSyncLog syncFromApi(SyncTriggerType triggerType) {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("공공데이터 동기화 시작: trigger={}", triggerType);

        List<PublicDataLocationDTO> items;
        try {
            items = apiClient.fetchAll();
        } catch (Exception e) {
            log.error("공공데이터 API 조회 실패: {}", e.getMessage(), e);
            return syncLogRepository.save(LocationSyncLog.builder()
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .status(SyncStatus.FAILED)
                    .triggerType(triggerType)
                    .errorMessage(truncate(e.getMessage()))
                    .build());
        }

        int inserted = 0, updated = 0, skipped = 0, failed = 0;
        Set<String> seen = new HashSet<>();
        List<LocationService> insertBatch = new ArrayList<>();
        List<LocationService> updateBatch = new ArrayList<>();

        for (PublicDataLocationDTO dto : items) {
            if (!conversion.isValid(dto)) {
                skipped++;
                continue;
            }
            String dedupKey = conversion.buildDedupKey(dto);
            if (!seen.add(dedupKey)) {
                skipped++; // 같은 실행 안 중복 행
                continue;
            }

            LocationService incoming;
            try {
                incoming = conversion.convertToEntity(dto);
                if (incoming == null) {
                    skipped++;
                    continue;
                }
            } catch (Exception e) {
                failed++;
                log.warn("엔티티 변환 실패: name={}, {}", dto.getFacilityName(), e.getMessage());
                continue;
            }

            String address = incoming.getAddress();
            Optional<LocationService> existingOpt = (address == null) ? Optional.empty()
                    : locationServiceRepository.findFirstByNameAndAddress(incoming.getName(), address);

            if (existingOpt.isEmpty()) {
                insertBatch.add(incoming);
            } else {
                LocationService existing = existingOpt.get();
                if (isSamePublicData(existing, incoming)) {
                    skipped++;
                } else {
                    copyPublicFields(incoming, existing);
                    updateBatch.add(existing);
                }
            }

            if (insertBatch.size() >= BATCH_SIZE) {
                int saved = batchWriter.saveBatch(insertBatch);
                inserted += saved;
                failed += (insertBatch.size() - saved);
                insertBatch.clear();
            }
            if (updateBatch.size() >= BATCH_SIZE) {
                int saved = batchWriter.updateBatch(updateBatch);
                updated += saved;
                failed += (updateBatch.size() - saved);
                updateBatch.clear();
            }
        }

        if (!insertBatch.isEmpty()) {
            int saved = batchWriter.saveBatch(insertBatch);
            inserted += saved;
            failed += (insertBatch.size() - saved);
        }
        if (!updateBatch.isEmpty()) {
            int saved = batchWriter.updateBatch(updateBatch);
            updated += saved;
            failed += (updateBatch.size() - saved);
        }

        SyncStatus status = (failed > 0) ? SyncStatus.PARTIAL : SyncStatus.SUCCESS;
        log.info("공공데이터 동기화 완료: 조회={}, 신규={}, 갱신={}, 스킵={}, 실패={}, status={}",
                items.size(), inserted, updated, skipped, failed, status);

        return syncLogRepository.save(LocationSyncLog.builder()
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(status)
                .totalFetched(items.size())
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .failed(failed)
                .triggerType(triggerType)
                .build());
    }

    /** 공공데이터가 채우는 필드만 비교. 앱 관리 필드(rating/reviewCount/isDeleted 등)는 비교 대상 아님. */
    private boolean isSamePublicData(LocationService a, LocationService b) {
        return Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getCategory1(), b.getCategory1())
                && Objects.equals(a.getCategory2(), b.getCategory2())
                && Objects.equals(a.getCategory3(), b.getCategory3())
                && Objects.equals(a.getSido(), b.getSido())
                && Objects.equals(a.getSigungu(), b.getSigungu())
                && Objects.equals(a.getEupmyeondong(), b.getEupmyeondong())
                && Objects.equals(a.getRoadName(), b.getRoadName())
                && Objects.equals(a.getAddress(), b.getAddress())
                && Objects.equals(a.getZipCode(), b.getZipCode())
                && Objects.equals(a.getLatitude(), b.getLatitude())
                && Objects.equals(a.getLongitude(), b.getLongitude())
                && Objects.equals(a.getPhone(), b.getPhone())
                && Objects.equals(a.getWebsite(), b.getWebsite())
                && Objects.equals(a.getClosedDay(), b.getClosedDay())
                && Objects.equals(a.getOperatingHours(), b.getOperatingHours())
                && Objects.equals(a.getParkingAvailable(), b.getParkingAvailable())
                && Objects.equals(a.getPriceInfo(), b.getPriceInfo())
                && Objects.equals(a.getPetFriendly(), b.getPetFriendly())
                && Objects.equals(a.getIsPetOnly(), b.getIsPetOnly())
                && Objects.equals(a.getPetSize(), b.getPetSize())
                && Objects.equals(a.getPetRestrictions(), b.getPetRestrictions())
                && Objects.equals(a.getPetExtraFee(), b.getPetExtraFee())
                && Objects.equals(a.getIndoor(), b.getIndoor())
                && Objects.equals(a.getOutdoor(), b.getOutdoor())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getLastUpdated(), b.getLastUpdated());
    }

    /** incoming(신규 변환 엔티티)의 공공데이터 필드만 existing(기존 관리 엔티티)에 복사. */
    private void copyPublicFields(LocationService incoming, LocationService existing) {
        existing.setName(incoming.getName());
        existing.setCategory1(incoming.getCategory1());
        existing.setCategory2(incoming.getCategory2());
        existing.setCategory3(incoming.getCategory3());
        existing.setSido(incoming.getSido());
        existing.setSigungu(incoming.getSigungu());
        existing.setEupmyeondong(incoming.getEupmyeondong());
        existing.setRoadName(incoming.getRoadName());
        existing.setAddress(incoming.getAddress());
        existing.setZipCode(incoming.getZipCode());
        existing.setLatitude(incoming.getLatitude());
        existing.setLongitude(incoming.getLongitude());
        existing.setPhone(incoming.getPhone());
        existing.setWebsite(incoming.getWebsite());
        existing.setClosedDay(incoming.getClosedDay());
        existing.setOperatingHours(incoming.getOperatingHours());
        existing.setParkingAvailable(incoming.getParkingAvailable());
        existing.setPriceInfo(incoming.getPriceInfo());
        existing.setPetFriendly(incoming.getPetFriendly());
        existing.setIsPetOnly(incoming.getIsPetOnly());
        existing.setPetSize(incoming.getPetSize());
        existing.setPetRestrictions(incoming.getPetRestrictions());
        existing.setPetExtraFee(incoming.getPetExtraFee());
        existing.setIndoor(incoming.getIndoor());
        existing.setOutdoor(incoming.getOutdoor());
        existing.setDescription(incoming.getDescription());
        existing.setLastUpdated(incoming.getLastUpdated());
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
