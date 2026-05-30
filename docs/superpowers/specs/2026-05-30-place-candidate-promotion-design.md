# Place Candidate Promotion System — Design Spec

**Date**: 2026-05-30
**Status**: Confirmed (Sections 1–4)
**Scope**: 1단계 MVP

---

## 배경 및 문제

pet-data-api가 수집한 장소 후보(블로그·Naver Local 기반)가 품질 검증 없이 바로
`locationservice`에 적재되면서 `식사`, `기념일`, `프렌치` 같은 일반어가 장소로 등록되는 문제 발생.

공공데이터(`locationservice`, dataSource=PUBLIC)는 31개 필드가 채워진 신뢰 높은 데이터이나,
신규 수집 데이터는 name·address·좌표 정도만 존재해 품질 격차가 큼.

---

## 핵심 방향

> 장소 자동 등록 시스템이 아니라 **장소 후보 검증/승격 시스템**

- 수집 결과 → `place_candidates` (미확정 후보)
- 자동 점수화 → 확실한 것만 `places` 승격
- 애매한 것은 Admin 검수
- 탈락 후보는 보관 (삭제 안 함)

---

## 불변 규칙 (4개)

1. `place_candidates`는 서비스 API 직접 노출 금지
2. `locationservice`는 read-only — **신규 장소 후보 적재 경로에서 write 금지**
3. `places`가 서비스의 canonical 장소 테이블
4. **자동 승격(PENDING)과 서비스 노출(ACTIVE)은 다른 개념** — AUTO_APPROVED도 status=PENDING으로 생성, ACTIVE는 관리자 별도 전환

> ⚠️ locationservice write 차단 범위: 신규 후보 적재 경로에만 적용.
> 레거시 공공데이터 동기화 배치가 존재하면 별도 허용/분리 필요.

---

## 섹션 1: 데이터 흐름

```text
[pet-data-api / 공공데이터 배치]
         │
         ▼
  place_candidates             ← 모든 신규 수집은 여기로만
         │
         ▼
  [자동 판정 엔진 — 4-gate]
         │
   ┌─────┼──────┐
AUTO  NEEDS  REJECTED
APPR  REVIEW  (보관, 노출 없음)
OVED    │
  │  [Admin 검수 UI — API first]
  └──── ▼
  places (status=PENDING)      ← 승격됐지만 아직 미노출
         │
         ▼  (관리자 ACTIVE 전환)
  places (status=ACTIVE)       ← 서비스 API 노출 대상
  + place_facts
```

---

## 섹션 2: 스키마

### places

```sql
CREATE TABLE places (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                        VARCHAR(150) NOT NULL,
    address                     VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    category                    VARCHAR(100),
    -- MVP에서 category = category3(소분류)에 해당.
    -- category1/category2 계층 이전은 2단계 마이그레이션에서 결정.
    status                      ENUM('PENDING','ACTIVE','INACTIVE') DEFAULT 'PENDING',
    primary_source              VARCHAR(50),
    confidence                  FLOAT,
    legacy_locationservice_id   BIGINT NULL,

    activated_by                VARCHAR(100) NULL,
    activated_at                DATETIME NULL,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_places_status_confidence (status, confidence DESC),
    INDEX idx_places_legacy_ls_id      (legacy_locationservice_id)
);
-- 상세 출처 근거는 place_facts에서 source별로 관리
```

### place_candidates

```sql
CREATE TABLE place_candidates (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_name                    VARCHAR(255),
    raw_address                 VARCHAR(255),
    lat                         DOUBLE,
    lng                         DOUBLE,
    collected_from              VARCHAR(100),
    evidence_text               TEXT,
    confidence_score            FLOAT,
    decision_status             ENUM(
                                    'PENDING',
                                    'AUTO_APPROVED',
                                    'ADMIN_APPROVED',
                                    'NEEDS_REVIEW',
                                    'REJECTED'
                                ) DEFAULT 'PENDING',
    decision_reason             TEXT,
    score_breakdown             JSON,

    matched_place_id            BIGINT NULL,
    matched_locationservice_id  BIGINT NULL,
    rejection_reason            VARCHAR(255) NULL,
    collected_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by                 VARCHAR(100) NULL,
    reviewed_at                 DATETIME NULL,

    INDEX idx_candidates_status_score (decision_status, confidence_score DESC),
    INDEX idx_candidates_dedup        (raw_name(100), raw_address(100)),
    INDEX idx_candidates_collected    (collected_at),

    -- FK: 운영 중 부모 삭제 방지. matched_* 는 nullable이라 SET NULL 허용.
    CONSTRAINT fk_candidates_place
        FOREIGN KEY (matched_place_id)
        REFERENCES places (id) ON DELETE SET NULL,
    CONSTRAINT fk_candidates_locationservice
        FOREIGN KEY (matched_locationservice_id)
        REFERENCES locationservice (idx) ON DELETE SET NULL
);
```

`score_breakdown` 예시:

