# LocationDomainV2 페이지 드래프트 점검 및 재작성

> 목적: 포트폴리오 repo의 `LocationDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/location.md`, 위치 아키텍처/리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

재작성 필요.

첨부한 `LocationDomainV2.jsx`는 포트폴리오 페이지용 구조와 톤이 기존 드래프트보다 더 정리되어 있다. 다만 현재 코드와 비교하면 일부 문구는 그대로 쓰면 위험하다.

- 페이지 섹션 구조(`pillars`, `intro`, `design`, `limits`, `docs`)는 그대로 진행해도 된다.
- 핵심 칩은 실제 페이지처럼 6개로 줄이는 편이 좋다. 기존 드래프트의 10개 칩은 구현 설명에는 좋지만 포트폴리오 첫 화면에서는 과하다.
- `roadName -> eupmyeondong -> sigungu -> sido` 식의 지역 계층 검색 문구는 현재 코드 기준으로 맞지 않다. 활성 검색 분기는 `sigungu -> sido`이고, `eupmyeondong`, `roadName` 직접 검색 repository 메서드는 비활성화되어 있다.
- `score` 정렬을 "DB 미지원이라 서비스 레이어 후처리"로만 설명하면 부정확하다. 반경 검색 native query에는 `score` ORDER BY가 있고, 컨트롤러에도 후처리 정렬이 남아 있다. 이 모순 자체를 한계로 쓰는 편이 정확하다.
- JSON 적재는 첨부 페이지의 표현에는 있지만, 현재 백엔드 코드 기준으로 확정 가능한 활성 적재 경로는 공공데이터 CSV 업로드/경로 임포트다. JSON을 페이지에 쓰려면 별도 근거 파일이 필요하다.

---

## 1. 페이지 상단

### H1

위치 서비스 도메인

### 소개 문단

Location 도메인은 반려동물 동반 시설을 지도에서 탐색하고, 리뷰/추천 흐름으로 연결하는 영역이다. 메인 사용자 경로는 사용자의 검색 기준점(`searchCenter`)과 반경을 서버에 보내는 위치 기반 검색이며, 백엔드는 `/api/location-services/search` 하나에서 좌표 반경 검색, 지역 검색, 키워드 단독 검색을 분기한다. 프론트는 지도 뷰 중심(`mapViewportCenter`)과 실제 검색 기준(`searchCenter`)을 분리해 지도 이동만으로 결과가 흔들리지 않게 하고, `"이 지역 검색"` 액션에서만 location 검색 기준을 커밋한다.

### 핵심 기능 태그 (`corePillars`)

첨부 페이지의 6개 태그 구성이 적절하다.

```javascript
const corePillars = [
  "위치 우선 검색 분기",
  "sort=stable 추천순",
  "반경·size=300",
  "지도 「이 지역」",
  "CSV 배치 적재",
  "목록/추천 API 분리",
];
```

주의: 첨부 JSX의 `"JSON·CSV 적재"`는 현재 코드 기준으로는 `"CSV 배치 적재"`가 더 안전하다. JSON 적재 근거를 포트폴리오 repo나 과거 문서에서 별도로 확인할 수 있으면 `"JSON·CSV 적재"`를 유지해도 된다.

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

Location 검색은 컨트롤러가 요청 파라미터 조합을 보고 `LocationServiceService`의 반경/지역/키워드 검색 메서드로 분기한다. `latitude`와 `longitude`가 있으면 keyword나 category가 있어도 반경 검색이 우선이고, keyword는 반경 후보 안에서 시설명 `LIKE` 필터로만 적용된다. 좌표가 없고 `sigungu` 또는 `sido`가 있을 때는 지역 검색을 사용하며, 좌표와 지역이 모두 없을 때만 FULLTEXT 기반 keyword 검색으로 fallback한다.

통합 지도 location 탭은 `unifiedMapApi`에서 반경을 km에서 m로 변환하고 `size=300`을 고정 전달한다. 프론트 기본 정렬은 `stable`이며 UI에서는 추천순으로 노출한다. 지도 드래그는 `mapViewportCenter`만 바꾸고, 사용자가 `"이 지역 검색"`을 누르거나 검색/필터를 바꿀 때 `searchCenter`를 갱신해 서버 조회를 다시 실행한다.

