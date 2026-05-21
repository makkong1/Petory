# Step 2: FacilitySyncService — 변환 매핑 + 중복 체크 + 배치 저장

## 목표
`PetFacilityDto` → `LocationService` 변환, `existsByNameAndAddress` 중복 체크,
배치 저장 로직을 담은 `FacilitySyncService`를 신규 생성한다.

## 배경
Petory `locationservice` 테이블: category1/2/3 계층, latitude/longitude, dataSource 필드 존재.
중복 체크: `locationServiceRepository.existsByNameAndAddress(name, address)` (기존 메서드 재사용).
배치 저장: 기존 `LocationServiceBatchWriter.saveBatch()` 재사용.

pet-data-api의 `PetFacilityDto`에서 Petory `LocationService`로의 매핑 규칙:
- `name` → `name`
- `address` → `address`
- `regionCity` → `sido`
- `regionDistrict` → `sigungu`
- `phone` → `phone`
- `lat/lng` → `latitude/longitude`
- `category` → `category3` (소분류), `category2 = "반려동물"`, `category1 = "반려동물 서비스"`
- `status != "영업"` → skip
- `dataSource = "PET_DATA_API"`

## 변경 파일

### 1. `backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncService.java` (신규)

```java
package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.recommendation.client.PetDataApiClient;
import com.linkup.Petory.domain.recommendation.dto.PetFacilityDto;
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
        log.info("[FacilitySyncService] 동기화 시작");
        int total = 0, saved = 0, duplicate = 0, skipped = 0;
        List<LocationService> batch = new ArrayList<>();

        List<PetFacilityDto> facilities = petDataApiClient.fetchAllFacilities(PAGE_SIZE);
        total = facilities.size();

        for (PetFacilityDto dto : facilities) {
            try {
                if (!isValid(dto)) {
                    skipped++;
                    continue;
                }
                String address = dto.getAddress();
                if (locationServiceRepository.existsByNameAndAddress(dto.getName(), address)) {
                    duplicate++;
                    continue;
                }
                batch.add(toEntity(dto));
                if (batch.size() >= batchSize) {
                    batchWriter.saveBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.warn("[FacilitySyncService] 변환 실패 name={} err={}", dto.getName(), e.getMessage());
                skipped++;
            }
        }

        if (!batch.isEmpty()) {
            batchWriter.saveBatch(batch);
            saved += batch.size();
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
        String category3 = categoryLabel(dto.getCategory(), dto.getType());
        return LocationService.builder()
                .name(dto.getName())
                .category1("반려동물 서비스")
                .category2("반려동물")
                .category3(category3)
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

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SyncResult {
        private int total;
        private int saved;
        private int duplicate;
        private int skipped;
    }
}
```

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory

# 컴파일
./gradlew compileJava

# 서비스 단위 테스트 (pet-data-api mock)
./gradlew test --tests "*FacilitySyncServiceTest*"

# 전체 테스트
./gradlew test
```

## 테스트 파일 생성 위치
`backend/test/java/com/linkup/Petory/domain/location/service/FacilitySyncServiceTest.java`

케이스:
1. 정상 변환 + 저장 확인
2. 중복 데이터 skip 확인 (`existsByNameAndAddress` mock)
3. 폐업 시설 skip 확인
4. 이름 없는 데이터 skip 확인