```json
{
  "name_quality": 0.1,
  "road_address": 0.2,
  "coord_exists": 0.1,
  "name_in_address": 0.2,
  "alt_business_detected": -0.3,
  "public_medium_match": 0.3,
  "duplicate_boost": 0.1,
  "risk_flag": true,
  "total": 0.7,
  "gate": "GATE4_THRESHOLD",
  "decision": "NEEDS_REVIEW"
}
```

### place_facts

```sql
CREATE TABLE place_facts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    place_id        BIGINT NOT NULL,
    fact_type       VARCHAR(100),
    value_text      TEXT,
    value_json      JSON NULL,
    source          VARCHAR(100),
    confidence      FLOAT,
    observed_at     DATE,

    INDEX idx_facts_place_type (place_id, fact_type),

    CONSTRAINT fk_facts_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE
);
-- 1단계: 테이블 생성만. 자동 수집 제외.
-- 공공데이터 strong match 시 선택적 적재 여부는 2단계에서 결정.
```

`value_json` 예시 (OPERATING_HOURS):

```json
{"days": ["MON","TUE","WED","THU","FRI"], "open": "11:00", "close": "22:00"}
```

---

## 섹션 3: 자동 판정 로직 (4-gate)

판정은 규칙 우선. `confidence_score`는 **Gate 4 threshold decision과 NEEDS_REVIEW 큐 정렬** 두 가지 목적으로 사용한다.

### Gate 1 — Hard Reject + Risk Flag

```text
[Hard blacklist] → 즉시 REJECTED (주소/좌표 무관, 이후 gate 없음)
  식사, 기념일, 맛집, 주말, 데이트, 공원, 산책

[패턴 reject] → 즉시 REJECTED
  - raw_name 2글자 이하
  - 문장형 (조사·어미로 끝남)
  - 형용사·동사 단독 구성
  - 특수문자만
  - 주소 AND 좌표 모두 없음

[Soft blacklist] → risk_flag = true 세팅 후 Gate 2 계속 진행
  프렌치, 벚꽃, 감성, 라운지, 살롱
  ※ terminal decision 아님. strong match(Gate 2)가 있으면 통과 가능.
     strong match 실패 시 risk_flag → Gate 3/4에서 NEEDS_REVIEW로 처리.
```

### Gate 2 — Strong Match → AUTO_APPROVED, score=0.9

`risk_flag`가 있어도 통과 가능.

```text
[경로 A] 공공데이터 strong match:
  - 이름 유사도 ≥ 0.85
  - AND (주소 정규화 일치 OR 좌표 150m 이내)

[경로 B] 자체 신뢰 조건 전부 충족:
  - 이름 품질 양호 (블랙리스트 미해당 + 4글자 이상 고유명사형)
  - AND 도로명주소 존재
  - AND (중복 수집 ≥ 3회  OR  서로 다른 source에서 2회 이상)
  - AND 좌표 존재
```

> "서로 다른 source"란 collected_from이 다른 두 개의 candidate 레코드를 의미.
> 같은 오탐이 동일 경로에서 중복 유입되는 케이스를 방지.

**자동 판정 엔진 side effect (Gate 2 통과 시):**

```text
candidate.decision_status = AUTO_APPROVED
candidate.confidence_score = 0.9
candidate.decision_reason  = "strong_match:public_data" | "strong_match:self_trust"
candidate.score_breakdown  = { gate: "GATE2_STRONG_MATCH", ... }
candidate.matched_place_id = 생성된 places.id

places.status     = PENDING
places.confidence = 0.9
places.primary_source = candidate.collected_from
```

### Gate 3 — Scoring

| 신호 | 조건 | 점수 |
| --- | --- | --- |
| 이름 품질 | 4글자+ 고유명사형 | +0.1 |
| 주소 존재 | 도로명주소 있음 | +0.2 |
| 좌표 존재 | lat/lng 있음 | +0.1 |
| 주소 내 raw_name 포함 | address 안에 raw_name 포함 | +0.2 |
| 주소 말미 별도 상호 감지 | 상세주소에서 다른 상호 후보 추출됨 | −0.3 |
| 공공데이터 medium match | 유사도 0.6~0.85 OR 좌표 150~500m | +0.3 |
| 중복 수집 | log(count) × 0.1, max 0.2 | +0.0~0.2 |

### Gate 4 — Threshold Decision

**AUTO_APPROVED** (아래 조건 전부 충족):

```text
score ≥ 0.6
AND name_quality > 0          (이름 품질 점수 양수)
AND alt_business_detected = false  (주소 말미 별도 상호 없음)
AND risk_flag = false          (soft blacklist 미해당)
AND (public_medium_match가 있으면 이름 유사도 0.6 이상도 충족)
→ places 생성 (status=PENDING)
```

조건 중 하나라도 미충족이면:

```text
score ≥ 0.3  → NEEDS_REVIEW
score < 0.3  → REJECTED
```

**자동 판정 엔진 side effect (Gate 4 AUTO_APPROVED 시):**

