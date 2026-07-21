# 위치서비스 공공데이터 오픈API 동기화 파이프라인

> **현행 기준 안내 (2026-07 구현)**: 현재 사실의 단일 진실은 [`docs/domains/location.md`](../../domains/location.md) 이다. 이 문서는 파이프라인의 설계·구현 배경을 코드 기준으로 정리한다.
>
> 설계 스펙: [`docs/superpowers/specs/2026-07-21-public-data-location-sync-design.md`](../../superpowers/specs/2026-07-21-public-data-location-sync-design.md)
> harness phase: `phases/public-data-location-sync/`

## 개요

관리자가 CSV를 손으로 내려받아 업로드하던 [CSV 배치 임포트](<위치서비스_공공데이터_CSV_배치_임포트_구현.md>)와 별개로, **data.go.kr odcloud 오픈API**(한국문화정보원 전국 반려동물 동반 가능 문화시설 위치 데이터, ID `15111389`, 약 7만 건)를 주기적으로 받아 `location_service` 테이블에 **멱등 upsert** 하는 자동 파이프라인이다.

CSV 경로는 그대로 두고(수동 백업 수단), 소스만 "수동 CSV 파일"에서 "API 자동 호출"로 확장했다.

## 구현 배경

- 기존에는 시설 데이터 자동 갱신 수단이 없었다. 과거 별도 Python 서비스(`pet-data-api`)가 공공데이터 + 블로그 크롤링을 조합했으나, 크롤링의 약관 리스크로 전체가 제거되고 신호 기반 NLP 추천으로 대체되었다.
- 맵 API(Naver/Google) 응답을 자체 DB에 영구 저장하는 것은 대부분 약관 위반이다. 반면 공공데이터는 출처 표시 조건으로 재배포·저장이 허용된다. 그래서 "검색 가능한 자체 시설 DB"는 공공데이터로 채우고, 맵 API는 조회·좌표 변환에만 쓰는 이원화 구조를 따른다.

## 시스템 아키텍처

### 전체 흐름

```
[data.go.kr odcloud API] ──▶ PublicDataApiClient (RestClient, 페이징 순회 + 재시도)
                                     │  한글키 정규화 매핑
                                     ▼
                           PublicDataLocationDTO
                                     │
                                     ▼
                       PublicDataSyncService (멱등 upsert)
                                     │
                 ┌───────────────────┴────────────────────┐
                 ▼                                         ▼
      LocationServiceBatchWriter                    location_sync_log
      (saveBatch / updateBatch,                     (run 당 1행: 상태·건수)
       @Transactional REQUIRES_NEW)
                 │
                 ▼
          location_service (기존 테이블)

트리거:
- PublicDataSyncScheduler  @Scheduled(cron "0 0 3 * * *")  매일 03:00
- AdminLocationController   POST /api/admin/location-services/sync-public-data  [MASTER]
```

### 주요 컴포넌트

| 컴포넌트 | 파일 | 책임 |
| --- | --- | --- |
| `PublicDataApiClient` | `domain/location/service/PublicDataApiClient.java` | odcloud 페이징 호출, 한글키→DTO 매핑, 재시도, 서비스키 인코딩 |
| `PublicDataSyncService` | `domain/location/service/PublicDataSyncService.java` | 조회→INSERT/UPDATE/skip 분기, 실행이력 기록 |
| `LocationServiceBatchWriter` | `domain/location/service/LocationServiceBatchWriter.java` | `saveBatch`(신규)·`updateBatch`(갱신) 배치 저장, 트랜잭션 분리 |
| `PublicDataSyncScheduler` | `domain/location/service/PublicDataSyncScheduler.java` | 매일 03:00 자동 실행, 예외 격리 |
| `LocationSyncLog` | `domain/location/entity/LocationSyncLog.java` | 실행이력 엔티티(`location_sync_log`, V8) |

## 구현 로직

### 1. odcloud 페이징 호출

odcloud 파일데이터 자동변환 API의 표준 포맷을 따른다.

- URL: `https://api.odcloud.kr/api/15111389/v1/uddi:41944402-8249-4e45-9e9d-a52d0a7db1cc`
- 쿼리: `page`(1부터), `perPage`, `returnType=JSON`, `serviceKey`
- 응답: `{ "page", "perPage", "totalCount", "currentCount", "matchCount", "data": [ {한글키:값} ] }`

`fetchAll()`이 `page`를 1씩 올리며 `data`가 비거나 누적 건수가 `totalCount`에 도달할 때까지 순회한다. 각 페이지는 `fetchPage()`가 담당하며 실패 시 최대 2회 재시도한다.

### 2. 서비스키 URL 인코딩 (실제로 겪은 버그)

디코딩 서비스키에는 `+`, `/`, `=`가 들어있다. 쿼리 문자열에서 `+`는 공백으로 해석되므로, 인코딩 없이 보내면 서버가 `등록되지 않은 인증키(401)`로 거부한다. `URLEncoder.encode(serviceKey, UTF_8)`로 `+`→`%2B` 변환 후 `build(true)`로 재인코딩을 막는다. 실제 라이브 호출로 재현·검증했다.

### 3. 한글 컬럼키 매핑

