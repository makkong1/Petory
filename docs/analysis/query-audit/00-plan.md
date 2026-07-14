---
date: 2026-07-14
domains: [board, care, chat, location, meetup, notification, payment, report, user, file, statistics]
type: audit-plan
problem: full-query-audit
status: planned
metric: "감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 27개 / 스케줄러 9개 / nativeQuery 20곳. 현재 확인된 것은 API 1개(GET /api/boards)뿐"
related: [docs/analysis/query-plan-monitoring-design.md, docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md]
---

# 전체 쿼리 감사 — 계획 및 방법론

> **프로젝트 전체 쿼리를 실제 API 호출로 측정하고 도메인별로 문서화한다.**
> 현재까지 확인된 것은 `GET /api/boards` **단 하나**다. 나머지는 전부 미확인이다.

---

## 0. 왜 이 문서가 필요한가 — 어제의 실패

2026-07-13 에 게시글 목록 쿼리를 튜닝하면서 **SQL 을 손으로 직접 던져 측정**했다.
그 결과 `0.17s → 0.00s` 로 고쳤다고 판단했다.

**다음 날 실제 API 를 호출해보니 절반만 맞았다.**

```
SELECT COUNT(...)  검사 180,003행  141ms   ← 손도 안 댐 (더 비쌈)
SELECT b1_0.idx... 검사     120행    4ms   ← 내가 고친 것
```

`Page<>` 는 목록 SELECT 와 **COUNT 두 개**를 날린다. 나는 하나만 보고 있었다.
**API 를 한 번도 호출하지 않았기 때문에 몰랐다.**

> **이 감사의 존재 이유: "내가 짠 SQL"이 아니라 "앱이 실제로 날리는 쿼리"를 봐야 한다.**

---

## 1. 🔴 절대 어기면 안 되는 원칙

| # | 원칙 | 이유 |
|---|---|---|
| **1** | **반드시 실제 API 를 호출한다 (`curl`)** | 손으로 쓴 SQL 은 앱이 하는 일이 아니다. 어제 실패의 직접 원인 |
| **2** | **측정 전 digest 를 비운다** | `TRUNCATE performance_schema.events_statements_summary_by_digest` — 안 그러면 이전 노이즈와 섞인다 |
| **3** | **stderr 를 지우지 않는다** | `2>/dev/null` 로 에러를 숨겼다가 빈 테이블을 측정하고 "재현 안 됨" 결론을 낼 뻔했다 |
| **4** | **`EXPLAIN` 과 `EXPLAIN ANALYZE` 를 둘 다 본다** | 전자는 *예상*, 후자는 *실제*. **둘의 차이가 곧 버그의 정체**인 경우가 있다 (선택도 오판) |
| **5** | **인과는 A/B/A 로 증명한다** | "고쳤더니 빨라졌다"는 증거가 아니다. 적용 → 제거 → 재적용으로 재현해야 한다 |
| **6** | **고치면 CI 회귀 테스트를 붙인다 (2단계)** | ① 수정 전 상태에서 문제 신호가 **실제로 나타나는지 먼저 확인** ② 수정 후 사라지는지 확인. ①이 없으면 테스트가 헛돌고 초록불만 켜진다 |
| **7** | **`SQL_NO_CACHE` 를 붙이고 3회 이상 측정해 최소값을 쓴다** | 캐시 효과 제거 |

---

## 2. 도메인 1개당 절차

```
[1] 준비
    - 앱 기동 (포트 8081 — 도커 app 이 8080 점유)
    - 시드 계정으로 로그인해 토큰 확보 (권한별로)
    - TRUNCATE performance_schema.events_statements_summary_by_digest

[2] 실행
    - 해당 도메인의 엔드포인트를 curl 로 한 바퀴 호출
    - 목록/상세/검색/필터/페이징(1페이지 + 깊은 페이지)을 모두 태운다
    - 응답 HTTP 코드와 소요 시간 기록

[3] 스캔
    - digest 조회 → 검사행 / 검사·반환 배율 / 디스크임시 / 정렬스캔 / 인덱스미사용 / 총ms
    - 상위 쿼리를 뽑는다

[4] 진단
    - 의심 쿼리마다 EXPLAIN + EXPLAIN ANALYZE
    - estimated rows vs actual rows 대조 (선택도 오판 탐지)
    - Extra 의 Using filesort / Using temporary 확인

[5] 기록
    - docs/analysis/query-audit/<domain>-YYYY-MM-DD.md 로 evidence 문서 작성
    - frontmatter 필수 (docs/INDEX.md 자동 등록)

[6] 수정 (문제가 있으면)
    - 원인별 처방: 히스토그램 / 인덱스 / 쿼리 재작성 / 페이징 전략 변경
    - A/B/A 인과 증명
    - CI 회귀 테스트 (2단계)
```

