# Step 2: PublicDataApiClient — odcloud 오픈API 페이징 호출 + 한글 키 매핑

## 목표
공공데이터포털(data 15111389)의 odcloud 자동변환 오픈API를 페이지 단위로 전부 순회 호출하고,
응답의 한글 컬럼 키를 기존 `PublicDataLocationDTO`(영문 필드)로 매핑해 리스트로 반환하는
`PublicDataApiClient`를 신규 생성한다.

## 배경
- odcloud 파일데이터 오픈API 표준 포맷:
  - URL: `https://api.odcloud.kr/api/15111389/v1/uddi:41944402-8249-4e45-9e9d-a52d0a7db1cc`
  - 쿼리: `page`(1부터), `perPage`, `serviceKey`(디코딩키), `returnType=JSON`
  - 응답: `{ "page":1, "perPage":100, "totalCount":70650, "currentCount":100, "matchCount":70650, "data":[ {한글키:값, ...} ] }`
- 데이터가 약 7만 건이므로 `perPage`(예: 1000) 단위로 `page`를 1씩 올리며 `data`가 빌 때까지 순회한다.
- 응답 `data` 항목의 키는 **한글 컬럼명**이다. 최종작성일/좌표 등 파싱은 다음 Step의 `convertToEntity`가 담당하므로,
  여기서는 문자열 그대로 `PublicDataLocationDTO`에 채운다.
- `NaverMapService`와 동일하게 `RestClient.Builder`를 주입받아 `RestClient`를 만든다. 서비스키는 `@Value`로 주입.

> ⚠️ **한글 키 정확성**: 아래 매핑은 데이터셋 컬럼 정의 기준이다. 키 문자열의 공백/괄호가 실제 응답과 미세하게 다를 수 있어,
> 매핑 시 **키의 모든 공백을 제거한 정규화 문자열로 비교**한다(`normalizeKey`). 라이브 응답에서 매핑 누락이 발견되면
> `KEY_TO_FIELD`의 정규화 키만 보정하면 된다 — 이 지점이 유일하게 실제 응답 확인이 필요한 곳이다.

## 설정 추가

### `backend/main/resources/application.properties` (gitignored — 로컬 dev)
아래 3줄 추가(값의 `serviceKey`는 data.go.kr 활용신청 후 발급되는 **디코딩 키**를 넣는다):

```properties
# 공공데이터포털 반려동물 동반가능 문화시설 오픈API
app.public-data.base-url=https://api.odcloud.kr/api/15111389/v1/uddi:41944402-8249-4e45-9e9d-a52d0a7db1cc
app.public-data.service-key=여기에_디코딩_서비스키
app.public-data.page-size=1000
```

### `backend/main/resources/application-prod.properties` (커밋됨 — 값은 .env에서 주입)

```properties
app.public-data.base-url=https://api.odcloud.kr/api/15111389/v1/uddi:41944402-8249-4e45-9e9d-a52d0a7db1cc
# env 미설정이어도 기동은 되게 빈 기본값(:) 사용 — 키 없으면 실행 시 FAILED 로그만 남고 앱은 정상 기동
app.public-data.service-key=${PUBLIC_DATA_SERVICE_KEY:}
app.public-data.page-size=1000
```

### `.env` (gitignored)
`PUBLIC_DATA_SERVICE_KEY=발급받은_디코딩_서비스키` 한 줄 추가.

## 변경 파일

### 1. `backend/main/java/com/linkup/Petory/domain/location/dto/PublicDataApiPage.java` (신규)
odcloud 응답 페이지 래퍼(내부 사용).

```java
package com.linkup.Petory.domain.location.dto;

import java.util.List;

/**
 * odcloud 오픈API 한 페이지 파싱 결과.
 * @param items    이 페이지의 시설 목록
 * @param totalCount 전체 건수(응답 matchCount/totalCount)
 */
public record PublicDataApiPage(List<PublicDataLocationDTO> items, int totalCount) {
}
```

### 2. `backend/main/java/com/linkup/Petory/domain/location/service/PublicDataApiClient.java` (신규)

