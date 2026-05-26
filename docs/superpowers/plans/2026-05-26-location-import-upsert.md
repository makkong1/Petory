# LocationImportService Source-Aware Upsert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `LocationImportService.processEntries()`를 duplicate-skip에서 source-aware upsert로 전환해, BATCH_IMPORT 소유 시설이 다시 들어오면 갱신되고 soft-deleted 시설은 재활성화되도록 한다.

**Architecture:** `name + address + dataSource="BATCH_IMPORT"` 3중 키로 기존 row를 조회한다. 존재하면 mutable 필드(phone/lat/lng/sido/sigungu/category3/lastUpdated)를 갱신하고, soft-deleted 상태면 복구한다. 없으면 신규 insert (lastUpdated=today 포함). PUBLIC 등 다른 dataSource row는 건드리지 않는다.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, JUnit 5 + Mockito, Lombok

---

## 코드베이스 핵심 사실

```
# Petory 레포 (/Users/maknkkong/project/Petory/)

backend/main/java/com/linkup/Petory/domain/location/
  repository/
    LocationServiceRepository.java                  ← 도메인 인터페이스 (메서드 추가)
    SpringDataJpaLocationServiceRepository.java     ← Spring Data JPA (JPQL 추가)
    JpaLocationServiceAdapter.java                  ← 위임 구현체 (위임 메서드 추가)
  service/
    LocationImportService.java                      ← processEntries() / SyncResult 교체
    FacilitySyncScheduler.java:30                   ← getDuplicate() → getUpdated() 로그 수정
  entity/
    LocationService.java                            ← @Getter @Setter @Builder (변경 없음)

backend/test/java/com/linkup/Petory/domain/location/service/
  LocationImportServiceTest.java                    ← 전체 교체 (duplicate 기반 → upsert 기반)
```

`LocationService` 엔티티: `@Getter @Setter` Lombok 있으므로 `setPhone()` 등 직접 사용 가능.
`SyncResult`: `@Data` Lombok — 생성자·getter 자동 생성.
`existsByNameAndAddress`는 repo에서 제거하지 않음 (호출처 불명, 보존).
Gradle 테스트 명령: `./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest"`

---

## Task 1: Repository 레이어 메서드 추가

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/location/repository/LocationServiceRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/repository/JpaLocationServiceAdapter.java`

- [ ] **Step 1: LocationServiceRepository 인터페이스에 메서드 추가**

`existsByNameAndAddress` 선언 바로 아래에 추가:

```java
/**
 * name + address + dataSource로 조회 — isDeleted 무관 (soft-deleted row 포함)
 */
Optional<LocationService> findByNameAndAddressAndDataSource(
        String name, String address, String dataSource);
```

파일 상단에 `import java.util.Optional;`이 없으면 추가.

- [ ] **Step 2: SpringDataJpaLocationServiceRepository에 JPQL 쿼리 추가**

`existsByNameAndAddress` 메서드 바로 아래에 추가:

```java
@RepositoryMethod("장소 서비스: name+address+dataSource 조회 (isDeleted 무관)")
@Query("SELECT ls FROM LocationService ls WHERE " +
        "ls.name = :name AND ls.address = :address AND ls.dataSource = :dataSource")
Optional<LocationService> findByNameAndAddressAndDataSource(
        @Param("name") String name,
        @Param("address") String address,
        @Param("dataSource") String dataSource);
