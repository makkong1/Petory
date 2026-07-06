# Location 도메인

> 기준: 현재 코드를 단일 진실로 본다. 리팩토링/트러블슈팅 문서는 배경과 히스토리로만 참고한다.

## 1. 범위

Location 도메인은 반려동물 관련 장소 데이터를 검색하고, 지도에 표시하고, 시설 리뷰를 관리하는 도메인이다.

현재 사용자-facing 핵심 경로는 통합 지도 `주변서비스` 탭이다. 이 경로는 행정구역 계층 탐색보다 `lat/lng + radius` 반경 검색을 기본으로 사용한다. 지역 계층 검색과 FULLTEXT 검색은 백엔드에 남아 있지만, 메인 지도에서는 지역명 검색 또는 위치 정보가 없는 fallback 경로에 가깝다.

포함 범위:

- 위치 서비스 검색
- 반경 기반 주변 시설 조회
- 지역명 기반 시설 조회
- 키워드 단독 FULLTEXT 검색
- 카테고리 필터링
- 지도 검색 기준 관리
- 시설 리뷰 작성/수정/삭제/조회
- 리뷰 평균 평점과 리뷰 수 캐시 갱신
- 주소 검색, 주소-좌표 변환, 좌표-주소 변환, 길찾기
- 관리자용 위치 서비스 목록 조회와 공공데이터 CSV 임포트

비범위:

- Meetup/Care의 지도 반경 조회 로직
- 추천 signal 분석 자체
- 단일 통합 지도 BFF API

## 2. 주요 코드

| 구분                       | 주요 파일                                                                                                    |
| -------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 프론트 통합 지도           | `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`                                                    |
| 통합 지도 API 어댑터       | `frontend/src/api/unifiedMapApi.js`                                                                          |
| 위치 서비스 API 클라이언트 | `frontend/src/api/locationServiceApi.js`                                                                     |
| 검색 컨트롤러              | `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceController.java`              |
| 검색 서비스                | `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java`                    |
| 검색 Repository            | `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java` |
| 리뷰 컨트롤러              | `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceReviewController.java`        |
| 리뷰 서비스                | `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceReviewService.java`              |
| 지오코딩 컨트롤러          | `backend/main/java/com/linkup/Petory/domain/location/controller/GeocodingController.java`                    |
| Naver Maps 연동            | `backend/main/java/com/linkup/Petory/domain/location/service/NaverMapService.java`                           |
| 관리자 Location API        | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminLocationController.java`                   |
| 공공데이터 적재            | `backend/main/java/com/linkup/Petory/domain/location/service/PublicDataLocationService.java`                 |

## 3. 검색 API

### `GET /api/location-services/search`

위치 서비스 검색의 단일 진입점이다.

요청 파라미터:

| 파라미터    | 단위/의미 | 비고                                                       |
| ----------- | --------- | ---------------------------------------------------------- |
| `latitude`  | 위도      | `longitude`와 함께 있으면 반경 검색                        |
| `longitude` | 경도      | `latitude`와 함께 있으면 반경 검색                         |
| `radius`    | 미터      | 없거나 `<= 0`이면 10,000m                                  |
| `sido`      | 시도      | 위치 파라미터가 없을 때 지역 검색                          |
| `sigungu`   | 시군구    | 위치 파라미터가 없을 때 지역 검색, `sido`보다 우선         |
| `category`  | 카테고리  | `category1/2/3` 중 하나와 일치하면 포함                    |
| `keyword`   | 검색어    | 경로에 따라 `LIKE` 또는 FULLTEXT                           |
| `sort`      | 정렬      | `stable`, `distance`, `rating`, `reviews`, `score`         |
| `size`      | 결과 상한 | 컨트롤러 기준 `null`이면 100, `<=0`이면 제한 없음으로 전달 |

응답:

```json
{
  "services": [],
  "count": 0
}
```

### 검색 분기

현재 컨트롤러의 분기 순서는 다음과 같다.

```text
latitude + longitude 있음
  -> 반경 검색

위치 없음 + sigungu/sido 있음
  -> 지역 검색

위치 없음 + 지역 없음 + keyword 있음
  -> FULLTEXT 키워드 검색

조건 없음
  -> 전체 평점순
