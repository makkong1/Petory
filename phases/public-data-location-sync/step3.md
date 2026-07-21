# Step 3: PublicDataSyncService + BatchWriter upsert 확장

## 목표
`PublicDataApiClient.fetchAll()`로 받은 시설을 `location_service`에 **멱등 upsert**한다:
- 시설명+주소로 기존 행 조회 → 없으면 INSERT / 있고 내용이 바뀌었으면 UPDATE / 동일하면 skip.
- 실행 결과를 `location_sync_log`에 1행 기록한다.

## 배경 및 설계 결정
- **앱 관리 필드 보존(중요)**: `location_service`에는 공공데이터가 주지 않는 앱 관리 컬럼(`rating`, `reviewCount`,
  `isDeleted`, `geo_point`, `createdAt` 등)이 있다. UPDATE 시 신규 엔티티를 만들어 `idx`만 세팅해 `saveAll`하면
  이 컬럼들이 기본값으로 **덮어써진다**(별점·리뷰수 소실). 따라서 UPDATE는 **기존 엔티티를 로드해 공공데이터 필드만
  복사**하고 나머지는 건드리지 않는다.
- **변환 로직 재사용**: DTO→엔티티 변환·유효성·중복키는 이미 `PublicDataLocationService`(CSV 경로)에 검증된 코드가 있다.
  중복 구현 대신 이 메서드들의 접근제어자를 `private` → **package-private**로 낮춰 같은 패키지의 `PublicDataSyncService`가
  재사용한다. CSV 경로의 세션 처리 로직은 건드리지 않는다(외과적 변경).
- **실패 격리**: API 전체 호출 실패 → `FAILED`. 개별 배치/행 저장 실패가 일부 있으면 `PARTIAL`, 전부 성공하면 `SUCCESS`.
- 조회는 CSV 경로와 동일하게 행 단위(`findFirstByNameAndAddress`)로 한다. 7만 행 기준 조회 쿼리가 많지만 배치 잡이라 허용,
  전량 프리로드 최적화는 후속 과제로 남긴다.

## 변경 파일

### 1. `SpringDataJpaLocationServiceRepository.java` (기존, 메서드 추가)
`existsByNameAndAddress` 바로 아래에 추가. (기존 `@RepositoryMethod`+`@Query` 패턴 그대로)

```java
    @RepositoryMethod("장소 서비스: 이름+주소로 조회(업서트용)")
    @Query("SELECT ls FROM LocationService ls WHERE "
            + "ls.name = :name AND ls.address = :address AND "
            + "ls.isDeleted = false")
    java.util.List<LocationService> findByNameAndAddress(@Param("name") String name, @Param("address") String address);
```

### 2. `LocationServiceRepository.java` (기존 도메인 인터페이스, 메서드 추가)
`existsByNameAndAddress` 선언 아래에 추가:

```java
    /**
     * 이름과 주소로 첫 번째 미삭제 서비스 조회 (업서트용)
     */
    java.util.Optional<LocationService> findFirstByNameAndAddress(String name, String address);
```

### 3. `JpaLocationServiceAdapter.java` (기존 어댑터, 메서드 추가)
`existsByNameAndAddress` 구현 아래에 추가:

```java
    @Override
    public java.util.Optional<LocationService> findFirstByNameAndAddress(String name, String address) {
        return jpaRepository.findByNameAndAddress(name, address).stream().findFirst();
    }
```

### 4. `LocationServiceBatchWriter.java` (기존, 메서드 추가)
`saveBatch` 아래에 UPDATE 전용 배치 메서드 추가. `saveBatch`와 달리 **idx를 초기화하지 않는다**(업데이트라서).

```java
    /**
     * 업데이트 배치 저장 (각 배치 별도 트랜잭션). idx가 세팅된 detached 엔티티를 merge 한다.
     * 일부 실패해도 나머지는 저장됨.
     *
     * @param batch 업데이트할(기존 idx 보유) 엔티티 목록
     * @return 실제 저장된 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int updateBatch(List<LocationService> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            locationServiceRepository.saveAll(batch);
            return batch.size();
        } catch (DataAccessException e) {
            log.error("업데이트 배치 실패: {}개 중 일부 실패 - {}", batch.size(), e.getMessage(), e);
            entityManager.clear();
            int saved = 0;
            for (LocationService entity : batch) {
                try {
                    locationServiceRepository.save(entity);
                    saved++;
                } catch (DataAccessException ex) {
                    log.warn("개별 업데이트 실패: idx={}, {}", entity.getIdx(), ex.getMessage());
                    entityManager.clear();
                }
            }
            return saved;
        }
    }
```

