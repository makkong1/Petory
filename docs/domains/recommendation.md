# Recommendation 도메인 - 맞춤 추천 (Pet Data API)

## 1. 도메인 개요

### 1.1 역할

Petory 백엔드는 이제 모든 컨텍스트를 외부 추천 서버에 그대로 프록시하지 않습니다.

- **Track A (`grooming`, `hospital`, `pharmacy`, `cafe`, `restaurant`, `pension`, `boarding`, `hotel`)**
  - Petory가 `LocationService` 구조화 저장소에서 **nearby 후보를 직접 조회**
  - `pet-data-api`에서는 **`popular` / `trends` 시그널만 조회**
  - 최종 조합과 정렬, 응답 DTO 조립은 **Petory 서비스 레이어**가 담당
  - `boarding`, `hotel`은 2026-05-25 기준 전환 완료 — `GET /facilities` 구현 및 87개 시설 DB 적재 후 편입
- **Track B (비-시설 카테고리: `supplies`, `snack`, `food`, `clothes`)**
  - 기존 `PetDataApiClient.recommend()` 경로를 유지
  - 구조화 시설 마스터가 없는 트렌드 중심 카테고리라서 Petory owner 전환 대상이 아님

### 1.2 Location 추천과의 구분

| 구분        | Recommendation 도메인                                | Location (별도)                                                           |
| ----------- | ---------------------------------------------------- | ------------------------------------------------------------------------- |
| 경로        | `GET /api/recommend`                                 | `GET /api/location-services/recommend` 등                                 |
| 데이터 소스 | Petory `LocationService` + 외부 `pet-data-api` popularity/trend | `LocationService` + `LocationRecommendAgentService` 등 Petory DB/에이전트 |
| 용도        | nearby 후보 + popularity/trend 조합 추천             | 주변 위치 서비스 목록 + AI 이유 enrich                                    |

### 1.3 핵심 원칙

- **로그인 전용**: 비인증 요청은 `UnauthenticatedException`으로 처리
- **전용 JPA 엔티티 없음**: 추천 결과를 Petory MySQL에 영속하지 않음
- **반려동물은 User 도메인 `Pet`에서 조회**: `findByUserIdAndNotDeleted` 첫 항목만 사용

### 1.4 로드맵 — Location AI 추천과의 통합·폐기 예정

사용자 경험상 **“주변 서비스 추천”**은 Location 도메인의 `GET /api/location-services/recommend`(MySQL 검색 후 `LocationRecommendAgentService`/`Ollama`로 재순위·이유)과 **동일 목적 영역에서 겹친다.**

- **현재**: 두 경로 모두 유지(회귀·프론트 의존성 방지).
- **향후**: **Pet Data API(Python)가 `GET /api/recommend` 기준으로 기대 동작까지 안정 검증된 뒤**, Location **`/api/location-services/recommend`(및 해당 에이전트)·통합 지도 AI 모드**를 Pet Data 계약 한쪽으로 합치고 **코드/API 제거**할 예정이다. 시행 시점 확정 후 `docs/domains/location.md`·본 문서에 상태를 업데이트한다.

---

## 2. API

### 2.1 `GET /api/recommend`

| 항목                                     | 내용                                                                                 |
| ---------------------------------------- | ------------------------------------------------------------------------------------ |
| 메서드                                   | `GET`                                                                                |
| 쿼리                                     | `lat` (필수, `double`), `lng` (필수, `double`), `context` (필수, `String`)           |
| 인증                                     | Spring Security — `Authentication`에서 `userId` = `auth.getName()`                   |
| 성공                                     | `200` + `RecommendResponse` JSON                                                     |
| 응답 없음(현행 서비스는 거의 사용 안 함) | 서비스가 `null`이면 `503` (바디 없음)                                                |
| 외부 API 실패                            | `PetDataApiClient`가 예외 throw → 애플리케이션 전역 예외 처리에 따름(일반적으로 5xx) |

### 2.2 응답 DTO (`RecommendResponse`)

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
   - `LocationServiceService.searchLocationServicesByLocation(...)`로 반경 10km 후보 조회
   - `PetDataApiClient.fetchPopular(context, ...)`
   - `PetDataApiClient.fetchTrends(context, ...)`
   - 이름 normalize 기반으로 popularity 시그널 조인 후 Petory가 최종 정렬
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
- **주 역할**
  - `fetchPopular(context, limit, correlationId)` → `GET /popular/{context}`
  - `fetchTrends(context, limit, correlationId)` → `GET /trends/{category}`
  - `recommend(request)`는 Track B/레거시 호환용 조합 메서드로만 유지
- **실패**: 예외는 래핑하여 `RuntimeException`으로 throw — 상위에서 `null`이 반환되는 경로는 현재 `RecommendService`에 없음

---

## 5. 데이터베이스

Recommendation 도메인 **전용 테이블은 없습니다.**  
`Pet` 조회는 `com.linkup.Petory.domain.user.repository.PetRepository`를 사용합니다.

---

## 6. 운영 시 체크리스트

- `application.properties`에 `app.pet-data-api.base-url`, `app.pet-data-api.api-key` 설정
- Pet Data API가 내려가면 `PetDataApiClient` 예외 → 사용자는 5xx/에러 응답을 볼 수 있으므로, 모니터링·재시도 정책은 API 쪽 또는 Resilience4j 도입 시 검토
- `context` 값은 **프론트·기획**과 Pet Data API가 합의한 자유 형식 문자열(예: 화면명, 시나리오 키)

---

## 7. 관련 코드

| 역할                | 클래스                                  |
| ------------------- | --------------------------------------- |
| REST                | `RecommendController`                   |
| 애플리케이션 서비스 | `RecommendService`                      |
| nearby 후보 조회     | `LocationServiceService`                |
| HTTP 클라이언트     | `PetDataApiClient`                      |
| DTO                 | `RecommendRequest`, `RecommendResponse` |

## 8. 관련 아키텍처 문서

- [Pet Data API & Petory Recommendation — 통합 아키텍처](../architecture/pet-data-api%20architecture.md) — pet-data-api 내부 구조, E2E 다이어그램, `context`·요청/응답 계약, 운영 시 장애 시나리오
