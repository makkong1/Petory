# Recommendation 도메인 - 맞춤 추천 (Pet Data API)

## 1. 도메인 개요

### 1.1 역할

Petory 백엔드는 이제 모든 컨텍스트를 외부 추천 서버에 그대로 프록시하지 않습니다.

- **Track A (`grooming`, `hospital`, `pharmacy`, `cafe`, `restaurant`, `pension`, `boarding`, `hotel`, `supplies`)**
  - Petory가 `LocationService` 구조화 저장소에서 **nearby 후보를 직접 조회**
  - `pet-data-api`에서는 **`popular` / `trends` 시그널만 조회**
  - 최종 조합과 정렬, 응답 DTO 조립은 **Petory 서비스 레이어**가 담당
  - 시설 데이터는 Python batch CLI → JSON → `LocationImportService`를 통해 `locationservice` DB에 적재
- **Track B (비-시설 카테고리: `snack`, `food`, `clothes`)**
  - 기존 `PetDataApiClient.recommend()` 경로를 유지
  - 구조화 시설 마스터가 없는 트렌드 중심 카테고리라서 Petory owner 전환 대상이 아님
  - `snack` / `food` / `clothes`는 `PetDataApiClient`에서 경로 alias `supplies`로 정규화되어 호출됨

### 1.2 Location 추천과의 구분

| 구분        | Recommendation 도메인                                           | Location (별도)                                                           |
| ----------- | --------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 경로        | `GET /api/recommend`                                            | `GET /api/location-services/recommend` 등                                 |
| 데이터 소스 | Petory `LocationService` + 외부 `pet-data-api` popularity/trend | `LocationService` + `LocationRecommendAgentService` 등 Petory DB/에이전트 |
| 용도        | nearby 후보 + popularity/trend 조합 추천                        | 주변 위치 서비스 목록 + AI 이유 enrich                                    |

### 1.3 핵심 원칙

- **로그인 전용**: 비인증 요청은 `UnauthenticatedException`으로 처리
- **전용 JPA 엔티티 없음**: 추천 결과를 Petory MySQL에 영속하지 않음
- **반려동물은 User 도메인 `Pet`에서 조회**: `findByUserIdAndNotDeleted` 첫 항목만 사용

### 1.4 Location AI 추천 통합 — 완료

`GET /api/location-services/recommend` 및 `LocationRecommendAgentService`는 코드베이스에서 **제거 완료**. 해당 컨트롤러·서비스 클래스 파일이 존재하지 않으며, “주변 서비스 추천” 목적은 `GET /api/recommend`(본 도메인)로 단일화되었다. `docs/domains/location.md` §4.4 참고.

---

## 2. API

모든 경로는 `/api/recommend/**` 이며 **인증 필요**. (위 **1.3 핵심 원칙** 참고.)

### 2.1 엔드포인트 요약

- **`GET`** `/api/recommend` — `lat`, `lng`, `context` 로 추천, 응답은 `RecommendResponse`.
- **`POST`** `/api/recommend/copy` — 시설·트렌드 payload 로 LLM 카피 생성.
- **`POST`** `/api/recommend/events` — 요청별 행동 이벤트 기록(`userId` SHA-256 해시 → `userRef`; 현재 전송 스킵).
- **`GET`** `/api/recommend/trends/{category}/timeseries` — 트렌드 시계열.

### 2.2 `GET /api/recommend`

| 구분          | 내용                                                          |
| ------------- | ------------------------------------------------------------- |
| 쿼리          | `lat`, `lng`, `context` 필수 (`double`, `double`, `String`)   |
| 인증          | `Authentication#getName()` → `userId`                         |
| 성공          | `200` + `RecommendResponse`                                   |
| 본문 없음     | 서비스가 `null`이면 `503`(바디 없음; 현 코드 경로에서는 드묾) |
| 외부 I/O 실패 | `PetDataApiClient` 예외 → 전역 핸들러(대개 5xx)               |

### 2.3 부가 엔드포인트

**`POST` `/api/recommend/copy`**

- 본문: `RecommendCopyRequest`(context, request_id, facilities, trends, pet) → `RecommendCopyResponse`
- `pet` 는 백엔드에서 보강 가능

**`POST` `/api/recommend/events`**

