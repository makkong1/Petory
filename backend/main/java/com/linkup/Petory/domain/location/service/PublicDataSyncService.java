package com.linkup.Petory.domain.location.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncSource;
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
     * 공공데이터(odcloud 동반가능 문화시설) API에서 전체 시설을 받아 upsert 하고, 실행 이력을 저장해 반환한다.
     */
    public LocationSyncLog syncFromApi(SyncTriggerType triggerType) {
        return sync(SyncSource.CULTURE_FACILITY, triggerType, apiClient::fetchAll, (dto, entity) -> {
        }, true);
    }

    /**
     * 데이터셋 하나를 받아 upsert 하고 실행 이력을 남긴다.
     *
     * @param source    어느 데이터셋인가 (이력에 기록된다)
     * @param fetch     전체 행을 가져오는 호출
     * @param onInsert  <b>신규로 적재되는 행에만</b> 적용할 보정. 출처별 추가 필드가 장소당 1회
     *                  외부 호출을 요구할 수 있어(TourAPI 상세) 이미 있는 행에는 부르지 않는다.
     * @param updateExisting 이미 있는 장소를 이 데이터셋 값으로 덮어쓸 것인가.
     *        🔴 <b>칸이 적은 데이터셋은 false 여야 한다.</b> {@link #copyPublicFields} 는 공공데이터
     *        필드 전체를 통째로 옮기므로, 원본에 없는 칸이 null 로 덮인다. 2026-10-24 TourAPI
     *        시험 적재(26건)에서 겹친 4건이 실제로 이렇게 망가졌다 — 「제주민속촌」의 홈페이지·
     *        운영시간·입장료·입장가능크기가 지워지고 소분류가 「여행지」에서 「문화시설」로 바뀌었다.
     *        원본(odcloud)에는 그 값들이 그대로 있었다. 즉 더 빈약한 출처가 더 나은 행을 덮었다.
     */
    public LocationSyncLog sync(SyncSource source, SyncTriggerType triggerType,
            Supplier<List<PublicDataLocationDTO>> fetch,
            BiConsumer<PublicDataLocationDTO, LocationService> onInsert,
            boolean updateExisting) {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("위치 동기화 시작: source={}, trigger={}", source, triggerType);

        List<PublicDataLocationDTO> items;
        try {
            items = fetch.get();
        } catch (Exception e) {
            log.error("{} API 조회 실패: {}", source, e.getMessage(), e);
            return syncLogRepository.save(LocationSyncLog.builder()
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .status(SyncStatus.FAILED)
                    .triggerType(triggerType)
                    .source(source)
                    .errorMessage(truncate(e.getMessage()))
                    .build());
        }

        int inserted = 0, updated = 0, failed = 0;
        // skip 은 사유별로 센다. 합쳐 놓으면 이력만 보고 "멱등하게 잘 돌았다"와
        // "원본·매핑이 깨져 전량 거부됐다"를 구분할 수 없다.
        int skippedInvalid = 0, skippedDuplicate = 0, skippedUnchanged = 0;
        Set<String> seen = new HashSet<>();
        List<LocationService> insertBatch = new ArrayList<>();
        List<LocationService> updateBatch = new ArrayList<>();

        for (PublicDataLocationDTO dto : items) {
            if (!conversion.isValid(dto)) {
                skippedInvalid++;
                continue;
            }
            String dedupKey = conversion.buildDedupKey(dto);
            if (!seen.add(dedupKey)) {
                skippedDuplicate++; // 같은 실행 안 중복 행
                continue;
            }

            LocationService incoming;
            try {
                incoming = conversion.convertToEntity(dto);
                if (incoming == null) {
                    // 변환 결과가 비는 것도 "쓸 수 없는 행"이라 invalid 로 묶는다.
                    skippedInvalid++;
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
                onInsert.accept(dto, incoming);
                insertBatch.add(incoming);
            } else {
                LocationService existing = existingOpt.get();
                if (!updateExisting) {
                    // 이미 아는 장소다. 이 데이터셋은 덮어쓸 자격이 없으니 그냥 둔다.
                    // 멱등 정상 동작이므로 「이미 있고 같다」와 같은 칸에 센다.
                    skippedUnchanged++;
                } else if (isSamePublicData(existing, incoming)) {
                    skippedUnchanged++;
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

        int skipped = skippedInvalid + skippedDuplicate + skippedUnchanged;
        SyncStatus status = (failed > 0) ? SyncStatus.PARTIAL : SyncStatus.SUCCESS;
        log.info("위치 동기화 완료: source={}, 조회={}, 신규={}, 갱신={}, 스킵={}(무효={}, 중복={}, 동일={}), 실패={}, status={}",
                source, items.size(), inserted, updated, skipped,
                skippedInvalid, skippedDuplicate, skippedUnchanged, failed, status);

        return syncLogRepository.save(LocationSyncLog.builder()
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .status(status)
                .totalFetched(items.size())
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .skippedInvalid(skippedInvalid)
                .skippedDuplicate(skippedDuplicate)
                .skippedUnchanged(skippedUnchanged)
                .failed(failed)
                .triggerType(triggerType)
                .source(source)
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

    /**
     * incoming(신규 변환 엔티티)의 공공데이터 필드만 existing(기존 관리 엔티티)에 복사.
     *
     * <p>
     * ⚠️ {@code petZoneType} 은 여기에 넣지 말 것 — onInsert 로 신규 행에만 채우는 값이라
     * 갱신 경로의 incoming 은 항상 null 이고, 복사하면 이미 있던 값을 지운다.
     */
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