추천 카드는 Location 목록 API가 직접 만들지 않는다. 추천 도메인은 별도의 `/api/recommend` 흐름에서 signal/card를 만들고, 사용자가 추천 카드를 선택하면 Location 검색의 `category` 필터로 다시 연결된다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 메인 사용자 경로 | `UnifiedPetMapPage`에서 `searchCenter + radius + keyword/category/sort`로 주변서비스 조회 |
| API 진입점 | `GET /api/location-services/search` |
| 검색 분기 | `lat/lng` 반경 검색 -> `sigungu/sido` 지역 검색 -> keyword 단독 FULLTEXT -> 전체 평점순 |
| 반경 단위 | 프론트 UI는 km, `unifiedMapApi`에서 `radius * 1000`으로 변환해 백엔드에는 m 전달 |
| 결과 제한 | controller 기본 `size=100`, 통합 지도 location 탭은 `size=300` 고정 |
| 반경 기본값 | 프론트 location 기본 반경 5km, 백엔드는 radius 누락/0 이하일 때 10,000m |
| 기본 정렬 | 프론트 기본값 `stable`, UI 라벨은 추천순 |
| 지도 상태 | `mapViewportCenter`와 `searchCenter`를 분리 |
| 지역 검색 | 활성 직접 검색은 `sigungu`, `sido` 기준 |
| 비활성 지역 필드 | `eupmyeondong`, `roadName` 직접 검색 repository 메서드는 현재 주석 처리 |
| 키워드 검색 | 좌표 경로에서는 `name LIKE`, keyword 단독 경로에서는 FULLTEXT |
| 데이터 적재 | `AdminLocationController` -> `PublicDataLocationService` CSV 업로드/경로 임포트 |

### 2-3. 성능/안정성 테이블

첨부 페이지의 Before/After 수치는 포트폴리오 설명에 사용할 수 있다. 단, 새로 측정한 값처럼 쓰지 말고 기존 초기 로드 개선 맥락의 수치로 명시한다.

| 지표 | Before | After |
|---|---|---|
| 초기 로드 데이터 수 | 22,699건 | 1,026건 |
| 프론트 전체 처리 시간 | 1,484ms | 약 700ms |
| 메모리 사용량 | 78.90MB | 약 28.6MB |

보조 설명:

- 현재 통합 지도는 location 결과를 `size=300`으로 제한하므로, 위 After 수치는 현재 코드의 항상값이 아니라 초기 로드 개선 문맥의 측정값으로 다룬다.
- 현재 구현의 핵심 개선은 전체 조회를 피하고, 반경 검색 + SQL `LIMIT` + 지도 기준 커밋 방식으로 전송량과 렌더링 부담을 제한한 점이다.

### 2-4. 데이터 흐름 카드

문구:

통합 지도는 별도 BFF 없이 프론트의 `fetchActiveMapItems`가 활성 탭에 맞는 도메인 API를 호출한다. Location 탭에서는 `locationServiceApi.searchPlaces`가 `/api/location-services/search`를 호출하고, 응답은 `toMapItem()`에서 공통 지도 마커 모델로 정규화된다. 상세한 시퀀스 다이어그램은 통합 흐름 페이지로 분리한다.

내부 링크:

- `/domains/flows?tab=location`

---

## 3. `section#design` - 기술 결정

첨부 JSX처럼 A~F 6개 카드 구성이 적절하다. 기존 드래프트의 리뷰/추천 카드는 Location 페이지 본문에서는 줄이고, 관련 도메인 링크나 한계 섹션에서만 언급한다.

### A. 검색 분기 우선순위

핵심 문구:

위치 서비스 검색은 파라미터가 많지만, 우선순위는 단순하다. 좌표가 있으면 지도 중심 반경 검색이 우선이고, 좌표가 없을 때만 지역명 또는 keyword 단독 검색으로 내려간다. 그래서 메인 지도 경로에서는 서버가 받은 반경 안에서 이미 후보를 줄인 뒤 keyword/category 필터를 적용한다.

코드 스니펫 후보:

```java
if (latitude != null && longitude != null) {
    int radiusM = (radius != null && radius > 0) ? radius : 10_000;
    services = locationServiceService.searchLocationServicesByLocation(
            latitude, longitude, radiusM, keyword, category, sort, effectiveSize);
} else if (StringUtils.hasText(sigungu) || StringUtils.hasText(sido)) {
    services = locationServiceService.searchLocationServicesByRegion(
            sido, sigungu, keyword, category, effectiveSize);
} else if (StringUtils.hasText(keyword)) {
    services = locationServiceService.searchLocationServicesByKeyword(
            keyword, category, effectiveSize);
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceController.java`
- `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java`

### B. 초기 로드 최적화

핵심 문구:

초기 구현은 지도에 필요한 주변 후보보다 훨씬 많은 위치 데이터를 가져오는 구조였다. 현재 통합 지도는 location 탭에서 좌표, 반경, 정렬, 카테고리, keyword를 서버에 넘기고 `size=300`으로 후보 상한을 고정한다. Meetup/Care는 줌 레벨별 제한을 쓰지만, Location은 결과 안정성을 위해 줌 레벨과 후보 수를 분리했다.

코드 스니펫 후보:

```javascript
const LOCATION_RESULT_LIMIT = 300;

const res = await locationServiceApi.searchPlaces({
  latitude: lat,
  longitude: lng,
  radius: radiusKm * 1000,
  ...(keyword && { keyword }),
  ...(category && { category }),
  ...(sort && { sort }),
  size: LOCATION_RESULT_LIMIT,
});
```

근거:

- `frontend/src/api/unifiedMapApi.js`
- `docs/troubleshooting/location/initial-load-performance.md`
- `docs/refactoring/location/지도-결과-안정성-리팩토링.md`

### C. 지도 이동과 재조회 분리

핵심 문구:

지도 이동은 사용자의 탐색 행위이고, 서버 검색은 명시적인 조회 행위다. `mapViewportCenter`는 사용자가 보고 있는 지도 중심이고, `searchCenter`는 서버에 실제로 요청한 검색 기준점이다. Location 탭은 `searchCenter`로 조회하고, 지도 이동 후에는 `"이 지역 검색"` 액션에서만 새 기준점을 커밋한다.

코드 스니펫 후보:

```javascript
const effectiveFetchCenter =
  activeLayer === "location" ? searchCenter : mapViewportCenter;

const handleSearchThisArea = useCallback(() => {
  commitLocationSearch(mapViewportCenter, "user-triggered");
}, [commitLocationSearch, mapViewportCenter]);
```

근거:

- `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`
- `docs/architecture/location/위치 기반 서비스 아키텍처.md`
- `docs/refactoring/location/지도-검색-워크플로우-정리.md`

### D. 검색 분기별 서버 SQL 통합

핵심 문구:

반경 검색은 MySQL 공간 연산을 사용해 후보를 먼저 줄인다. `ST_Within(POLYGON)`으로 근사 사각형 후보를 좁히고, `ST_Distance_Sphere`로 실제 반경 조건을 확인한다. 같은 SQL 안에서 `is_deleted`, keyword, category, sort, `LIMIT`까지 처리해 클라이언트 필터링으로 돌아가지 않게 했다.

정렬 설명:

- `stable`: `rating DESC`, `review_count DESC`, `idx ASC`
- `score`: `ls.score DESC`가 반경 검색 query에 포함됨
- `reviews`: `review_count DESC`
- `rating`: `rating DESC`
- 그 외: 거리순 fallback

코드 스니펫 후보:

```sql
WHERE ST_Within(ls.location, ST_GeomFromText(...))
  AND ST_Distance_Sphere(ls.location, ST_GeomFromText(...)) <= :radiusInMeters
  AND ls.is_deleted = 0
  AND (:keyword IS NULL OR ls.name LIKE CONCAT('%', :keyword, '%'))
ORDER BY
  CASE WHEN :sort = 'score' THEN ls.score END DESC,
  CASE WHEN :sort = 'stable' THEN ls.rating END DESC,
  CASE WHEN :sort = 'stable' THEN ls.review_count END DESC,
  ls.rating DESC,
  ls.idx ASC
LIMIT :limit
```

근거:

- `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java`
- `docs/architecture/location/위치 기반 서비스 아키텍처.md`

### E. 시설 적재 - CSV 배치 경로

핵심 문구:

현재 코드 기준의 활성 시설 적재 경로는 공공데이터 CSV 배치 임포트다. `AdminLocationController`가 MASTER 전용 업로드/경로 임포트 API를 제공하고, `PublicDataLocationService`가 CSV 파싱, 유효성 검증, 엔티티 변환을 맡는다. 저장은 `LocationServiceBatchWriter`로 분리해 대량 데이터 처리 트랜잭션 경계를 관리한다.

API:

| API | 권한 | 용도 |
|---|---|---|
| `POST /api/admin/location-services/import-public-data` | `MASTER` | CSV 파일 업로드 |
| `POST /api/admin/location-services/import-public-data-path` | `MASTER` | 서버 경로 CSV 임포트 |

페이지 문구 주의:

- 첨부 JSX의 `"JSON·CSV 이중 경로"`는 현재 코드 기준으로는 확인되지 않는다.
- `LocationServiceAdminService.loadInitialData()`는 카카오맵 API 의존성 제거로 비활성화되어 있고, CSV 배치 업로드 사용 메시지를 반환한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminLocationController.java`
- `backend/main/java/com/linkup/Petory/domain/location/service/PublicDataLocationService.java`
- `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceBatchWriter.java`
- `docs/architecture/location/위치서비스_공공데이터_CSV_배치_임포트_구현.md`

### F. 지역 계층 검색은 보조 경로로 둔다

핵심 문구:

지역 검색은 백엔드에 남아 있지만 메인 지도 UX의 기본 경로는 아니다. 현재 활성 직접 검색은 `sigungu`와 `sido` 기준이고, `eupmyeondong`, `roadName` 직접 검색 repository 메서드는 주석 처리되어 있다. 따라서 포트폴리오 페이지에서는 "지역 계층 전체를 적극 활용한다"보다 "지역명 fallback으로 지원한다"가 정확하다.

정확한 우선순위:

```text
lat/lng 있음 -> 반경 검색
lat/lng 없음 + sigungu/sido 있음 -> 지역 검색
lat/lng 없음 + 지역 없음 + keyword 있음 -> FULLTEXT 검색
조건 없음 -> 전체 평점순
```

첨부 JSX 수정 권장:

- `roadName → eupmyeondong → sigungu → sido` 표현은 현재 코드 기준으로 제거한다.
- 대신 `sigungu → sido → 전체 평점순` 또는 `지역명 fallback`으로 표현한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/location/repository/LocationServiceRepository.java`
- `backend/main/java/com/linkup/Petory/domain/location/repository/JpaLocationServiceAdapter.java`
- `docs/domains/location.md`

---

## 4. `section#limits` - 한계 & 다음 개선

문구:

Location 도메인은 메인 지도 경로를 반경 검색 중심으로 안정화했지만, 검색 품질과 데이터 운영에는 아직 정리할 부분이 남아 있다.

목록:

- 메인 지도 기본 경로는 lat/lng 반경 검색이다. 백엔드 지역 검색은 지원하지만, 현재 주 사용자 경로에서는 지역명 fallback에 가깝다.
- `eupmyeondong`, `roadName` 직접 검색 query는 코드에 흔적이 있지만 현재 비활성화되어 있다.
- 위치/지역 경로의 keyword는 `name LIKE '%keyword%'`이고, keyword 단독 경로만 FULLTEXT라 검색 범위와 품질이 다르다.
- 통합 지도 Location 결과는 `size=300`으로 고정되어 밀집 지역에서는 300개 이후 후보가 잘릴 수 있다.
- 성능 표의 Before/After 수치는 기존 초기 로드 개선 맥락의 측정값이다. 현재 모든 조회가 그 수치로 동작한다고 쓰면 안 된다.
- `score` 정렬은 반경 검색 SQL에도 있고 컨트롤러 후처리에도 있다. 컨트롤러 주석의 "DB ORDER BY 미지원"은 현재 repository query와 맞지 않아 정리가 필요하다.
- `stable` 정렬은 결과 안정성을 높이지만, 실제 거리 가까움을 최우선으로 보장하지 않는다.
- `/api/location-services` 하위 API는 보안 설정의 `/api/**` 인증 규칙 영향을 받으므로, 페이지에서 공개 API처럼 표현하지 않는다.
- legacy `GET /api/location-services/recommend` 흐름은 추천 도메인으로 분리된 것으로 설명한다.
- CSV 적재는 활성 경로지만, JSON 적재는 현재 코드 근거가 부족하다. 포트폴리오에 JSON을 넣으려면 별도 근거 파일을 확인한다.