### 5. `PublicDataLocationService.java` (기존, 접근제어자만 변경)
아래 3개 메서드의 `private` 를 제거해 package-private 으로 바꾼다(같은 패키지 재사용). **본문은 그대로.**

- `private boolean isValid(PublicDataLocationDTO dto)` → `boolean isValid(PublicDataLocationDTO dto)`
- `private String buildDedupKey(PublicDataLocationDTO dto)` → `String buildDedupKey(PublicDataLocationDTO dto)`
- `private LocationService convertToEntity(PublicDataLocationDTO dto)` → `LocationService convertToEntity(PublicDataLocationDTO dto)`

### 6. `PublicDataSyncService.java` (신규)

```java
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
```

## 테스트

### `backend/test/java/com/linkup/Petory/domain/location/service/PublicDataSyncServiceTest.java` (신규)
Mockito로 협력자를 목킹해 5개 시나리오를 검증한다.

```java
package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationSyncLogRepository;

@ExtendWith(MockitoExtension.class)
class PublicDataSyncServiceTest {

    @Mock PublicDataApiClient apiClient;
    @Mock PublicDataLocationService conversion;
    @Mock LocationServiceRepository locationServiceRepository;
    @Mock LocationServiceBatchWriter batchWriter;
    @Mock LocationSyncLogRepository syncLogRepository;
    @InjectMocks PublicDataSyncService service;

    private PublicDataLocationDTO dto(String name, String addr) {
        PublicDataLocationDTO d = new PublicDataLocationDTO();
        d.setFacilityName(name);
        d.setRoadAddress(addr);
        return d;
    }

    private LocationService entity(String name, String addr, String phone) {
        return LocationService.builder().name(name).address(addr).phone(phone).build();
    }

    private void stubValidConversion(PublicDataLocationDTO dto, LocationService entity) {
        when(conversion.isValid(dto)).thenReturn(true);
        when(conversion.buildDedupKey(dto)).thenReturn(dto.getFacilityName() + "|" + dto.getRoadAddress());
        when(conversion.convertToEntity(dto)).thenReturn(entity);
    }

    @Test
    void 신규는_insertBatch로_저장된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService e = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, e);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any())).thenReturn(1);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getInserted()).isEqualTo(1);
        assertThat(log.getUpdated()).isZero();
        assertThat(log.getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void 내용이_바뀌면_updateBatch로_갱신된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService incoming = entity("병원A", "서울 강남", "010-NEW");
        LocationService existing = entity("병원A", "서울 강남", "010-OLD");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, incoming);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.of(existing));
        when(batchWriter.updateBatch(any())).thenReturn(1);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getUpdated()).isEqualTo(1);
        assertThat(existing.getPhone()).isEqualTo("010-NEW"); // 공공필드 복사됨
    }

    @Test
    void 내용이_동일하면_skip된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService incoming = entity("병원A", "서울 강남", "010");
        LocationService existing = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, incoming);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.of(existing));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getSkipped()).isEqualTo(1);
        assertThat(log.getInserted()).isZero();
        assertThat(log.getUpdated()).isZero();
    }

    @Test
    void API_조회_실패시_FAILED로_기록한다() {
        when(apiClient.fetchAll()).thenThrow(new RuntimeException("timeout"));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.SCHEDULED);

        assertThat(log.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("timeout");
    }

    @Test
    void 일부_배치_실패시_PARTIAL로_기록한다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService e = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, e);
        when(locationServiceRepository.findFirstByNameAndAddress(anyString(), anyString())).thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any())).thenReturn(0); // 1건 중 0건 저장 → 1 실패
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getFailed()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(SyncStatus.PARTIAL);
    }
}
```

## Acceptance Criteria

- [ ] `./gradlew compileJava` 성공.
- [ ] `./gradlew test --tests "*PublicDataSyncServiceTest"` — 5개 시나리오 모두 PASS.
- [ ] `./gradlew test --tests "*PublicDataApiClientTest"` 회귀 PASS(Step 2 유지).

## 커밋

```bash
git add backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java \
        backend/main/java/com/linkup/Petory/domain/location/repository/LocationServiceRepository.java \
        backend/main/java/com/linkup/Petory/domain/location/repository/JpaLocationServiceAdapter.java \
        backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceBatchWriter.java \
        backend/main/java/com/linkup/Petory/domain/location/service/PublicDataLocationService.java \
        backend/main/java/com/linkup/Petory/domain/location/service/PublicDataSyncService.java \
        backend/test/java/com/linkup/Petory/domain/location/service/PublicDataSyncServiceTest.java
git commit -m "feat(location): 공공데이터 멱등 upsert 동기화 서비스 및 실행이력 기록 추가"
```