```text
candidate.decision_status = AUTO_APPROVED
candidate.confidence_score = score
candidate.decision_reason  = "threshold_passed"
candidate.score_breakdown  = { gate: "GATE4_THRESHOLD", ... }
candidate.matched_place_id = 생성된 places.id

places.status     = PENDING
places.confidence = score
places.primary_source = candidate.collected_from
```

---

## 섹션 4: Admin API

모든 엔드포인트: **ADMIN 권한 필수 + audit 컬럼 기록**

### 상태 전이 guard

```text
approve (idempotent 우선):
  1. matched_place_id != null → 기존 place 반환 (상태 무관, 중복 생성 방지)
  2. status not in (PENDING, NEEDS_REVIEW) → 409 Conflict
  3. 정상 승인 처리

reject:
  허용 상태: PENDING, NEEDS_REVIEW
  → 그 외: 409 Conflict

activate:
  허용 상태: places.status = PENDING
  → ACTIVE, INACTIVE: 409 Conflict
```

### ① NEEDS_REVIEW 큐 조회

```text
GET /admin/place-candidates?status=NEEDS_REVIEW&sort=confidence_score,desc

Response:
{
  "id": 1234,
  "raw_name": "23플래터",
  "raw_address": "서울특별시 마포구 잔다리로3안길 36 1층 23플래터",
  "lat": 37.5504806,
  "lng": 126.9193937,
  "confidence_score": 0.52,
  "score_breakdown": { ... },
  "decision_reason": "주소 말미에 별도 상호 후보 감지",
  "matched_locationservice": { "id": 100, "name": "...", "address": "..." } | null,
  "evidence_text": "...",
  "collected_from": "PET_DATA_API",
  "collected_at": "2026-05-29T..."
}
```

### ② 후보 승인 (idempotent)

```text
POST /admin/place-candidates/{id}/approve
허용 상태: PENDING, NEEDS_REVIEW (그 외 409)

Body:
{
  "override_name":     "...",
  "override_address":  "...",
  "override_category": "...",
  "override_lat":      37.55,
  "override_lng":      126.92
}

Logic:
  if candidate.matched_place_id != null:
    return existing places record  // 중복 생성 방지
  else:
    create places (status=PENDING, primary_source=candidate.collected_from)
    candidate.decision_status = ADMIN_APPROVED
    candidate.matched_place_id = new places.id
    candidate.reviewed_by = currentAdmin
    candidate.reviewed_at = now()
```

### ③ 후보 탈락

```text
POST /admin/place-candidates/{id}/reject
허용 상태: PENDING, NEEDS_REVIEW (그 외 409)

Body: { "rejection_reason": "일반어로 판단" }

→ candidate.decision_status = REJECTED
→ candidate.reviewed_by = currentAdmin
→ candidate.reviewed_at = now()
```

### ④ PENDING places 조회

```text
GET /admin/places?status=PENDING&sort=confidence,desc

Response:
{
  "id": 500,
  "name": "23플래터",
  "address": "서울특별시 마포구 잔다리로3안길 36",
  "lat": 37.5504806,
  "lng": 126.9193937,
  "category": "식당",
  "confidence": 0.9,
  "primary_source": "PET_DATA_API",
  "legacy_locationservice_id": null,
  "created_at": "2026-05-30T..."
}
```

### ⑤ PENDING → ACTIVE 전환

```text
POST /admin/places/{id}/activate
허용 상태: PENDING (그 외 409)

→ places.status = ACTIVE
→ places.activated_by = currentAdmin
→ places.activated_at = now()
```

---

## MVP 구현 범위 (1단계)

### 포함

- `place_candidates`, `places`, `place_facts` 테이블 생성 (DDL + index + FK)
- 판정 엔진 4-gate (`@Scheduled` 또는 Spring Batch Job)
- Admin API 5개 엔드포인트 (상태 전이 guard 포함)
- pet-data-api export → `place_candidates` 적재 (기존 적재 경로 변경)
- `locationservice` 신규 장소 후보 적재 경로 write 차단 (service/repository 레벨)

### 제외 (2단계 이후)

- Google Places / LLM 보강
- `locationservice` → `places` 마이그레이션
- `place_facts` 자동 수집 (공공데이터 strong match 연동 포함)
- ACTIVE 자동 전환 정책
- 프론트엔드 Admin UI

---

## 단계별 마이그레이션 계획

| 단계 | 내용 |
| --- | --- |
| 1단계 | locationservice 유지. places/place_candidates 추가. 신규 수집은 candidates만. |
| 2단계 | locationservice 기존 레코드 → places 점진 이전. `primary_source=PUBLIC_DATA`, `legacy_locationservice_id` 연결. |
| 3단계 | 서비스 API 조회 기준을 places로 이동. place_facts로 공공데이터 상세 필드 분리. |
| 4단계 | locationservice read-only/deprecated. 신규 write 전면 금지. |
| 5단계 | 충분한 검증 후 locationservice archive 또는 제거. |
