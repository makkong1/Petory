# Pet Data API — 설계 스펙

**작성일:** 2026-04-19  
**프로젝트 유형:** Python 사이드 프로젝트 (GitHub 포트폴리오용)  
**목적:** 공공데이터 기반 반려동물 데이터 수집 & 분석 REST API. Java/Spring(Petory)과 함께 멀티스택 백엔드 포트폴리오 구성.

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| 웹 프레임워크 | FastAPI |
| DB ORM | SQLAlchemy 2.0 (async) |
| DB | PostgreSQL (pg_trgm 확장 사용) |
| 스케줄러 | APScheduler |
| HTTP 클라이언트 | httpx (async) |
| 인증 | API Key (`X-API-Key` 헤더, SHA-256 해싱) |
| 환경변수 | python-dotenv |

---

## 프로젝트 구조

```
pet-data-api/
├── app/
│   ├── api/          # FastAPI 라우터 (엔드포인트)
│   ├── collector/    # 공공데이터 API 수집기
│   ├── scheduler/    # APScheduler 스케줄 설정
│   ├── models/       # SQLAlchemy 모델
│   ├── schemas/      # Pydantic 요청/응답 스키마
│   ├── core/         # 설정, DB 연결, API Key 인증 미들웨어
│   └── main.py       # 앱 진입점
├── .env
├── requirements.txt
└── README.md
```

---

## 데이터 소스

모두 공공데이터포털(data.go.kr) Open API. 합법적 사용, 무료, 키 발급 필요.

| API | 제공기관 | 설명 |
|-----|----------|------|
| 국가동물보호정보시스템 구조동물 조회 | 농림축산검역본부 | 유기동물 공고, 상태, 보호소 정보 (핵심 데이터) |
| 전국동물보호센터 정보 | 공공데이터포털 | 전국 보호소 위치, 운영시간, 좌표 |
| 반려동물 영업장 정보 | 농림축산검역본부 | 동물판매·생산·미용 등 영업장 목록 |
| 동물병원 현황 | 행정안전부 | 전국 동물병원 인허가 정보 |

---

## DB 모델

### `abandoned_animals`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | SERIAL PK | |
| notice_no | VARCHAR UNIQUE | 공고번호 (upsert 기준키) |
| animal_type | VARCHAR | 개 / 고양이 / 기타 |
| breed | VARCHAR | 품종 |
| age | VARCHAR | 나이 |
| gender | VARCHAR | 성별 |
| region | VARCHAR | 지역 |
| shelter_name | VARCHAR | 보호소명 |
| status | VARCHAR | 보호중 / 입양 / 안락사 등 |
| notice_date | DATE | 공고일 |
| collected_at | TIMESTAMP | 수집 시각 |

**인덱스 전략:**

```sql
-- upsert 기준키
CREATE UNIQUE INDEX idx_animals_notice_no ON abandoned_animals(notice_no);

-- 목록 필터 핵심 경로 (region + status + animal_type 동시 필터 커버)
CREATE INDEX idx_animals_region_status_type ON abandoned_animals(region, status, animal_type);

-- 날짜 범위 조회 / 통계 집계
CREATE INDEX idx_animals_notice_date ON abandoned_animals(notice_date);

-- 품종/보호소명 한국어 퍼지 검색 (pg_trgm)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_animals_breed_trgm ON abandoned_animals USING gin(breed gin_trgm_ops);
CREATE INDEX idx_animals_shelter_trgm ON abandoned_animals USING gin(shelter_name gin_trgm_ops);
```

> 중장기: `notice_date` 기준 Range Partition (연도별). 데이터 증가 시 파티션 프루닝으로 Full Scan 방지.

### `collection_logs`

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | SERIAL PK | |
| source | VARCHAR | 수집 대상 API 이름 |
| status | VARCHAR | success / partial / failed |
| total_fetched | INT | 공공API에서 받아온 건수 |
| total_saved | INT | 실제 DB 저장 건수 |
| error_message | TEXT | 실패 시 에러 내용 |
| started_at | TIMESTAMP | |
| finished_at | TIMESTAMP | |

---

## 통계 — Materialized View

`daily_region_stats` 테이블 대신 **PostgreSQL Materialized View** 사용.

```sql
CREATE MATERIALIZED VIEW mv_region_stats AS
SELECT
    region,
    DATE(notice_date)          AS date,
    COUNT(*)                   AS total_count,
    COUNT(*) FILTER (WHERE status = '입양')   AS adopted_count,
    COUNT(*) FILTER (WHERE status = '안락사') AS euthanized_count
FROM abandoned_animals
GROUP BY region, DATE(notice_date);

CREATE UNIQUE INDEX ON mv_region_stats(region, date);
```