응답 `data` 항목의 키는 한글 컬럼명(예: `"반려동물 동반 가능정보"`, `"기본 정보_장소설명"`)이며 공백·괄호·언더스코어 표기가 섞여 있다. `normalizeKey`가 이 문자들을 모두 제거해 `KEY_TO_FIELD`(정규화 키 31개)와 매칭한다. 표기가 조금 달라도 흡수되며, 라이브 응답 31개 컬럼이 전부 매핑됨을 확인했다.

### 4. 멱등 Upsert

`PublicDataSyncService`가 DTO를 엔티티로 변환(`PublicDataLocationService`의 변환·검증·중복키 로직을 재사용)한 뒤:

1. 같은 실행 내 중복(시설명+주소)은 skip.
2. `findFirstByNameAndAddress`로 기존 행 조회.
3. 없으면 `insertBatch`에 추가(신규 INSERT).
4. 있고 내용이 다르면 `updateBatch`에 추가(UPDATE), 동일하면 skip.

**핵심 — 앱 관리 필드 보존**: UPDATE 시 신규 엔티티에 `idx`만 세팅해 `saveAll` 하면 `rating`·`reviewCount`·`isDeleted`·`geo_point`·`createdAt` 등 공공데이터가 주지 않는 컬럼이 기본값으로 덮어써져 별점·리뷰수가 소실된다. 그래서 기존 엔티티를 로드해 `copyPublicFields`로 **공공데이터 필드만** 복사하고 나머지는 건드리지 않는다.

### 5. 배치 저장과 트랜잭션 분리

`LocationServiceBatchWriter`는 `@Transactional(REQUIRES_NEW)`로 배치마다 독립 트랜잭션을 연다(AOP self-invocation 회피 위해 별도 빈). 신규는 `saveBatch`(실패 시 idx 초기화 후 개별 재시도), 갱신은 `updateBatch`(idx 유지 merge)로 나뉜다. 배치 크기는 1000이다.

### 6. 실행 이력 (location_sync_log, V8)

run 당 1행을 기록한다.

| 컬럼 | 의미 |
| --- | --- |
| `started_at` / `finished_at` | 실행 시작·종료 시각 |
| `status` | `SUCCESS`(전량 성공) / `PARTIAL`(일부 배치 실패) / `FAILED`(API 조회 자체 실패) |
| `total_fetched` / `inserted` / `updated` / `skipped` / `failed` | 건수 집계 |
| `trigger_type` | `SCHEDULED` / `MANUAL` |
| `error_message` | 실패 시 원인(최대 1000자) |

## 트리거와 설정

- **스케줄러**: `PublicDataSyncScheduler.runDailySync()`가 `@Scheduled(cron = "0 0 3 * * *")`로 매일 03:00 실행. `@EnableScheduling`은 `SchedulingConfig`가 `petory.scheduling.enabled`(기본 true)로 중앙 게이팅한다. 스케줄러는 서비스 예외를 `try/catch`로 감싸 스레드가 죽지 않게 한다.
- **수동 트리거**: `POST /api/admin/location-services/sync-public-data` [MASTER] → 결과 요약(JSON) 반환.
- **설정 키**:
  - `app.public-data.base-url` — 기본값이 고정 odcloud 엔드포인트라 미설정이어도 컨텍스트 로드된다.
  - `app.public-data.service-key` — 비우면 동기화만 `FAILED`로 남고 앱은 정상 기동. gitignore된 로컬 설정/`.env`에만 둔다.
  - `app.public-data.page-size` — 기본 1000.

## 발생한 오류 및 해결 방법

### 오류 1: 서비스키 `+` 공백 오해석 (401)

디코딩 키의 `+`가 쿼리에서 공백으로 해석돼 `등록되지 않은 인증키`가 났다. `URLEncoder` + `build(true)`로 해결.

### 오류 2: CI 컨텍스트 로드 실패 (PlaceholderResolutionException)

`@Value("${app.public-data.base-url}")`에 기본값이 없어, CI 더미 설정(`.github/ci/application-ci.properties`)에 해당 프로퍼티가 없자 `PetoryApplicationTests`의 전체 컨텍스트 로드가 실패했다. `@Value` 기본값(고정 엔드포인트) + CI 설정 파일에 프로퍼티 명시로 해결.

## 테스트

- `PublicDataApiClientTest` — 한글키 매핑 / 빈 페이지 처리(2건).
- `PublicDataSyncServiceTest` — 신규 INSERT / 변경 UPDATE / 무변경 skip / API 실패(FAILED) / 부분 실패(PARTIAL)(5건).
- `PublicDataSyncSchedulerTest` — SCHEDULED 위임 / 예외 격리(2건).
- 실제 odcloud API 라이브 호출로 인증·매핑 end-to-end 검증(임시 테스트, 커밋하지 않음).

## 관련 문서

- [Location 도메인 스펙](../../domains/location.md)
- [위치서비스 공공데이터 CSV 배치 임포트 구현](<위치서비스_공공데이터_CSV_배치_임포트_구현.md>)
- [위치 기반 서비스 아키텍처](<위치 기반 서비스 아키텍처.md>)