```java
package com.linkup.Petory.domain.location.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.linkup.Petory.domain.location.dto.PublicDataApiPage;
import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터포털(odcloud) 반려동물 동반가능 문화시설 오픈API 클라이언트.
 * 페이지 단위 조회 + 한글 응답 키 → PublicDataLocationDTO 매핑을 담당한다.
 */
@Slf4j
@Service
public class PublicDataApiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final int MAX_RETRIES = 2;

    /** 정규화(공백 제거)된 한글 컬럼키 → DTO setter 매핑. 라이브 응답에서 키가 다르면 여기만 보정. */
    private static final Map<String, java.util.function.BiConsumer<PublicDataLocationDTO, String>> KEY_TO_FIELD =
            Map.ofEntries(
                    Map.entry("시설명", PublicDataLocationDTO::setFacilityName),
                    Map.entry("카테고리1", PublicDataLocationDTO::setCategory1),
                    Map.entry("카테고리2", PublicDataLocationDTO::setCategory2),
                    Map.entry("카테고리3", PublicDataLocationDTO::setCategory3),
                    Map.entry("시도명칭", PublicDataLocationDTO::setSidoName),
                    Map.entry("시군구명칭", PublicDataLocationDTO::setSigunguName),
                    Map.entry("법정읍면동명칭", PublicDataLocationDTO::setEupmyeondongName),
                    Map.entry("리명칭", PublicDataLocationDTO::setRiName),
                    Map.entry("번지", PublicDataLocationDTO::setBunji),
                    Map.entry("도로명이름", PublicDataLocationDTO::setRoadName),
                    Map.entry("건물번호", PublicDataLocationDTO::setBuildingNumber),
                    Map.entry("위도", PublicDataLocationDTO::setLatitude),
                    Map.entry("경도", PublicDataLocationDTO::setLongitude),
                    Map.entry("우편번호", PublicDataLocationDTO::setPostalCode),
                    Map.entry("도로명주소", PublicDataLocationDTO::setRoadAddress),
                    Map.entry("지번주소", PublicDataLocationDTO::setJibunAddress),
                    Map.entry("전화번호", PublicDataLocationDTO::setPhone),
                    Map.entry("홈페이지", PublicDataLocationDTO::setWebsite),
                    Map.entry("휴무일", PublicDataLocationDTO::setClosedDays),
                    Map.entry("운영시간", PublicDataLocationDTO::setOperatingHours),
                    Map.entry("주차가능여부", PublicDataLocationDTO::setParkingAvailable),
                    Map.entry("입장이용료가격정보", PublicDataLocationDTO::setEntranceFee),
                    Map.entry("반려동물동반가능정보", PublicDataLocationDTO::setPetFriendly),
                    Map.entry("반려동물전용정보", PublicDataLocationDTO::setPetOnly),
                    Map.entry("입장가능동물크기", PublicDataLocationDTO::setPetSizeLimit),
                    Map.entry("반려동물제한사항", PublicDataLocationDTO::setPetRestrictions),
                    Map.entry("장소실내여부", PublicDataLocationDTO::setIndoor),
                    Map.entry("장소실외여부", PublicDataLocationDTO::setOutdoor),
                    Map.entry("기본정보장소설명", PublicDataLocationDTO::setDescription),
                    Map.entry("애견동반추가요금", PublicDataLocationDTO::setPetAdditionalFee),
                    Map.entry("최종작성일", PublicDataLocationDTO::setLastUpdatedDate));

    private final RestClient restClient;

    @Value("${app.public-data.base-url}")
    private String baseUrl;

    @Value("${app.public-data.service-key:}")
    private String serviceKey;

    @Value("${app.public-data.page-size:1000}")
    private int pageSize;

    public PublicDataApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * 전체 페이지를 순회하며 모든 시설을 수집한다.
     * @return 매핑된 전체 시설 DTO 목록
     * @throws IllegalStateException 서비스키 미설정 시
     */
    public List<PublicDataLocationDTO> fetchAll() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("app.public-data.service-key 가 설정되지 않았습니다.");
        }

        List<PublicDataLocationDTO> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PublicDataApiPage result = fetchPage(page);
            if (result.items().isEmpty()) {
                break;
            }
            all.addAll(result.items());
            log.info("공공데이터 조회 진행: page={}, 누적={}, 전체={}", page, all.size(), result.totalCount());
            if (all.size() >= result.totalCount()) {
                break;
            }
            page++;
        }
        return all;
    }

    /**
     * 단일 페이지 조회 (실패 시 최대 {@link #MAX_RETRIES}회 재시도).
     */
    public PublicDataApiPage fetchPage(int page) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("page", page)
                .queryParam("perPage", pageSize)
                .queryParam("returnType", "JSON")
                .queryParam("serviceKey", serviceKey)
                .build()
                .toUri();

        RestClientException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> body = restClient.get().uri(uri)
                        .retrieve()
                        .body(MAP_TYPE);
                return parsePage(body);
            } catch (RestClientException e) {
                last = e;
                log.warn("공공데이터 page={} 호출 실패(attempt {}/{}): {}", page, attempt + 1, MAX_RETRIES + 1,
                        e.getMessage());
            }
        }
        throw last;
    }

    @SuppressWarnings("unchecked")
    private PublicDataApiPage parsePage(Map<String, Object> body) {
        if (body == null) {
            return new PublicDataApiPage(List.of(), 0);
        }
        int totalCount = asInt(body.getOrDefault("totalCount", body.get("matchCount")));
        Object dataObj = body.get("data");
        if (!(dataObj instanceof List<?> rows)) {
            return new PublicDataApiPage(List.of(), totalCount);
        }
        List<PublicDataLocationDTO> items = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                items.add(mapRow((Map<String, Object>) map));
            }
        }
        return new PublicDataApiPage(items, totalCount);
    }

    private PublicDataLocationDTO mapRow(Map<String, Object> row) {
        PublicDataLocationDTO dto = new PublicDataLocationDTO();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            var setter = KEY_TO_FIELD.get(normalizeKey(e.getKey()));
            if (setter != null && e.getValue() != null) {
                setter.accept(dto, String.valueOf(e.getValue()).trim());
            }
        }
        return dto;
    }

    /** 키의 모든 공백/괄호/언더스코어를 제거해 미세한 헤더 표기차를 흡수한다. */
    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.replaceAll("[\\s()\\[\\]_]", "");
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? 0 : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

## 테스트

### `backend/test/java/com/linkup/Petory/domain/location/service/PublicDataApiClientTest.java` (신규)
외부 API를 부르지 않고 `mapRow`/`parsePage` 매핑만 검증한다. `restClient` 필드는 리플렉션으로 목 응답을 주입하기
번거로우므로, **매핑 로직을 검증**하는 데 집중한다. `parsePage`가 private이므로, 아래처럼 `RestClient`를 목킹해
`fetchPage`를 통해 검증한다(MockRestServiceServer 대신 순수 단위 테스트로 단순화).

```java
package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.linkup.Petory.domain.location.dto.PublicDataApiPage;