```

파일 상단에 `import java.util.Optional;`이 없으면 추가.

- [ ] **Step 3: JpaLocationServiceAdapter에 위임 메서드 추가**

`existsByNameAndAddress` Override 바로 아래에 추가:

```java
@Override
public Optional<LocationService> findByNameAndAddressAndDataSource(
        String name, String address, String dataSource) {
    return jpaRepository.findByNameAndAddressAndDataSource(name, address, dataSource);
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/maknkkong/project/Petory
git add backend/main/java/com/linkup/Petory/domain/location/repository/LocationServiceRepository.java \
        backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java \
        backend/main/java/com/linkup/Petory/domain/location/repository/JpaLocationServiceAdapter.java
git commit -m "feat(location): findByNameAndAddressAndDataSource 메서드 추가"
```

---

## Task 2: LocationImportService upsert — TDD

**Files:**
- Modify: `backend/test/java/com/linkup/Petory/domain/location/service/LocationImportServiceTest.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncScheduler.java`

- [ ] **Step 1: 테스트 파일 전체 교체**

`LocationImportServiceTest.java` 전체를 아래 내용으로 교체:

```java
package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationImportServiceTest {

    @Mock  LocationServiceRepository locationServiceRepository;
    @Mock  LocationServiceBatchWriter batchWriter;
    @Spy   ObjectMapper objectMapper;

    @InjectMocks LocationImportService service;

    private ByteArrayInputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── 신규 insert ──────────────────────────────────────────────────────────

    @Test
    void 신규시설_insert_saved카운트증가() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any(List.class))).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
        assertThat(result.getSkipped()).isEqualTo(0);
    }

    @Test
    void 신규시설_insert시_lastUpdated가today() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.findByNameAndAddressAndDataSource(any(), any(), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<List<LocationService>> captor = ArgumentCaptor.forClass(List.class);
        when(batchWriter.saveBatch(captor.capture())).thenReturn(1);

        service.importFromStream(toStream(json));

        LocationService saved = captor.getValue().get(0);
        assertThat(saved.getLastUpdated()).isEqualTo(LocalDate.now());
    }

    // ── upsert (기존 BATCH_IMPORT row 갱신) ──────────────────────────────────

    @Test
    void 기존BATCH_IMPORT시설_upsert_updated카운트증가() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(126.0).isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getSaved()).isEqualTo(0);
    }

    @Test
    void 기존BATCH_IMPORT시설_upsert시_mutable필드갱신() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"hospital\",\"address\":\"서울 강남구\"," +
                "\"lat\":38.0,\"lng\":128.0,\"phone\":\"02-9999-9999\",\"sido\":\"서울\",\"sigungu\":\"강남구\"}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0).phone("010-0000-0000")
                .category3("미용").sido("경기").sigungu("성남시").isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);

        service.importFromStream(toStream(json));

        assertThat(existing.getPhone()).isEqualTo("02-9999-9999");
        assertThat(existing.getLatitude()).isEqualTo(38.0);
        assertThat(existing.getLongitude()).isEqualTo(128.0);
        assertThat(existing.getSido()).isEqualTo("서울");
        assertThat(existing.getSigungu()).isEqualTo("강남구");
        assertThat(existing.getCategory3()).isEqualTo("동물병원");
        assertThat(existing.getLastUpdated()).isEqualTo(LocalDate.now());
    }

    @Test
    void 기존BATCH_IMPORT시설_upsert시_rating_reviewCount_score_보존() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService existing = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0)
                .rating(4.8).reviewCount(42).score(0.95).isDeleted(false).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(existing));
        when(locationServiceRepository.save(existing)).thenReturn(existing);

        service.importFromStream(toStream(json));

        assertThat(existing.getRating()).isEqualTo(4.8);
        assertThat(existing.getReviewCount()).isEqualTo(42);
        assertThat(existing.getScore()).isEqualTo(0.95);
    }

    // ── soft-deleted row 재활성화 ─────────────────────────────────────────────

    @Test
    void softDeleted_BATCH_IMPORT시설_재활성화() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        LocationService softDeleted = LocationService.builder()
                .name("멍멍미용").address("서울 강남구").dataSource("BATCH_IMPORT")
                .latitude(37.0).longitude(127.0).isDeleted(true).build();
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.of(softDeleted));
        when(locationServiceRepository.save(softDeleted)).thenReturn(softDeleted);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(softDeleted.getIsDeleted()).isFalse();
        assertThat(softDeleted.getDeletedAt()).isNull();
    }

    // ── PUBLIC row 격리 ──────────────────────────────────────────────────────

    @Test
    void PUBLIC_row_동일name_address_BATCH_IMPORT없으면_신규insert() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        // PUBLIC row 존재해도 BATCH_IMPORT 조회는 empty
        when(locationServiceRepository.findByNameAndAddressAndDataSource("멍멍미용", "서울 강남구", "BATCH_IMPORT"))
                .thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any(List.class))).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getUpdated()).isEqualTo(0);
    }

    // ── isValid 실패 ─────────────────────────────────────────────────────────

    @Test
    void 빈이름_skipped() throws IOException {
        String json = "[{\"name\":\"\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 빈주소_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"\",\"lat\":37.5,\"lng\":127.0}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void latLng없으면_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\"}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 폐업status_skipped() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0,\"status\":\"폐업\"}]";
        assertThat(service.importFromStream(toStream(json)).getSkipped()).isEqualTo(1);
    }

    @Test
    void 빈배열_모두0() throws IOException {
        LocationImportService.SyncResult result = service.importFromStream(toStream("[]"));
        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getSaved()).isEqualTo(0);
        assertThat(result.getUpdated()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest" 2>&1 | tail -20
```

Expected: 컴파일 에러 또는 여러 테스트 실패 (`getUpdated()` 미존재, mock 불일치 등)

- [ ] **Step 3: LocationImportService 전체 교체**

`LocationImportService.java` 전체를 아래로 교체:

```java
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        int total = dtos.size(), saved = 0, updated = 0, skipped = 0;
        List<LocationService> batch = new ArrayList<>();

        for (LocationImportDto dto : dtos) {
            try {
                if (!isValid(dto)) { skipped++; continue; }

                Optional<LocationService> existing = locationServiceRepository
                        .findByNameAndAddressAndDataSource(dto.getName(), dto.getAddress(), "BATCH_IMPORT");

                if (existing.isPresent()) {
                    LocationService entity = existing.get();
                    entity.setPhone(dto.getPhone());
                    entity.setLatitude(dto.getLat());
                    entity.setLongitude(dto.getLng());
                    entity.setSido(dto.getSido());
                    entity.setSigungu(dto.getSigungu());
                    entity.setCategory3(categoryLabel(dto.getCategory()));
                    entity.setLastUpdated(LocalDate.now());
                    if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                        entity.setIsDeleted(false);
                        entity.setDeletedAt(null);
                    }
                    locationServiceRepository.save(entity);
                    updated++;
                } else {
                    batch.add(toEntity(dto));
                    if (batch.size() >= batchSize) {
                        saved += batchWriter.saveBatch(batch);
                        batch.clear();
                    }
                }
            } catch (Exception e) {
                log.warn("[LocationImportService] 변환 실패 name={}", dto.getName(), e);
                skipped++;
            }
        }
        if (!batch.isEmpty()) saved += batchWriter.saveBatch(batch);

        log.info("[LocationImportService] 완료 total={} saved={} updated={} skipped={}",
                total, saved, updated, skipped);
        return new SyncResult(total, saved, updated, skipped);
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
                .lastUpdated(LocalDate.now())
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
        private final int updated;
        private final int skipped;
    }
}
```

- [ ] **Step 4: FacilitySyncScheduler 로그 수정**

`FacilitySyncScheduler.java:29-30` 의 로그 라인을 교체:

```java
// 변경 전
log.info("[FacilitySyncScheduler] 완료 total={} saved={} duplicate={} skipped={}",
        result.getTotal(), result.getSaved(), result.getDuplicate(), result.getSkipped());

// 변경 후
log.info("[FacilitySyncScheduler] 완료 total={} saved={} updated={} skipped={}",
        result.getTotal(), result.getSaved(), result.getUpdated(), result.getSkipped());
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 모든 테스트 PASSED

- [ ] **Step 6: 전체 테스트 회귀 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`
실패 시: 에러 메시지에 `getDuplicate()` 등 SyncResult 관련 컴파일 에러가 있으면 해당 파일에서 동일하게 `getUpdated()`로 교체.

- [ ] **Step 7: 커밋**

```bash
cd /Users/maknkkong/project/Petory
git add backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java \
        backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncScheduler.java \
        backend/test/java/com/linkup/Petory/domain/location/service/LocationImportServiceTest.java
git commit -m "feat(location): LocationImportService source-aware upsert 전환"
```