---

## 3. 도구 · 계정

### 앱 기동

```bash
./gradlew bootRun --args='--server.port=8081'    # 8080 은 도커 app 이 점유
```

### 로그인 (시드 계정, 비밀번호 전부 `Seed1234!`)

```bash
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"id":"seed_user_1","password":"Seed1234!"}'
```

| 권한 | 계정 수 | 예시 로그인 ID |
|---|---|---|
| `USER` | 9,500 | `seed_user_1` |
| `SERVICE_PROVIDER` | 490 | `seed_user_100` |
| `ADMIN` | 10 | `seed_user_1000` |
| `MASTER` | 1 | `dud123` (**사용자 실계정 — 비밀번호 모름**) |

> ⚠️ **MASTER 전용 엔드포인트**가 있으면 시드 ADMIN 을 임시로 MASTER 로 승격해서 테스트한다
> (로컬 DB 한정, 감사 후 원복). 사용자 실계정 비밀번호를 요구하지 않는다.

### digest 스캔 쿼리

```sql
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;   -- 측정 전

SELECT LEFT(REPLACE(DIGEST_TEXT,'\n',' '), 60)             AS 쿼리,
       COUNT_STAR                                          AS 횟수,
       SUM_ROWS_SENT                                       AS 반환,
       SUM_ROWS_EXAMINED                                   AS 검사,
       ROUND(SUM_ROWS_EXAMINED/GREATEST(SUM_ROWS_SENT,1))  AS 배율,
       SUM_CREATED_TMP_DISK_TABLES                         AS 디스크임시,
       SUM_SORT_SCAN                                       AS 정렬스캔,
       SUM_NO_INDEX_USED                                   AS 인덱스미사용,
       ROUND(SUM_TIMER_WAIT/1000000000)                    AS 총ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='petory' AND DIGEST_TEXT LIKE 'SELECT%'
  AND DIGEST_TEXT NOT LIKE '%INFORMATION_SCHEMA%'
ORDER BY SUM_ROWS_EXAMINED DESC LIMIT 20;
```

### 현재 데이터 규모 (측정이 유의미한 수준)

users 10,000 / pets 12,000 / board 50,000 / comment 150,000 / reaction 175,000 /
view_log 125,000 / meetup 5,000 / carerequest 3,000 / chatmessage 30,600 /
locationservice 22,905 / locationservicereview 20,000

> 재생성: `mysql ... petory < scripts/seed/seed-dev-data.sql`

---

## 4. 도메인별 감사 체크리스트

| # | 도메인 | 컨트롤러 | 엔드포인트 | `Page<>` | 우선순위 | 상태 |
|---|---|---|---|---|---|---|
| 1 | **board** | 2 | 24 | **13** | ★★★ | 🟡 부분 (목록 SELECT 만. **COUNT 미해결**) |
| 2 | **care** | 3 | 16 | 5 | ★★★ | ⬜ 미착수 (주변검색 공간인덱스 없음 — 이미 알려짐) |
| 3 | **meetup** | 1 | 15 | 3 | ★★★ | 🟡 부분 (스케줄러 `findWithoutChatRoom` 문제 발견) |
| 4 | **chat** | 3 | 18 | 2 | ★★ | ⬜ 미착수 |
| 5 | **location** | 3 | 11 | 1 | ★★ | ⬜ 미착수 (공간·풀텍스트는 baseline 에서 정상 확인) |
| 6 | **user** | 2 | 13 | - | ★★ | ⬜ 미착수 |
| 7 | **notification** | 2 | 8 | - | ★ | ⬜ 미착수 |
| 8 | **payment** | 1 | 4 | 1 | ★ | ⬜ 미착수 |
| 9 | **file** | 1 | 2 | 1 | ★ | ⬜ 미착수 |
| 10 | **report** | 1 | 1 | 1 | ★ | ⬜ 미착수 |
| 11 | **admin** (13개 컨트롤러) | 13 | — | 다수 | ★★ | ⬜ 미착수 (트래픽은 적으나 스캔량이 크다) |
| 12 | **statistics** | — | — | — | ★ | ⬜ 미착수 (배치) |