```

중요한 점:

- keyword가 있어도 `latitude + longitude`가 있으면 반경 검색이 우선이다.
- 반경/지역 검색에서 keyword는 `name LIKE '%keyword%'` 조건이다.
- FULLTEXT는 위치와 지역 파라미터가 모두 없는 keyword 단독 경로에서만 사용한다.
- `category`는 세 경로 모두에서 SQL `WHERE` 조건으로 처리한다.

## 4. 메인 지도 경로

프론트 통합 지도는 `UnifiedPetMapPage`가 담당한다.

핵심 상태:

| 상태                | 의미                                |
| ------------------- | ----------------------------------- |
| `mapViewportCenter` | 사용자가 현재 보고 있는 지도 중심   |
| `searchCenter`      | 서버에 실제 검색 기준으로 보낸 중심 |
| `radius`            | UI 단위 km                          |
| `locationKeyword`   | 주변서비스 검색어                   |
| `locationCategory`  | 주변서비스 카테고리 필터            |
| `locationSort`      | 주변서비스 정렬, 기본값 `stable`    |

주변서비스 탭은 `searchCenter` 기준으로 조회한다. 지도 드래그는 `mapViewportCenter`만 바꾸고, 바로 API를 다시 호출하지 않는다. 사용자가 `"이 지역 검색"`을 누르거나 검색/카테고리/정렬을 바꿀 때 `searchCenter`를 갱신한다.

`unifiedMapApi.fetchActiveMapItems()`는 주변서비스 탭에서 다음 값을 보낸다.

```javascript
{
  latitude: lat,
  longitude: lng,
  radius: radiusKm * 1000,
  keyword,
  category,
  sort,
  size: 300
}
```

정책:

- 프론트 UI의 반경은 km이다.
- 백엔드 API의 `radius`는 m이다.
- `unifiedMapApi`가 `radius * 1000`으로 단위를 변환한다.
- Location 결과는 줌 레벨과 무관하게 `size=300`으로 고정한다.
- Meetup/Care만 줌 레벨 기반 limit을 유지한다.

## 5. 검색창 fallback

주변서비스 검색창은 한 번에 keyword만 보내지 않고 다음 순서로 fallback한다.

1. **Geocoding 우선**
   - `geocodingApi.searchPlaces(kw)`로 주소/장소 좌표 변환을 시도한다.
   - 성공하면 지도 중심과 검색 기준을 해당 좌표로 이동하고 keyword는 비운다.

2. **지역명 감지**
   - geocoding 실패 후 `구/군/시/도/특별시/광역시` 패턴을 확인한다.
   - 지역명으로 보이면 `sido` 또는 `sigungu` 파라미터로 직접 검색한다.
   - 이 경로는 `lat/lng`를 보내지 않으므로 백엔드 지역 검색을 탄다.

3. **시설명 keyword 검색**
   - 위 두 경우가 아니면 현재 지도 중심 반경 안에서 keyword 필터 검색을 수행한다.
   - 좌표가 함께 전달되므로 백엔드에서는 반경 검색 + `name LIKE` 경로가 된다.

## 6. 반경 검색

반경 검색은 `SpringDataJpaLocationServiceRepository.findByRadius`가 담당한다.

쿼리 특징:

- `ST_Within(ls.location, POLYGON(...))`으로 사각 후보를 먼저 줄인다.
- `ST_Distance_Sphere(ls.location, POINT(...)) <= :radiusInMeters`로 실제 반경 조건을 적용한다.
- `is_deleted = 0` 조건을 포함한다.
- keyword는 `ls.name LIKE CONCAT('%', :keyword, '%')`로 처리한다.
- category는 `category3`, `category2`, `category1` 중 하나와 일치하면 포함한다.
- `LIMIT :limit`을 SQL에 직접 적용한다.

서비스 레이어는 응답 DTO에 표시할 거리 값을 Haversine 공식으로 다시 계산해 넣는다.

## 7. 정렬

반경 검색의 `sort`는 다음 값을 받는다.

| 값         | 의미                                                       |
| ---------- | ---------------------------------------------------------- |
| `stable`   | 추천순. `rating DESC`, `review_count DESC`, `idx ASC` 중심 |
| `distance` | 거리순. 거리 → 평점 → idx                                  |
| `rating`   | 평점순. 평점 → 거리 → idx                                  |
| `reviews`  | 리뷰순. 리뷰 수 → 거리 → 평점 → idx                        |
| `score`    | score 내림차순                                             |

프론트 기본값은 `stable`이고 UI 라벨은 “추천순”이다. 거리순은 중심 좌표가 조금만 바뀌어도 결과 경계가 흔들릴 수 있으므로 기본 검색에는 안정적인 정렬을 사용한다.

## 8. 지역 검색

지역 검색은 위치 파라미터가 없고 `sigungu` 또는 `sido`가 있을 때만 실행된다.

우선순위:

```text
sigungu -> sido -> 전체 평점순
```

현재 백엔드 repository에는 `findBySigungu`, `findBySido`, `findByOrderByRatingDesc`가 활성화되어 있다. `eupmyeondong`, `roadName` 직접 검색 쿼리는 코드상 비활성화되어 있고, 메인 지도에서는 상세 주소나 읍면동 입력을 geocoding으로 좌표화한 뒤 반경 검색으로 처리하는 쪽에 가깝다.

## 9. 키워드 단독 FULLTEXT

위치 파라미터도 지역 파라미터도 없고 keyword만 있을 때 `findByNameContaining`이 실행된다.

쿼리:

```sql
MATCH(name, description, category1, category2, category3)
AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE)
```

사용 필드:

- `name`
- `description`
- `category1`
- `category2`
- `category3`

주의:

- 이 FULLTEXT 경로는 메인 지도 기본 검색 경로가 아니다.
- 메인 지도에서 현재 좌표와 함께 검색하면 반경 검색 + `name LIKE` 경로가 된다.

## 10. 리뷰

리뷰 API는 `/api/location-service-reviews` 아래에 있다.

| API                                                      | 설명                  |
| -------------------------------------------------------- | --------------------- |
| `POST /api/location-service-reviews`                     | 리뷰 생성             |
| `PUT /api/location-service-reviews/{reviewIdx}`          | 리뷰 수정             |
| `DELETE /api/location-service-reviews/{reviewIdx}`       | 리뷰 soft delete      |
| `GET /api/location-service-reviews/service/{serviceIdx}` | 특정 서비스 리뷰 목록 |
| `GET /api/location-service-reviews/user/{userIdx}`       | 특정 사용자 리뷰 목록 |

정책:

- 리뷰 API는 인증 사용자가 필요하다.
- 리뷰 작성자는 JWT의 현재 사용자 기준이며, 요청 본문의 user 정보는 신뢰하지 않는다.
- 한 사용자는 같은 서비스에 중복 리뷰를 작성할 수 없다.
- 리뷰 작성/수정/삭제에는 이메일 인증이 필요하다.
- 본인 리뷰만 수정/삭제할 수 있다.
- 삭제는 soft delete다.

리뷰 생성/수정/삭제 후 `LocationServiceReviewService.updateServiceReviewStats()`가 호출된다. repository는 DB 단일 UPDATE로 평균 평점과 리뷰 수를 갱신한다.

```sql
UPDATE locationservice SET
  rating = (SELECT COALESCE(AVG(r.rating), 0.0) ...),
  review_count = (SELECT COUNT(*) ...)