**갱신 전략:**
- 수집 완료 후 `REFRESH MATERIALIZED VIEW CONCURRENTLY mv_region_stats` 실행
- `CONCURRENTLY` 옵션: 갱신 중에도 기존 뷰 읽기 가능 (락 없음)
- Full Scan은 REFRESH 시점 1회만 발생. 조회 쿼리는 항상 인덱스 탐색.
- 수집 실패 시 REFRESH 건너뜀 (기존 뷰 보존)

---

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/animals` | 유기동물 목록 (필터, 검색, 커서 페이지네이션) |
| GET | `/animals/{id}` | 유기동물 상세 |
| GET | `/stats/region` | 지역별 현황 통계 |
| GET | `/stats/trend` | 월별 추이 (year, month 파라미터) |
| POST | `/collect/trigger` | 수동 수집 트리거 (관리자용) |

모든 엔드포인트에 `X-API-Key` 헤더 필수.

### 페이지네이션 — Keyset (Cursor)

`GET /animals` offset 기반 **금지**. `id` 기준 커서 페이지네이션 사용.

```
# 첫 페이지
GET /animals?limit=20

# 다음 페이지 (이전 응답의 next_cursor 사용)
GET /animals?limit=20&cursor=12345
```

```sql
-- 실행 쿼리
SELECT * FROM abandoned_animals
WHERE id > :cursor
  AND region = :region          -- 선택적 필터
  AND status = :status
  AND animal_type = :animal_type
ORDER BY id ASC
LIMIT :limit;
```

- offset 기반은 OFFSET N 증가 시 O(n) Full Scan 발생 → 사용 금지
- `id` 오름차순 정렬 + `WHERE id > cursor` → 항상 인덱스 탐색 O(log n)
- 응답: `{ items, next_cursor, has_next }`

### 검색

`GET /animals?search=골든리트리버` — 품종(breed) 대상 trigram 유사도 검색:

```sql
SELECT * FROM abandoned_animals
WHERE breed % :query          -- pg_trgm 유사도 연산자
ORDER BY similarity(breed, :query) DESC, id ASC
LIMIT :limit;
```

- `%` 연산자: GIN trigram 인덱스 탐색 (LIKE '%...%' Full Scan 금지)
- search 파라미터와 필터 파라미터 동시 사용 가능

---

## 수집 스케줄

- **실행 시각:** 매일 오후 7시
- **중복 실행 방지:** APScheduler `max_instances=1`
- **실행 순서:** 공공API 수집 → DB upsert → `REFRESH MATERIALIZED VIEW`

### 실패 처리

1. httpx 재시도 3회 (지수 백오프: 1s → 2s → 4s)
2. 3회 실패 → `collection_logs` `status=failed`, DB 기존 데이터 보존
3. 부분 성공 허용: 일부 저장 성공 시 `status=partial` 기록
4. upsert는 배치 단위 트랜잭션 (실패 시 전체 롤백, 부분 저장 방지)
5. 통계 REFRESH는 수집 성공/partial일 때만 실행

---

## 인증

### API Key 구조

두 종류:
- **일반 키** — 조회/통계 엔드포인트 접근
- **관리자 키** — 전체 접근 (`/collect/trigger` 포함)

### 보안 처리

```
키 생성: secrets.token_hex(32)  →  64자 랜덤 hex 문자열
저장:    hashlib.sha256(key).hexdigest()  →  DB에 해시값만 저장
검증:    요청 키를 SHA-256 해싱 후 DB 해시와 비교
```

**bcrypt 대신 SHA-256을 쓰는 이유:**
- bcrypt는 패스워드용 — 의도적으로 느림 (요청마다 수십ms 소요)
- API Key는 `secrets.token_hex`로 생성한 고엔트로피 랜덤값 → 브루트포스 불가
- SHA-256은 빠르고 충분히 안전 (API Key 검증 표준 방식)

FastAPI `Depends`로 엔드포인트별 권한 분리.

---

## 에러 처리

| 코드 | 상황 |
|------|------|
| 400 | 잘못된 파라미터 (limit 범위 초과 등) |
| 401 | API Key 없거나 틀림 |
| 403 | 일반 키로 관리자 전용 엔드포인트 접근 |
| 404 | 해당 ID 없음 |
| 500 | 공공API 호출 실패 또는 DB 오류 |