- 본문: `RecommendEventRequest`(request_id, events[]) → 응답 `202`
- `userId` 는 `SHA-256` 앞 12자 해시(`petory-xxxxxxxxxxxx`)로 변환해 `userRef` 필드에 주입
- 현재 pet-data-api에 이벤트 수집 엔드포인트가 없어 **실제 전송은 스킵** (`sendEvents()`는 debug 로그만 출력). 추후 엔드포인트 구현 시 전송 로직 추가 예정

**`GET` `/api/recommend/trends/{category}/timeseries`**

- 쿼리: `days`(기본 14), `top_keywords`(기본 10)
- 응답: `TrendTimeseriesResponse`(category, points: date, keyword, score)
- pet-data-api는 현재 시점 스냅샷(1회 데이터)만 제공한다. `PetDataApiClient`는 이 스냅샷의 각 키워드를 `days`일치 포인트로 복제해 Recharts 차트 호환 시계열로 변환(`expandSnapshotToSyntheticSeries`)한다. 실제 일별 변화 데이터는 아님.

### 2.4 응답 DTO (`RecommendResponse`)

```java
public record RecommendResponse(
    String context,
    @JsonProperty("recommend_version") String recommendVersion,
    @JsonProperty("request_id")        String requestId,
    List<FacilityItem>                 facilities,
    List<TrendItem>                    trends,
    String                             recommendation,
    @JsonProperty("generated_at")      String generatedAt)
```

- `context`: 요청한 context 문자열 그대로 echo
- `recommend_version`: 응답 생성 경로 식별자 (`"petory-nearby-v1"` / `"popular-intelligence-v1"`)
- `request_id`: 요청 추적용 UUID (Petory 내부 생성)
- `facilities`: 시설 후보 — `name`, `distance_m`, `address`, `lat`, `lng`
- `trends`: 트렌드 — `keyword`, `score`
- `recommendation`: rule-based 또는 자연어 추천 문구
- `generated_at`: 응답 생성 시각 (ISO 8601)

---

## 3. 서비스 흐름

1. `RecommendController`가 `userId`, `lat`, `lng`, `context`를 `RecommendService`에 전달
2. `PetRepository.findByUserIdAndNotDeleted(userId)`로 반려동물 목록 조회
3. **펫이 있으면** 첫 요소(`pets.get(0)`)로 `PetInfo` 구성
   - `type`: `petType.name().toLowerCase()`
   - `breed`: 품종
   - `age_months`: `birthDate`가 있을 때만 `ChronoUnit.MONTHS`로 계산, 없으면 `null`
4. **Track A면**
   - `LocationServiceService.searchLocationServicesByLocation(...)`로 반경 10km 후보 최대 20개 조회
   - `PetDataApiClient.fetchPopular(context, ...)` — popular 시그널 목록 조회
   - `PetDataApiClient.fetchTrends(context, ...)` — 트렌드 키워드 최대 15개 조회
   - **이름 정규화 + alias 조인**: 상호명에서 업종 suffix(예: `미용`, `애견미용실`, `동물병원`)를 제거한 별칭을 생성해 popular 시그널 인덱스와 매칭. 예) "피플앤독 미용" → "피플앤독" alias로 popular 시그널 찾음
   - **가중 스코어링 후 상위 5개** 반환:
     | 요소 | 가중치 |
     |------|--------|
     | 거리 (가까울수록 높음) | **55%** |
     | 평점 (`rating / 5.0`) | 20% |
     | 리뷰 수 (`log(n+1)` 정규화) | 15% |
     | 인기도 (블로그 언급 점수·횟수) | 10% |
   - 동점 시 거리순 → 이름 사전순으로 tie-break
5. **Track B면**
   - `RecommendRequest` 빌드: `lat`, `lng`, `context`, **고정** `radius_km=10.0`, `top_n=5`, `pet`(또는 `null`)
   - `PetDataApiClient.recommend(request)` 호출
     - **내부에서** `GET /popular/{context}` + `GET /trends/{category}` 를 각각 직접 호출
     - `lat`/`lng`/`radius_km`은 실제 사용되지 않음 (`topN`, `context`만 사용)
     - `RecommendResponse`를 rule-based 추천 문구와 함께 로컬 조립 (POST /recommend 없음)

### 3.1 `RecommendRequest` DTO