---

## 5. `section#docs` - 관련 페이지

### 내부 링크

- `/domains/flows?tab=location` - Location 검색/지도 흐름
- `/domains/recommendation` - 추천 signal과 Location 카테고리 연결
- `/domains/location/optimization` - 초기 로드/지도 결과 안정성 개선
- `/domains/location/refactoring` - 지도 검색 워크플로우 리팩토링
- `/domains/meetup` - 통합 지도에서 공유되는 Meetup 반경 조회
- `/domains/care` - 통합 지도에서 공유되는 Care 반경 조회

### GitHub 링크 상수 후보

첨부 JSX의 상수는 대부분 유지 가능하다. 단, CSV 임포트와 score scheduler를 근거로 보여주고 싶으면 상수를 추가한다.

```javascript
const PETORY_LOCATION_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java';
const PETORY_LOCATION_REPO =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java';
const PETORY_LOCATION_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/domains/location.md';
const PETORY_LOCATION_ARCH =
  'https://github.com/makkong1/Petory/blob/main/docs/architecture/location/%EC%9C%84%EC%B9%98%20%EA%B8%B0%EB%B0%98%20%EC%84%9C%EB%B9%84%EC%8A%A4%20%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98.md';
const PETORY_LOCATION_IMPORT_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/architecture/location/%EC%9C%84%EC%B9%98%EC%84%9C%EB%B9%84%EC%8A%A4_%EA%B3%B5%EA%B3%B5%EB%8D%B0%EC%9D%B4%ED%84%B0_CSV_%EB%B0%B0%EC%B9%98_%EC%9E%84%ED%8F%AC%ED%8A%B8_%EA%B5%AC%ED%98%84.md';
const PETORY_RECOMMENDATION_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/domains/recommendation.md';
```

관련 문서:

- `docs/domains/location.md`
- `docs/architecture/location/위치 기반 서비스 아키텍처.md`
- `docs/architecture/location/위치서비스_공공데이터_CSV_배치_임포트_구현.md`
- `docs/refactoring/location/지도-결과-안정성-리팩토링.md`
- `docs/refactoring/location/지도-검색-워크플로우-정리.md`
- `docs/refactoring/location/주변서비스-현행vs설계안-비교.md`
- `docs/troubleshooting/location/initial-load-performance.md`
- `docs/troubleshooting/location/map-ux-improvement.md`

---

## 6. 첨부 `LocationDomainV2.jsx` 반영 체크

그대로 진행해도 되는 부분:

- 섹션 순서: `pillars -> intro -> design -> limits -> docs`
- 포트폴리오 톤: 문제 배경보다 현재 설계 선택을 먼저 보여주는 방식
- 성능 표: 단, 기존 측정 문맥이라는 주석 필요
- 지도 `"이 지역"` 액션, `searchCenter/mapViewportCenter` 설명
- 목록 API와 추천 API를 분리해서 설명하는 방향

수정하고 진행할 부분:

- `LocationServiceService.searchLocationServices`처럼 단일 서비스 메서드가 있는 것처럼 쓰지 말고, 컨트롤러가 `searchLocationServicesByLocation`, `searchLocationServicesByRegion`, `searchLocationServicesByKeyword`로 분기한다고 쓴다.
- `roadName -> eupmyeondong -> sigungu -> sido` 지역 계층 검색 카드는 현재 코드와 다르다. `sigungu/sido 지역명 fallback`으로 바꾼다.
- `"JSON·CSV 적재"`는 현재 코드 근거만 보면 `"CSV 배치 적재"`가 맞다.
- `score` 정렬 한계 문구는 "DB 미지원"이 아니라 "SQL 정렬과 컨트롤러 후처리가 동시에 남아 있어 정리 필요"로 쓴다.
- `GET /api/location-services` 계열을 공개 API처럼 표현하지 않는다. 보안 설정의 `/api/**` 인증 규칙 영향을 받는다.