**합계: 컨트롤러 34개 / 엔드포인트 189개**

---

## 5. 도메인을 가로지르는 시스템적 위험 (별도 스윕)

도메인별 감사와 **별개로** 전수 점검해야 하는 것들.

### 5.1 🔴 `Page<>` COUNT 쿼리 — 27개

Spring Data 의 `Page<>` 는 목록 SELECT 와 **별개로 COUNT 쿼리**를 날린다.
board 에서 확인된 것: **180,003행 검사 / 141ms** — 목록 SELECT(4ms)보다 **35배 비싸다.**

| 도메인 | `Page<>` 메서드 수 |
|---|---|
| board | 13 |
| care | 5 |
| meetup | 3 |
| chat | 2 |
| location / payment / report / file | 각 1 |

**27개 중 1개만 확인했다.** 이것이 이 프로젝트에서 **가장 넓게 퍼진 성능 위험**일 가능성이 높다.

처방 후보 (총건수 정확도를 포기해야 한다):
- `Slice` 전환 (COUNT 없음) → 무한스크롤/"더보기" UI 필요
- 총건수 캐싱 (Redis, TTL 수 분)
- `countQuery` 를 단순화 (조인 제거 가능한 경우)

### 5.2 스케줄러 — 9개

사용자가 체감하지 못하므로 **더 오래 숨는다.**

`MeetupChatRoomRecoveryScheduler.findWithoutChatRoom()` 에서
**1만 행 검사 / 0행 반환 / 인덱스 미사용** 발견. 나머지 8개 미확인.

`StatisticsScheduler`, `CareRequestScheduler`, `BoardPopularityScheduler`,
`LocationServiceScoreScheduler`, `UserSanctionScheduler`, `UserDormantScheduler`,
`MeetupScheduler`, `BoardListQueryPlanMaintainer`

> 감사 방법: cron 을 앞당기거나 메서드를 직접 호출한 뒤 digest 를 본다.

### 5.3 네이티브 쿼리 — 20곳

`nativeQuery = true` 는 Hibernate 가 생성하지 않고 **사람이 직접 쓴 SQL** 이므로
옵티마이저 계획을 개별 확인해야 한다.

board, boardReaction, boardViewLog, commentReaction, care, chat, location, meetup, dailyStatistics

(공간 검색처럼 잘 짜인 것도 있다 — baseline §2 에서 `ST_Within` 이 인덱스를 제대로 타는 것 확인)

---

## 6. 산출물

```
docs/analysis/query-audit/
  00-plan.md                     ← 이 문서 (진행 상태 갱신)
  board-2026-07-XX.md
  care-2026-07-XX.md
  meetup-2026-07-XX.md
  ...
  99-summary.md                  ← 전체 결과 종합 (감사 완료 후)
```

각 evidence 문서는 frontmatter 필수 (`docs/INDEX.md` 자동 등록):

```yaml
---
date: YYYY-MM-DD
domains: [<domain>]
type: query-audit-evidence
problem: <발견한 문제 슬러그>
status: verified
metric: "핵심 수치 (전 → 후)"
---
```

---

## 7. 이미 알려진 미해결 항목

| 항목 | 출처 |
|---|---|
| **board `Page<>` COUNT** 180,003행 / 141ms | design 문서 §1 |
| **meetup `findWithoutChatRoom()`** 1만행 검사 / 0행 반환 / 인덱스 미사용 | design 문서 §1 |
| **`carerequest` 주변 검색에 공간 인덱스 없음** (풀스캔) | baseline §4 |
| **`file.idx_file_target`** 미측정 (시드가 `file` 테이블을 만들지 않음) | baseline §5 |
| **`BoardListQueryPlanMaintainer` 의 SQL 이 리포지토리와 갈라질 위험** | design 문서 §3.2 |