```java
public record RecommendRequest(
    double lat, double lng, String context,
    @JsonProperty("radius_km") double radiusKm,
    @JsonProperty("top_n")     int topN,
    PetInfo pet)
```

- **Track A**: 이 DTO는 사용하지 않음 (Petory가 직접 popular/trends 호출)
- **Track B**: `PetDataApiClient.recommend(request)` 인자로 전달되지만,  
  내부에서 `context`와 `topN`만 사용. `lat`/`lng`/`radiusKm`은 무시됨.  
  pet-data-api에 직접 직렬화 전송되지 않음.

---

## 4. 클라이언트 (`PetDataApiClient`)

- **등록**: `@Component` — 애플리케이션 빈으로 등록
- **내부**: 생성자에서 `RestClient.builder()...`로 `RestClient` 구성, `ObjectMapper`는 인스턴스 필드에 `new ObjectMapper()` (별도 `@Bean` 아님)
- **설정**
  - `app.pet-data-api.base-url` — Pet Data API 베이스 URL (필수)
  - `app.pet-data-api.api-key` — `X-API-Key` (빈 문자열 가능하나, 운영 시 필수)
  - `app.pet-data-api.timeout-ms` — popular·trends 호출 타임아웃 (기본 3000ms)
  - `app.pet-data-api.copy-timeout-ms` — 생성자 파라미터로 수신하나 **현재 미사용** (copy 호출이 로컬 규칙 기반으로 전환됨)
- **RestClient 구성**
  - `recommendClient` — popular·trends·copy 호출용, `timeout-ms` 적용
  - `facilityClient` — 레거시 `/facilities` 페이지네이션용, **30초 하드코딩** (현 pet-data-api에 `/facilities` 없어 404 폴백)
- **주 역할**
  - `fetchPopular(context, limit, correlationId)` → `GET /popular/{context}` (`snack`/`food`/`clothes` → `supplies` 경로 alias 자동 변환)
  - `fetchTrends(context, limit, correlationId)` → `GET /trends/{category}`
  - `recommend(request)` — Track B/레거시 호환: pet-data-api에 `POST /recommend` 없으므로 `fetchPopular` + `fetchTrends`를 조합해 `RecommendResponse`를 **로컬에서 조립**
  - `recommendCopy(request)` — LLM 카피 엔드포인트 없어 **규칙 기반 문구를 로컬 생성**
  - `sendEvents(request)` — pet-data-api 이벤트 엔드포인트 미구현으로 **현재 스킵** (debug 로그만)
- **실패 처리**
  - `fetchPopular` / `fetchTrends`: 404(미지원 context) · 503(빈 캐시)는 빈 리스트 반환, 그 외는 `RuntimeException` throw
  - `recommend()` / `getTrendTimeseries()`: 예외를 `RuntimeException`으로 래핑 — 상위 컨트롤러에서 `null` 반환 경로는 없음

---

## 5. 데이터베이스

Recommendation 도메인 **전용 테이블은 없습니다.**  
`Pet` 조회는 `com.linkup.Petory.domain.user.repository.PetRepository`를 사용합니다.

---

## 6. 운영 시 체크리스트

- `application.properties`에 `app.pet-data-api.base-url`, `app.pet-data-api.api-key` 설정
- Pet Data API가 내려가면 `PetDataApiClient` 예외 → 사용자는 5xx/에러 응답을 볼 수 있으므로, 모니터링·재시도 정책은 API 쪽 또는 Resilience4j 도입 시 검토 ㅋ
- `context` 값은 **프론트·기획**과 Pet Data API가 합의한 자유 형식 문자열(예: 화면명, 시나리오 키)

--

## 7. 관련 코드

- **REST** — `RecommendController`
- **애플리케이션 서비스** — `RecommendService`
- **nearby 후보 조회** — `LocationServiceService`
- **HTTP 클라이언트** — `PetDataApiClient`
- **DTO** — `RecommendRequest`, `RecommendResponse`, `RecommendCopyRequest`, `RecommendCopyResponse`, `RecommendEventRequest`, `TrendTimeseriesResponse`

## 8. 관련 아키텍처 문서

- [Pet Data API & Petory Recommendation — 통합 아키텍처](../architecture/pet-data-api%20architecture.md) — pet-data-api 내부 구조, E2E 다이어그램, `context`·요청/응답 계약, 운영 시 장애 시나리오
