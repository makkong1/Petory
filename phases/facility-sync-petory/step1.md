# Step 1: PetDataApiClient 확장 — GET /facilities 페이지네이션 + PetFacilityDto

## 목표
`PetDataApiClient`에 pet-data-api의 `GET /facilities` 전체 페이지네이션 호출 메서드를 추가하고,
응답을 담을 `PetFacilityDto`를 신규 생성한다.

## 배경
현재 `PetDataApiClient`는 `/recommend`, `/recommend/copy`, `/events/recommendation`만 호출.
facility 데이터를 가져오려면 `GET /facilities?cursor=0&limit=100` keyset 페이지네이션 호출이 필요.
pet-data-api 응답 스키마: `FacilityListResponse { items, next_cursor, has_next }`.

## 변경 파일

### 1. `backend/main/java/com/linkup/Petory/domain/recommendation/dto/PetFacilityDto.java` (신규)

```java
package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetFacilityDto {
    private Long id;
    @JsonProperty("source_id")
    private String sourceId;
    private String type;
    private String category;
    private String name;
    private String status;
    private String address;
    @JsonProperty("region_city")
    private String regionCity;
    @JsonProperty("region_district")
    private String regionDistrict;
    private String phone;
    private Double lat;
    private Double lng;
}
```

### 2. `backend/main/java/com/linkup/Petory/domain/recommendation/dto/PetFacilityPageDto.java` (신규)

```java
package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetFacilityPageDto {
    private List<PetFacilityDto> items;
    @JsonProperty("next_cursor")
    private Long nextCursor;
    @JsonProperty("has_next")
    private boolean hasNext;
}
```

### 3. `backend/main/java/com/linkup/Petory/domain/recommendation/client/PetDataApiClient.java` (수정)

기존 클래스에 메서드 추가. `recommendClient` (3초 타임아웃)는 너무 짧으므로
대용량 fetch용으로는 별도 `facilityClient` (30초) 사용.

생성자에 `facilityClient` 추가:
```java
private final RestClient facilityClient;

public PetDataApiClient(...) {
    ...
    this.facilityClient = buildClient(baseUrl, apiKey, 30_000);  // facility sync용
}
```

fetchFacilitiesPage 메서드 추가:
```java
public PetFacilityPageDto fetchFacilitiesPage(long cursor, int limit) {
    try {
        String responseBody = facilityClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/facilities")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(String.class);
        return objectMapper.readValue(responseBody, PetFacilityPageDto.class);
    } catch (Exception e) {
        log.error("[PetDataApiClient/facilities] 실패 cursor={} {}", cursor, e.getMessage(), e);
        throw new RuntimeException("시설 목록 API 호출 실패: " + e.getMessage(), e);
    }
}
```

전체 페이지 순회 메서드:
```java
public List<PetFacilityDto> fetchAllFacilities(int pageSize) {
    List<PetFacilityDto> all = new ArrayList<>();
    long cursor = 0;
    do {
        PetFacilityPageDto page = fetchFacilitiesPage(cursor, pageSize);
        all.addAll(page.getItems());
        if (!page.isHasNext() || page.getNextCursor() == null) break;
        cursor = page.getNextCursor();
    } while (true);
    return all;
}
```

## AC (Acceptance Criteria)

```bash
# 컴파일 확인
cd /Users/maknkkong/project/Petory && ./gradlew compileJava

# 새 DTO가 Jackson으로 역직렬화 가능한지 단위 테스트
# (pet-data-api 미기동 상태에서 mock으로 확인)
./gradlew test --tests "*PetDataApiClientTest*"
```
