# 공공데이터 위치 서비스 동기화 파이프라인 설계

## 배경 및 목적

Location 도메인의 시설 데이터(`location_service`)는 현재 관리자가 공공데이터포털에서 CSV를 수동으로 다운받아 `AdminLocationController` → `PublicDataLocationService.importFromCsv`로 업로드하는 방식으로만 채워진다. 자동 갱신 수단이 전혀 없다.

과거 별도 Python 서비스(`pet-data-api`)가 공공데이터 + 네이버 블로그 크롤링을 조합해 이 문제를 해결하려 했으나, 블로그 크롤링 파트의 이용약관 리스크 등으로 전체가 삭제되고 현재의 신호 기반 NLP 추천(`petRecommendation` + `petory-nlp-server`)으로 대체되었다.

이번 작업의 목적은 **포트폴리오/면접용 기술 시연**이다 — 실서비스 안정성보다 데이터 파이프라인 설계 패턴(스케줄링, 외부 API 연동, 멱등성 upsert, 에러 처리, 실행이력 추적)을 제대로 갖추는 데 집중한다.

## 범위

- 대상 데이터셋: **한국관광공사 "반려동물 동반여행 가능정보"** (현재 CSV 임포터가 이미 이 데이터셋의 필드 구조(`PublicDataLocationDTO`)에 맞춰져 있음) 단 하나만 타겟으로 한다. 다른 공공데이터셋 추가는 이번 범위 밖.
- 소스만 "관리자가 다운받은 CSV 파일"에서 "data.go.kr Open API 직접 호출"로 바꾼다. CSV 업로드 경로 자체는 유지(수동 백업 수단으로 남겨둠).

## 아키텍처

```
[data.go.kr Open API] ──▶ PublicDataApiClient (신규, RestClient, 페이징 처리)
                                  │
                                  ▼
                        PublicDataLocationDTO (기존 재사용)
                                  │
                                  ▼
                  PublicDataSyncService (신규 — upsert 로직 담당)
                                  │
                   ┌──────────────┴───────────────┐
                   ▼                               ▼
        LocationServiceBatchWriter          location_sync_log
        (기존 + upsert 메서드 추가)         (신규 테이블, 실행이력)
                   │
                   ▼
        LocationService 테이블 (기존)

트리거:
- PublicDataSyncScheduler (@Scheduled, 신규) — 매일 새벽 자동 실행
- AdminLocationController에 POST /api/admin/location-services/sync-public-data 추가 — 관리자 수동 실행
```

기존 코드 재사용 항목: `PublicDataLocationDTO`, `convertToEntity`, `buildDedupKey`(시설명+주소), `LocationServiceBatchWriter`. 신규 항목: `PublicDataApiClient`, `PublicDataSyncService`, `PublicDataSyncScheduler`, `location_sync_log` 테이블.

## 컴포넌트 상세

### PublicDataApiClient (신규)

- `NaverMapService`와 동일한 패턴: `RestClient` 기반, `@Value`로 서비스키 주입 (`app.public-data.service-key`).
- data.go.kr Open API는 페이지 단위 응답(`pageNo`, `numOfRows`, `totalCount`)이므로, 전체 페이지를 순회하며 `PublicDataLocationDTO` 리스트로 변환해 반환.
- 타임아웃 10초, 실패 시 최대 2회 재시도(고정 backoff).

### PublicDataSyncService (신규)

핵심 메서드: `syncFromApi(triggerType: SCHEDULED|MANUAL) -> SyncResult`

1. `PublicDataApiClient`로 전체 데이터 조회 (페이지 단위)
2. 각 행에 대해:
   - `buildDedupKey`(기존 로직 재사용)로 기존 행 조회
   - 없으면 INSERT
   - 있으면 `PublicDataLocationDTO`의 모든 매핑 필드(위경도, 주소류, 운영시간, 휴무일, 전화번호, 반려동물 제한사항, 주차가능여부 등 `LocationService` 엔티티 컬럼과 매핑되는 필드 전체)를 비교 → 하나라도 다르면 UPDATE, 완전히 동일하면 스킵
3. 페이지 단위로 실패해도 나머지 페이지는 계속 진행 (전체 롤백 안 함)
4. 실행 종료 시 `location_sync_log`에 결과 기록

### LocationServiceBatchWriter (기존 확장)

- 기존 `saveBatch`(INSERT 전용)는 그대로 유지
- 신규 `upsertBatch` 메서드 추가: 변경분만 UPDATE, 신규만 INSERT

### PublicDataSyncScheduler (신규)

- 기존 `StatisticsScheduler`, `MeetupChatRoomRecoveryScheduler`와 동일한 `@Scheduled` cron 패턴
- 기본 매일 1회 새벽 실행 (구체 시각은 구현 시 `application.properties`로 설정 가능하게)

### 관리자 수동 트리거 (신규)

- `AdminLocationController`에 `POST /api/admin/location-services/sync-public-data` 추가
- `PublicDataSyncService.syncFromApi(MANUAL)` 호출, 결과를 응답으로 반환

## 데이터 모델 — location_sync_log (신규, V8 마이그레이션)

```sql
CREATE TABLE location_sync_log (
  idx BIGINT PRIMARY KEY AUTO_INCREMENT,
  started_at DATETIME NOT NULL,
  finished_at DATETIME,
  status VARCHAR(20),        -- SUCCESS / FAILED / PARTIAL
  total_fetched INT,
  inserted INT,
  updated INT,
  skipped INT,
  failed INT,
  error_message TEXT,
  trigger_type VARCHAR(20)   -- SCHEDULED / MANUAL
);
```

기존 엔티티(`LocationService`)와 스키마 변경 없음 — 새 테이블 추가만 필요.

## 에러 처리 & 복원력

- data.go.kr API 호출 타임아웃(10초) + 실패 시 최대 2회 재시도
- 페이지 단위 실패는 격리 — 한 페이지 실패해도 나머지는 계속 처리, 최종 상태는 `PARTIAL`
- 개별 행 파싱 실패는 기존 CSV 로직과 동일하게 해당 행만 skip
- 서비스키 미설정/만료 등 인증 실패는 즉시 `FAILED`로 기록하고 재시도 없이 종료

## 테스트 계획

- `PublicDataSyncService` 단위 테스트: 신규 삽입 / 변경감지 후 갱신 / 무변경 스킵 / 외부 API 실패(전체) / 일부 페이지만 실패(PARTIAL) — 5개 시나리오
- `PublicDataApiClient`: 페이징 처리, 타임아웃/재시도 동작 테스트 (WireMock 등으로 목 서버 구성)
- 기존 CSV 업로드 경로(`importFromCsv`) 회귀 테스트는 그대로 유지 — 이번 변경으로 깨지지 않아야 함

## 범위 밖 (Out of Scope)

- 다른 공공데이터셋 추가 연동
- 실시간/준실시간 동기화 (배치만 다룸)
- 프론트엔드 UI 변경 (관리자 페이지에 "동기화 이력 보기" 화면 추가 여부는 이번 스펙 밖 — 필요 시 별도 스펙)