class PublicDataApiClientTest {

    private PublicDataApiClient newClient() {
        PublicDataApiClient client = new PublicDataApiClient(RestClient.builder());
        ReflectionTestUtils.setField(client, "baseUrl", "https://example.test/api");
        ReflectionTestUtils.setField(client, "serviceKey", "dummy-key");
        ReflectionTestUtils.setField(client, "pageSize", 1000);
        return client;
    }

    @Test
    void 한글키_응답을_DTO로_매핑한다() {
        PublicDataApiClient client = newClient();
        Map<String, Object> body = Map.of(
                "totalCount", 1,
                "data", List.of(Map.of(
                        "시설명", "행복동물병원",
                        "도로명주소", "서울특별시 강남구 테헤란로 152",
                        "위도", "37.5",
                        "경도", "127.0",
                        "반려동물 동반 가능정보", "Y")));

        PublicDataApiPage page = (PublicDataApiPage) ReflectionTestUtils.invokeMethod(client, "parsePage", body);

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        var dto = page.items().get(0);
        assertThat(dto.getFacilityName()).isEqualTo("행복동물병원");
        assertThat(dto.getRoadAddress()).isEqualTo("서울특별시 강남구 테헤란로 152");
        assertThat(dto.getLatitude()).isEqualTo("37.5");
        assertThat(dto.getPetFriendly()).isEqualTo("Y"); // "반려동물 동반 가능정보" → 공백제거 매칭
    }

    @Test
    void data가_없으면_빈_페이지를_반환한다() {
        PublicDataApiClient client = newClient();
        PublicDataApiPage page = (PublicDataApiPage) ReflectionTestUtils.invokeMethod(
                client, "parsePage", Map.of("totalCount", 0));
        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }
}
```

## Acceptance Criteria

- [ ] `./gradlew compileJava` 성공.
- [ ] `./gradlew test --tests "*PublicDataApiClientTest"` 성공 — 한글 키 매핑/빈 페이지 처리 검증.
- [ ] (선택, 서비스키 발급 후 수동) 앱 기동 상태에서 실제 1페이지 스모크 확인:
  `curl "https://api.odcloud.kr/api/15111389/v1/uddi:41944402-8249-4e45-9e9d-a52d0a7db1cc?page=1&perPage=1&returnType=JSON&serviceKey=<키>"`
  → `data[0]`의 실제 한글 키가 `KEY_TO_FIELD`의 정규화 키와 매칭되는지 확인. 누락 시 매핑 보정.

## 커밋

```bash
git add backend/main/java/com/linkup/Petory/domain/location/dto/PublicDataApiPage.java \
        backend/main/java/com/linkup/Petory/domain/location/service/PublicDataApiClient.java \
        backend/test/java/com/linkup/Petory/domain/location/service/PublicDataApiClientTest.java \
        backend/main/resources/application-prod.properties
git commit -m "feat(location): 공공데이터 odcloud 오픈API 클라이언트 및 한글 키 매핑 추가"
```

> 참고: `application.properties`와 `.env`는 gitignored이므로 커밋되지 않는다. 로컬/배포에 서비스키를 직접 넣어야 한다.