WHERE idx = :serviceIdx
```

이 방식은 애플리케이션에서 평균을 읽고 다시 저장하는 read-modify-write 흐름보다 동시 리뷰 상황의 Lost Update 위험이 낮다.

## 11. 지오코딩과 길찾기

`GeocodingController`는 Naver Maps 연동을 서버에서 감싼다.

| API                                                        | 설명               |
| ---------------------------------------------------------- | ------------------ |
| `GET /api/geocoding/address?address=...`                   | 주소를 좌표로 변환 |
| `GET /api/geocoding/search?query=...`                      | 주소 키워드 검색   |
| `GET /api/geocoding/coordinates?lat=...&lng=...`           | 좌표를 주소로 변환 |
| `GET /api/geocoding/directions?start=lng,lat&goal=lng,lat` | 길찾기             |

주의:

- 길찾기의 `start`, `goal`은 `경도,위도` 문자열이다.
- Naver Maps 키는 프론트가 아니라 서버 설정에 둔다.

## 12. 관리자와 데이터 적재

관리자 API는 `/api/admin/location-services` 아래에 있다.

| API                                                         | 권한              | 설명                   |
| ----------------------------------------------------------- | ----------------- | ---------------------- |
| `GET /api/admin/location-services`                          | `ADMIN`, `MASTER` | 위치 서비스 목록 조회  |
| `POST /api/admin/location-services/load-data`               | `MASTER`          | 초기 데이터 로드       |
| `POST /api/admin/location-services/import-public-data`      | `MASTER`          | CSV 파일 업로드 임포트 |
| `POST /api/admin/location-services/import-public-data-path` | `MASTER`          | CSV 파일 경로 임포트   |

CSV 업로드는 확장자, Content-Type, 최대 크기 200MB를 검증한다. 공공데이터 적재는 `PublicDataLocationService`가 처리하고, 배치 저장은 별도 writer를 통해 트랜잭션을 분리한다.

## 13. 추천 도메인 연동

Location 검색어는 `LocationSearchPerformedEvent`로 추천 도메인에 전달될 수 있다. 이벤트 클래스는 발행자인 location 도메인이 소유한다(`domain/location/event`) — 2026-07 리팩토링으로 petRecommendation에서 이동해 `location→petRecommendation` 역방향 의존을 제거했다.

흐름:

1. 인증 사용자가 keyword 검색을 수행한다.
2. `LocationServiceService.publishSearchEvent()`가 현재 사용자를 확인한다.
3. `LocationSearchPerformedEvent`가 발행된다.
4. 추천 도메인이 사용자 intent signal을 갱신한다.
5. 주변서비스 탭은 signal을 추천 카드로 보여준다.
6. 추천 카드를 누르면 기존 Location `category` 검색으로 연결한다.

추천 signal은 보조 기능이다. signal 조회가 실패하면 프론트는 빈 배열로 처리하고 Location 검색 자체는 계속 동작한다.

## 14. 한계와 개선

- 메인 지도 기본 경로는 반경 검색 중심이다. 지역 계층 검색은 백엔드에 남아 있지만 현재 주 사용자 경로에서 적극적으로 쓰이지 않는다.
- `eupmyeondong`, `roadName` 직접 검색 쿼리는 현재 비활성화되어 있다.
- 위치/지역 검색의 keyword는 `name LIKE '%keyword%'`라 FULLTEXT 단독 검색과 검색 범위가 다르다.
- `LIKE '%keyword%'`는 인덱스 효율이 낮지만, 반경/지역 후보를 먼저 줄인 뒤 적용하는 구조다.
- 통합 지도 Location 결과는 `size=300`으로 고정되어 밀집 지역에서는 이후 후보가 잘릴 수 있다.
- `stable` 정렬은 안정성 중심이며, 거리 가까움을 최우선으로 보장하지 않는다.
- `LocationServiceController`, `LocationServiceReviewController`, `GeocodingController`에는 아직 수동 try-catch 응답 조립이 남아 있어 전역 예외 처리로 더 정리할 수 있다.
- 뷰포트 바운딩 박스 검색은 현재 API 구조 변경 폭이 커서 장기 개선안으로 남아 있다.

## 15. 관련 문서

- [위치 기반 서비스 아키텍처](<../architecture/location/위치 기반 서비스 아키텍처.md>)
- [Location 지도 결과 안정성 리팩토링](../refactoring/location/지도-결과-안정성-리팩토링.md)
- [Location 프론트 검색 워크플로우 정리](../refactoring/location/지도-검색-워크플로우-정리.md)
- [주변서비스 현행 vs 설계안 비교](../refactoring/location/주변서비스-현행vs설계안-비교.md)
- [Location 도메인 초기 로드 성능 문제](../troubleshooting/location/initial-load-performance.md)
- [지도 서비스 UX 개선 트러블슈팅](../troubleshooting/location/map-ux-improvement.md)
- [Location 도메인 Fetch 전략 개선](<../refactoring/fetch-optimization/location/Fetch 전략 개선 (Fetch Join vs Batch Size).md>)
- [Location 도메인 예외처리 리팩토링](../refactoring/exception/location/위치예외처리.md)
