# Location File Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring이 JSON 파일을 읽어 locationservice DB에 upsert하는 import 기능을 만들고, FacilitySyncService의 pet-data-api 의존을 완전히 제거한다.

**Architecture:** Python batch가 출력한 JSON 파일을 Spring `LocationImportService`가 읽어 validate → dedup → batch upsert 한다. `FacilitySyncService`(pet-data-api 폴링)는 삭제하고, `FacilitySyncScheduler`는 파일 경로를 참조하도록 교체한다.

**Tech Stack:** Spring Boot 3, JPA, Mockito/JUnit5, `@PreAuthorize("hasAnyRole('ADMIN','MASTER')")`

---

## 파일 맵

| 작업 | 파일 |
|------|------|
| 생성 | `domain/location/dto/LocationImportDto.java` |
| 생성 | `domain/location/service/LocationImportService.java` |
| 생성 | `test/.../location/service/LocationImportServiceTest.java` |
| 수정 | `domain/location/controller/LocationServiceAdminController.java` |
| 삭제 | `domain/location/service/FacilitySyncService.java` |
| 수정 | `domain/location/service/FacilitySyncScheduler.java` |
| 수정 | `resources/application.properties` |

모든 경로 prefix: `backend/main/java/com/linkup/Petory/`  
테스트 prefix: `backend/test/java/com/linkup/Petory/`

---

## Task 1: LocationImportDto 생성

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/location/dto/LocationImportDto.java`

Python batch가 출력할 JSON 한 레코드의 스키마. `@JsonIgnoreProperties(ignoreUnknown = true)`로 Python 쪽 필드가 늘어나도 무시.

- [ ] **Step 1: 파일 생성**

```java
package com.linkup.Petory.domain.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationImportDto {
    private String name;      // 필수
    private String category;  // "grooming"|"hospital"|"pharmacy"|"cafe"|"restaurant"|"pension"|"boarding"|"hotel"|"supplies"
    private String address;   // 필수
    private String sido;
    private String sigungu;
    private Double lat;       // 필수
    private Double lng;       // 필수
    private String phone;
    private String status;    // "폐업" 이면 skip
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava -x test
```

Expected: `BUILD SUCCESSFUL`

---

## Task 2: LocationImportService 구현

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java`

`FacilitySyncService`의 validate/toEntity/batch 로직을 재구현. `SyncResult`도 이 클래스 내부에 정의 (`FacilitySyncService.SyncResult` 대체).

- [ ] **Step 1: 파일 생성**

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
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava -x test
```

Expected: `BUILD SUCCESSFUL`

---

## Task 3: LocationImportService 테스트

**Files:**
- Create: `backend/test/java/com/linkup/Petory/domain/location/service/LocationImportServiceTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package com.linkup.Petory.domain.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationImportServiceTest {

    @Mock  LocationServiceRepository locationServiceRepository;
    @Mock  LocationServiceBatchWriter batchWriter;
    @Spy   ObjectMapper objectMapper;

    @InjectMocks LocationImportService service;

    private ByteArrayInputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importFromStream_savesValidEntry() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.existsByNameAndAddress("멍멍미용", "서울 강남구")).thenReturn(false);
        when(batchWriter.saveBatch(any())).thenReturn(1);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(0);
        assertThat(result.getDuplicate()).isEqualTo(0);
    }

    @Test
    void importFromStream_skipsDuplicate() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"category\":\"grooming\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";
        when(locationServiceRepository.existsByNameAndAddress("멍멍미용", "서울 강남구")).thenReturn(true);

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getDuplicate()).isEqualTo(1);
        assertThat(result.getSaved()).isEqualTo(0);
    }

    @Test
    void importFromStream_skipsBlankName() throws IOException {
        String json = "[{\"name\":\"\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_skipsMissingLatLng() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\"}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_skipsClosedStatus() throws IOException {
        String json = "[{\"name\":\"멍멍미용\",\"address\":\"서울 강남구\",\"lat\":37.5,\"lng\":127.0,\"status\":\"폐업\"}]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getSkipped()).isEqualTo(1);
    }

    @Test
    void importFromStream_emptyArray_returnsZeros() throws IOException {
        String json = "[]";

        LocationImportService.SyncResult result = service.importFromStream(toStream(json));

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getSaved()).isEqualTo(0);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인 (LocationImportService 미완성 상태라면)**

```bash
./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest" -x compileJava
```

Task 2가 완료되었다면 PASS 예상.

- [ ] **Step 3: 테스트 PASS 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest"
```

Expected: 6개 모두 PASS

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/location/dto/LocationImportDto.java \
        backend/main/java/com/linkup/Petory/domain/location/service/LocationImportService.java \
        backend/test/java/com/linkup/Petory/domain/location/service/LocationImportServiceTest.java
git commit -m "feat(location): LocationImportService - JSON 파일 기반 시설 import"
```

---

## Task 4: Admin import 엔드포인트 추가

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceAdminController.java`

기존 `/sync` 엔드포인트는 이 Task에서 건드리지 않는다 (Task 5에서 제거).

- [ ] **Step 1: LocationImportService 의존 추가 + /import 엔드포인트 추가**

현재 파일 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.service.FacilitySyncService;
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

    private final FacilitySyncService facilitySyncService;
    private final LocationImportService locationImportService;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> syncFacilities() {
        FacilitySyncService.SyncResult result = facilitySyncService.syncFromPetDataApi();
        return ResponseEntity.ok(Map.of(
                "total", result.getTotal(),
                "saved", result.getSaved(),
                "duplicate", result.getDuplicate(),
                "skipped", result.getSkipped()
        ));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Map<String, Object>> importFacilities(
            @RequestPart("file") MultipartFile file) throws IOException {
        LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
        return ResponseEntity.ok(Map.of(
                "total", result.getTotal(),
                "saved", result.getSaved(),
                "duplicate", result.getDuplicate(),
                "skipped", result.getSkipped()
        ));
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceAdminController.java
git commit -m "feat(location): POST /api/admin/location/import - 파일 업로드 기반 시설 import 엔드포인트"
```

---

## Task 5: FacilitySyncService 삭제 + Scheduler 파일 기반으로 교체

pet-data-api 의존을 완전히 끊는다.

**Files:**
- Delete: `backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncScheduler.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceAdminController.java`
- Modify: `backend/main/resources/application.properties` (또는 `application-dev.properties`)

- [ ] **Step 1: application.properties에 file-path 설정 추가**

`app.location.import.batch-size` 아래에 추가:

```properties
# Python batch 출력 JSON 파일 경로. 비어있으면 cron sync 스킵.
app.location.import.file-path=
```

- [ ] **Step 2: FacilitySyncScheduler를 파일 기반으로 교체**

파일 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final LocationImportService locationImportService;

    @Value("${app.location.import.file-path:}")
    private String importFilePath;

    @Scheduled(cron = "0 0 1 * * *")
    public void scheduledSync() {
        if (!StringUtils.hasText(importFilePath)) {
            log.warn("[FacilitySyncScheduler] app.location.import.file-path 미설정 — sync 스킵");
            return;
        }
        log.info("[FacilitySyncScheduler] 파일 기반 import 시작: {}", importFilePath);
        try {
            LocationImportService.SyncResult result = locationImportService.importFromFile(importFilePath);
            log.info("[FacilitySyncScheduler] 완료 total={} saved={} duplicate={} skipped={}",
                    result.getTotal(), result.getSaved(), result.getDuplicate(), result.getSkipped());
        } catch (Exception e) {
            log.error("[FacilitySyncScheduler] 실패: {}", e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 3: LocationServiceAdminController에서 FacilitySyncService 제거 + /sync 엔드포인트 제거**

파일 전체를 아래로 교체:

```java
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
            @RequestPart("file") MultipartFile file) throws IOException {
        LocationImportService.SyncResult result = locationImportService.importFromStream(file.getInputStream());
        return ResponseEntity.ok(Map.of(
                "total", result.getTotal(),
                "saved", result.getSaved(),
                "duplicate", result.getDuplicate(),
                "skipped", result.getSkipped()
        ));
    }
}
```

- [ ] **Step 4: FacilitySyncService.java 삭제**

```bash
rm /Users/maknkkong/project/Petory/backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncService.java
```

- [ ] **Step 5: 컴파일 + 전체 테스트**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. FacilitySyncService를 참조하던 곳이 남아있으면 컴파일 에러로 드러남.

- [ ] **Step 6: 최종 커밋**

```bash
git add -u
git commit -m "refactor(location): FacilitySyncService 제거 — pet-data-api 의존 제거, 파일 기반 import로 교체"
```

---

## 검증

**수동 검증 (서버 실행 후):**

```bash
# 테스트용 JSON 파일 생성
cat > /tmp/test_import.json << 'EOF'
[
  {"name":"테스트미용실","category":"grooming","address":"서울 강남구 테헤란로 1","sido":"서울특별시","sigungu":"강남구","lat":37.5012,"lng":127.0396,"phone":"02-1234-5678"},
  {"name":"폐업미용실","category":"grooming","address":"서울 강남구 테헤란로 2","lat":37.5013,"lng":127.0397,"status":"폐업"},
  {"name":"","address":"서울 강남구 테헤란로 3","lat":37.5014,"lng":127.0398}
]
EOF

# Admin JWT 토큰으로 import 호출
curl -s -X POST http://localhost:8080/api/admin/location/import \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -F "file=@/tmp/test_import.json" | jq .
```

Expected 응답:
```json
{"total": 3, "saved": 1, "duplicate": 0, "skipped": 2}
```

**자동 테스트:**

```bash
./gradlew test --tests "com.linkup.Petory.domain.location.service.LocationImportServiceTest"
```

Expected: 6개 PASS
