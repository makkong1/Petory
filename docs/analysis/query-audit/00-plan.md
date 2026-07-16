---
date: 2026-07-14
domains: [board, care, chat, location, meetup, notification, payment, report, user, file, statistics]
type: audit-plan
problem: full-query-audit
status: verified
metric: "감사 대상 — 컨트롤러 34개 / 엔드포인트 189개 / Page<> COUNT 30개(그중 16개는 COUNT 자동생성) / 스케줄러 9개 / nativeQuery 21곳. 2026-07-14 전 도메인 감사 완료(엔드포인트 62개 실호출). 유일 잔여: 스케줄러 8개(cron)"
related: [docs/analysis/query-plan-monitoring-design.md, docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md, docs/analysis/query-audit/99-summary.md]
---

# 전체 쿼리 감사 — 계획 및 방법론

> **프로젝트 전체 쿼리를 실제 API 호출로 측정하고 도메인별로 문서화한다.**
>
> **📊 결과는 [99-summary.md](99-summary.md) 를 볼 것.** 이 문서는 *방법론*이다.
>
> **2026-07-14 진행 상황:** **11개 도메인 완료 (admin 포함, 엔드포인트 58개 실호출).**
> 치명 1건(care 검색 **HTTP 500** — 공개+admin 양쪽) · **진짜 N+1 1건**(admin care, 20건→60쿼리) · 무제한 반환 3건.
> **모든 도메인 감사 완료.** 잔여: 스케줄러 8개(cron 미발화), notification·file(시드 데이터 없음).

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

> **이 감사의 존재 이유: 요청 하나에 앱이 실행하는 쿼리를 "전부" 봐야 한다.**
>
> ⚠️ 이걸 "내가 짠 SQL이 틀렸다"로 오해하지 말 것. 내가 손으로 실행한 목록 SELECT 는
> 앱이 실행하는 것과 **같았고**, 그걸 튜닝한 것도 실제로 효과가 있었다(120행 / 4ms).
> 문제는 **범위**였다. SQL 을 손으로 실행하면 **내가 떠올린 쿼리만** 측정하게 되고,
> `Page<>` 가 자동으로 붙이는 COUNT 나 지연로딩이 추가하는 쿼리는 **애초에 시야에 들어오지 않는다.**

### 0.1 그리고 이 계획서 초안도 같은 실수를 했다 (2026-07-14 검증)

초안을 코드와 대조해보니 **똑같은 유형의 실수가 두 번 더** 있었다.

| 초안 | 실제 | 왜 틀렸나 |
|---|---|---|
| `Page<>` **27개** | **30개** | 리포지토리에 **선언된** 메서드만 셌다. `JpaRepository` 를 상속하면 `findAll(Pageable)` 은 **선언 없이도 존재하고 COUNT 를 날린다** (`UsersService:64`, users 1만 행) |
| user 도메인 `Page<>` **없음(`-`)** | **3개** | 위와 같은 이유 + QueryDSL 로 `Page` 를 조립하는 `JpaUsersAdapter` 를 못 봤다 |
| nativeQuery **20곳** | **21곳** | 단순 오집계 |

**교훈은 어제와 동일하다.** 어제는 "코드에 적힌 SELECT"만 보고 COUNT 를 놓쳤고,
오늘은 "코드에 적힌 `Page<>` 선언"만 보고 상속·QueryDSL 경로를 놓쳤다.
**세는 단위는 "선언"이 아니라 "호출 지점"이어야 한다.**

---

## 1. 🔴 절대 어기면 안 되는 원칙

| # | 원칙 | 이유 |
|---|---|---|
| **1** | **반드시 실제 API 를 호출한다 (`curl`)** | 손으로 SQL 을 실행하면 **내가 떠올린 쿼리만** 측정된다. 앱이 옆에 붙여 보내는 COUNT·지연로딩은 시야 밖이다. 어제 실패의 직접 원인 |
| **2** | **측정 전 digest 를 비운다** | `TRUNCATE performance_schema.events_statements_summary_by_digest` — 안 그러면 이전 노이즈와 섞인다 |
| **3** | **stderr 를 지우지 않는다** | `2>/dev/null` 로 에러를 숨겼다가 빈 테이블을 측정하고 "재현 안 됨" 결론을 낼 뻔했다 |
| **4** | **`EXPLAIN` 과 `EXPLAIN ANALYZE` 를 둘 다 본다** | 전자는 *예상*, 후자는 *실제*. **둘의 차이가 곧 버그의 정체**인 경우가 있다 (선택도 오판) |
| **5** | **인과는 A/B/A 로 증명한다** | "고쳤더니 빨라졌다"는 증거가 아니다. 적용 → 제거 → 재적용으로 재현해야 한다 |
| **6** | **고치면 CI 회귀 테스트를 붙인다 (2단계)** | ① 수정 전 상태에서 문제 신호가 **실제로 나타나는지 먼저 확인** ② 수정 후 사라지는지 확인. ①이 없으면 테스트가 헛돌고 초록불만 켜진다 |
| **7** | **API 측정에서는 `SQL_NO_CACHE` 를 쓸 수 없다 — 대신 앱 캐시를 비운다** | `SQL_NO_CACHE` 는 손으로 SQL 을 던질 때만 붙일 수 있다. curl 로는 붙일 방법이 없다. 대신 **`@Cacheable` 이 2회차 호출부터 쿼리를 0건으로 만든다** (아래 §1-1) |
| **8** | **SELECT 만 보지 않는다** | 스캔 필터를 `LIKE 'SELECT%'` 로 걸면 **INSERT/UPDATE/DELETE 가 통째로 빠진다.** 이 프로젝트의 핵심 동시성 패턴(펫코인·에스크로 비관적 락, 경고횟수·모임인원 원자적 증가 UPDATE)이 전부 쓰기 경로다 |
| **9** | **N+1 은 "검사행"이 아니라 "호출횟수"로 잡는다** | N+1 은 개별 쿼리가 **값싸고 대신 수백 번 반복**된다. 검사행순 정렬로는 상위권에 아예 안 뜬다 (§3 의 3-패스 스캔) |

### 1-1. 원칙 7 부연 — `@Cacheable` 이 걸린 엔드포인트는 2회차부터 쿼리가 0건이 된다

| 캐시 | 위치 | 측정 전 조치 |
|---|---|---|
| `todayStats` | `StatisticsService:71` | Redis `FLUSHDB` (로컬 한정) 후 **1회차 호출만** 측정 |
| `popularLocationServices` | `LocationServiceService:133` | 위와 동일 |

> 게시글 상세 캐시는 **현재 비활성**이다 (`BoardService:156` 에 `@Cacheable` 주석 처리 — 조회수 실시간 반영 때문).
> `CLAUDE.md` 의 "Redis 용도 3가지 — 게시글 상세 캐시" 설명은 코드보다 뒤처져 있다. **감사 후 문서 정정 대상.**

---

## 2. 도메인 1개당 절차

```
[1] 준비
    - 🔴 스케줄러를 끄고 기동한다 (--petory.scheduling.enabled=false) ← §2-1 필수
    - 앱 기동 (포트 8081 — 도커 app 이 8080 점유)
    - 시드 계정으로 로그인해 토큰 확보 (권한별로)
    - TRUNCATE performance_schema.events_statements_summary_by_digest
      ↑ 로그인/기동 쿼리까지 지우려면 "토큰 확보 후"에 TRUNCATE 한다

[2] 실행
    - 해당 도메인의 엔드포인트를 curl 로 한 바퀴 호출
    - 목록/상세/검색/필터/페이징(1페이지 + 깊은 페이지)을 모두 태운다
    - 쓰기 엔드포인트(POST/PUT/DELETE)도 반드시 태운다 (원칙 8)
    - @Cacheable 엔드포인트는 Redis 비우고 1회차만 (원칙 §1-1)
    - 응답 HTTP 코드와 소요 시간 기록
    - ⚠️ N+1 판별을 위해 "curl 몇 번 호출했는지"를 적어둔다 — 패스 B 의 분모다

[3] 스캔 — 3-패스 (§3)
    - 패스 A 검사행순  → 풀스캔 / 비싼 COUNT
    - 패스 B 호출횟수순 → N+1
    - 패스 C 락시간순  → 락 경합 / 과잉 락 UPDATE
    - 세 패스 각각 상위 쿼리를 뽑는다

[4] 진단
    - 의심 쿼리마다 EXPLAIN + EXPLAIN ANALYZE
    - estimated rows vs actual rows 대조 (선택도 오판 탐지)
    - Extra 의 Using filesort / Using temporary 확인
    - Page<> 라면 목록 SELECT 와 COUNT 를 "둘 다" 진단한다 (§0 의 실패 지점)

[5] 기록
    - docs/analysis/query-audit/<domain>-YYYY-MM-DD.md 로 evidence 문서 작성
    - frontmatter 필수 (docs/INDEX.md 자동 등록)

[6] 수정 (문제가 있으면)
    - 원인별 처방: 히스토그램 / 인덱스 / 쿼리 재작성 / 페이징 전략 변경
    - A/B/A 인과 증명
    - CI 회귀 테스트 (2단계)
```

### 2-1. 🔴 스케줄러를 끄지 않으면 측정이 오염된다

`MeetupChatRoomRecoveryScheduler` 는 **`@Scheduled(fixedDelay = 300_000)` — 5분마다** 돈다.
도메인 하나 감사에 5분 넘게 걸리므로 **반드시 측정 중에 끼어든다.**

하필 이게 **1만 행 검사 / 0행 반환 / 인덱스 미사용**인 그놈이다 (§5.2).
끄지 않으면 **패스 A 상위권에 뜨고, 감사 중인 도메인이 범인인 것처럼 보인다.**

| 스케줄러 | 주기 | 감사 중 발화? |
|---|---|---|
| `MeetupChatRoomRecoveryScheduler` | **5분마다** (+기동 직후 1회) | 🔴 **거의 항상** |
| `MeetupScheduler` | 매시 정각 | 🟡 가능 |
| `StatisticsScheduler` · `LocationServiceScoreScheduler` | 매일 자정 | 🟢 낮음 |
| `BoardPopularityScheduler` | 매일 18:30 | 🟢 낮음 |
| `BoardListQueryPlanMaintainer` | 매일 03:10 (`ANALYZE TABLE`) | 🟢 낮음 |

**끄는 방법 — 선행 코드 변경 1건 (§8)**

`@EnableScheduling` 이 `PetoryApplication:23` 에 직접 붙어 있어 **프로퍼티로 끌 수 없다.**
별도 `SchedulingConfig` 로 빼내고 스위치를 단다:

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "petory.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig { }
```

```bash
./gradlew bootRun --args='--server.port=8081 --petory.scheduling.enabled=false'
```

> 기본값이 `true` 이므로 **운영 동작은 그대로다.** 감사할 때만 끈다.
> 스케줄러 자체의 쿼리는 §5.2 에서 **따로, 단독으로** 측정한다 — 그래야 어느 도메인 탓인지 헷갈리지 않는다.

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

### digest 스캔 쿼리 — 반드시 3-패스로 본다

**정렬 기준 하나로는 문제의 3분의 1만 보인다.** 같은 digest 테이블을 **세 번**, 서로 다른 기준으로 정렬해서 본다.

| 패스 | 정렬 / 필터 | 잡히는 문제 |
|---|---|---|
| **A. 스캔량** | `SUM_ROWS_EXAMINED DESC` | 풀스캔, 인덱스 미사용, 비싼 COUNT |
| **B. 호출횟수** | `COUNT_STAR DESC` | **N+1** — 값싼 쿼리가 수백 번 반복되므로 패스 A 에는 안 뜬다 |
| **C. 쓰기 경로** | 쓰기 digest 만 필터 + `SUM_ROWS_EXAMINED DESC` | **과잉 락** — 인덱스 못 타는 UPDATE 는 잠글 필요 없는 행까지 잠근다 |

> 🔴 **`SUM_LOCK_TIME` 으로 락을 재려 하지 마라 (실측으로 반증됨, 2026-07-14).**
> MySQL 의 `LOCK_TIME` 은 **테이블 락 전용**이라 InnoDB **행 락 대기가 안 들어온다.**
> 이 DB 는 `Innodb_row_lock_waits = 377` / `Innodb_row_lock_time = 152,502ms` 로 **행 락 대기가 실제로 있었는데,
> digest 의 `SUM_LOCK_TIME` 은 전부 0 이었다.** 락 경합 관측 방법은 §5.4 를 볼 것.

```sql
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;   -- 측정 전 (원칙 2)

-- 패스 A · B 공통. ORDER BY 만 바꾼다.
-- ⚠️ DIGEST_TEXT LIKE 'SELECT%' 필터를 걸지 않는다 (원칙 8 — 쓰기 경로도 봐야 한다)
SELECT LEFT(REPLACE(DIGEST_TEXT,'\n',' '), 60)             AS 쿼리,
       COUNT_STAR                                          AS 횟수,
       SUM_ROWS_SENT                                       AS 반환,
       SUM_ROWS_EXAMINED                                   AS 검사,
       ROUND(SUM_ROWS_EXAMINED/GREATEST(SUM_ROWS_SENT,1))  AS 배율,
       ROUND(SUM_ROWS_EXAMINED/GREATEST(COUNT_STAR,1))     AS 회당검사,   -- N+1 판별용
       SUM_CREATED_TMP_DISK_TABLES                         AS 디스크임시,
       SUM_SORT_SCAN                                       AS 정렬스캔,
       SUM_NO_INDEX_USED                                   AS 인덱스미사용,
       ROUND(SUM_TIMER_WAIT/1000000000)                    AS 총ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='petory'
  AND DIGEST_TEXT NOT LIKE '%INFORMATION_SCHEMA%'
  AND DIGEST_TEXT NOT LIKE '%performance_schema%'
  AND DIGEST_TEXT NOT LIKE 'TRUNCATE%'
ORDER BY SUM_ROWS_EXAMINED DESC LIMIT 20;   -- 패스 A
--       COUNT_STAR        DESC LIMIT 20;   -- 패스 B

-- 패스 C: 쓰기 경로만. 검사행이 크면 그만큼 많은 행을 잠갔다는 뜻이다.
--   ⚠️ 'SELECT ... FOR UPDATE' 는 앞부분이 SELECT 라 접두어로 못 거른다.
--      DIGEST_TEXT 뒤쪽의 FOR UPDATE 로 걸러야 하는데, 그러려면 §3-1(절단 함정)을 먼저 해결해야 한다.
SELECT LEFT(REPLACE(DIGEST_TEXT,'\n',' '), 60) AS 쿼리,
       COUNT_STAR                              AS 횟수,
       SUM_ROWS_AFFECTED                       AS 변경,
       SUM_ROWS_EXAMINED                       AS 검사,       -- 검사 >> 변경 이면 과잉 락
       SUM_NO_INDEX_USED                       AS 인덱스미사용,
       ROUND(SUM_TIMER_WAIT/1000000000)        AS 총ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME='petory'
  AND (DIGEST_TEXT LIKE 'UPDATE%' OR DIGEST_TEXT LIKE 'DELETE%'
       OR DIGEST_TEXT LIKE 'INSERT%' OR DIGEST_TEXT LIKE '%FOR UPDATE%')
ORDER BY SUM_ROWS_EXAMINED DESC LIMIT 20;
```

### 3-1. 🔴 `DIGEST_TEXT` 는 1024자에서 잘린다 — 테이블명으로 필터하지 마라

**실측 (2026-07-14).** `MeetupChatRoomRecoveryScheduler` 의 쿼리를 `DIGEST_TEXT LIKE '%meetup%'` 로 찾으려 했으나
**한 건도 안 잡혔다.** 쿼리는 digest 에 분명히 있었는데도(1회 / 1만 행 검사) 그렇다.

```
max_digest_length = 1024                 ← MySQL 기본값
저장된 digest 길이: 948자
digest 끝부분:  ... `o1_0`.`warning_count`, `m1_0`.`status`      ← SELECT 목록 중간에서 끊김
```

`SELECT m.*, o.*` 로 컬럼이 많다 보니 **`FROM meetup ... WHERE ... NOT EXISTS` 까지 도달하지 못하고 잘렸다.**
digest 텍스트에 테이블명·WHERE·`FOR UPDATE` 가 **아예 존재하지 않는다.**

**이것이 깨뜨리는 것:**

| 하려던 것 | 결과 |
|---|---|
| 테이블명으로 도메인 필터 (`LIKE '%board%'`) | 🔴 **넓은 SELECT 는 통째로 누락** |
| 패스 C 의 `LIKE '%FOR UPDATE%'` | 🔴 **비관적 락 쿼리를 못 잡는다** — `FOR UPDATE` 는 쿼리 맨 끝이라 항상 잘린다 |
| `LIKE 'UPDATE%'` · `'DELETE%'` · `'INSERT%'` | ✅ 접두어라서 안전 |

**해결 — 선행 조건 3 (§8):** `max_digest_length` 를 4096 으로 올린다. **MySQL 재시작이 필요하다** (동적 변경 불가).

```ini
# my.cnf
[mysqld]
max_digest_length = 4096
performance_schema_max_digest_length = 4096
```

> 올리기 전까지는 **digest 텍스트로 필터하지 말고 `SUM_ROWS_EXAMINED` 같은 수치로만 판단하라.**
> 그리고 어느 쿼리인지는 `LEFT(DIGEST_TEXT, 60)` 의 컬럼 별칭(`m1_0`, `b1_0`)으로 역추적하라.

**패스 B 읽는 법 — N+1 판정 기준**

curl **한 번**에 같은 digest 의 `COUNT_STAR` 가 목록 크기(예: 20)만큼 찍히면 N+1 이다.
`회당검사` 가 1~2 로 낮은데 `횟수` 만 높다면 거의 확실하다 (PK 단건 조회 반복 = 전형적인 지연로딩 N+1).

> `횟수` 는 초안에도 컬럼으로는 있었다. 다만 **정렬 기준이 아니라서 상위 20 밖으로 밀려 안 보였다.**
> 컬럼에 있는 것과 눈에 띄는 것은 다르다.

**패스 C 읽는 법 — 과잉 락 판정 기준**

`검사 >> 변경` 이면 과잉 락이다. `UPDATE ... SET x = x+1 WHERE id = ?` 가 1행을 바꾸는데 1만 행을 검사했다면,
WHERE 가 인덱스를 못 탄 것이고 **그 1만 행을 다 잠갔다는 뜻이다.** 동시 요청이 오면 여기서 경합이 터진다.

### 현재 데이터 규모 (측정이 유의미한 수준)

users 10,000 / pets 12,000 / board 50,000 / comment 150,000 / reaction 175,000 /
view_log 125,000 / meetup 5,000 / carerequest 3,000 / chatmessage 30,600 /
locationservice 22,905 / locationservicereview 20,000

> 재생성: `mysql ... petory < scripts/seed/seed-dev-data.sql`

---

## 4. 도메인별 감사 체크리스트

| # | 도메인 | 컨트롤러 | 엔드포인트 | `Page<>` | 우선순위 | 상태 |
|---|---|---|---|---|---|---|
| 1 | **board** | 2 | 24 | **13** | ★★★ | ✅ **감사 완료** → [board-2026-07-14.md](board-2026-07-14.md) · 신규: 깊은 페이지 OFFSET 폭발 / N+1·과잉락 **없음** 확인 |
| 2 | **care** | 3 | 16 | 5 | ★★★ | ✅ **완료** → [care](care-2026-07-14.md) · 🔴🔴 **검색 HTTP 500**(FULLTEXT 없음) / 인덱스 3개뿐 / 선택도 208배 오판 |
| 3 | **meetup** | 1 | 15 | 3 | ★★★ | ✅ **완료** → [meetup](meetup-2026-07-14.md) · 🔴 검색 페이징 없음(500건·51쿼리) · @BatchSize 는 정상 |
| 4 | **chat** | 3 | 18 | 2 | ★★ | ✅ **완료** → [etc](etc-domains-2026-07-14.md) · 커버링 인덱스 정상. ⚠️ 시드가 작아 대규모 대화방 미검증 |
| 5 | **location** | 3 | 11 | 1 | ★★ | ✅ **완료** → [etc](etc-domains-2026-07-14.md) · 인덱스 9개(SPATIAL+FULLTEXT) **모범 사례** |
| 6 | **user** | 2 | 13 | **3** | ★★ | ✅ **완료** → [etc](etc-domains-2026-07-14.md) · 🔴 `/api/pets/type/{t}` 7,667건 무제한 반환 + 154쿼리 |
| 7 | **notification** | 2 | 8 | - | ★ | ⬜ **측정 불가** — 시드에 알림 0건 |
| 8 | **payment** | 1 | 4 | 1 | ★ | ✅ **완료** → [etc](etc-domains-2026-07-14.md) · 문제 없음 |
| 9 | **file** | 1 | 2 | 1 | ★ | ⬜ **측정 불가** — 시드가 file 테이블을 만들지 않음 |
| 10 | **report** | 1 | 1 | 1 | ★ | ⬜ POST 하나뿐 (조회 경로 없음) |
| 11 | **admin** (13개 컨트롤러) | 13 | 16 | 다수 | ★★ | ✅ **완료** → [admin](admin-2026-07-14.md) · 🔴 **프로젝트 유일의 진짜 N+1**(care 목록 20건→60쿼리) · users 풀스캔 |
| 12 | **statistics** | (admin 소속) | 4 | — | ★ | ✅ **완료** → [admin](admin-2026-07-14.md) §5 · Daily Summary Pattern 정상, **최대 검사 1행 — 가장 깨끗함**. 자정 배치는 별개(미측정) |

**합계: 컨트롤러 34개 / 엔드포인트 189개**

---

## 5. 도메인을 가로지르는 시스템적 위험 (별도 스윕)

도메인별 감사와 **별개로** 전수 점검해야 하는 것들.

### 5.1 🔴 `Page<>` COUNT 쿼리 — 30개 (초안의 27개는 과소집계)

Spring Data 의 `Page<>` 는 목록 SELECT 와 **별개로 COUNT 쿼리**를 날린다.
board 에서 확인된 것: **180,003행 검사 / 141ms** — 목록 SELECT(4ms)보다 **35배 비싸다.**

**세는 단위는 "리포지토리 선언"이 아니라 "COUNT 가 실제로 나가는 호출 지점"이다** (§0.1).

| 경로 | 개수 | 비고 |
|---|---|---|
| `SpringDataJpa*` 에 **선언된** `Page<>` | 27 | board 13 / care 5 / meetup 3 / chat 2 / location·payment·report·file 각 1 |
| **상속** `findAll(Pageable)` — 선언 없이 COUNT 발생 | 1 | `UsersService:64` — users **1만 행 COUNT** |
| **QueryDSL** 로 조립한 `Page` | 2 | `JpaUsersAdapter` admin 사용자 목록 |
| **합계** | **30** | **그중 1개만 확인했다** |

#### COUNT 를 누가 만드는가 — 이게 문제의 핵심이다

| COUNT 출처 | 개수 | 위험 |
|---|---|---|
| `countQuery` **직접 작성** | 12 | 사람이 JOIN 을 걷어냈을 수 있음 — 개별 확인 |
| **Hibernate 자동생성** | 16 | 🔴 **본문 쿼리의 JOIN 을 그대로 물고 COUNT 한다** |
| `PageableExecutionUtils` | 2 | ✅ 마지막/단일 페이지면 **COUNT 를 아예 생략** |

**board 의 180,003행 COUNT 가 정확히 "자동생성" 케이스다.** `findBoardListItems` 는 `countQuery` 가 없어서
Hibernate 가 `FROM Board b JOIN b.user u` 를 그대로 둔 채 COUNT 를 만든다. **16개가 같은 구조다.**

실측으로 확인했다 (2026-07-14, 패스 A):

```
SELECT COUNT(b1_0.idx) ...   3회 호출 / 검사 180,003행 / 회당 60,001행
                                          └─ board 50,000 + users 10,000 + 1
                                             = JOIN 을 물고 두 테이블을 다 훑는다
```

**자동생성 COUNT 16개 — 어디에 있나 (수리 우선순위)**

| 도메인 | 자동생성 | 위치 |
|---|---|---|
| **board** | **6** | `SpringDataJpaBoardRepository` — 🔴 180,003행 COUNT 가 여기 |
| chat | 2 | `SpringDataJpaChatMessageRepository` |
| meetup | 2 | `SpringDataJpaMeetupRepository` |
| care | 1 | `SpringDataJpaCareRequestRepository` |
| location / payment / report / file | 각 1 | 리뷰 · 펫코인 · 신고 · 첨부 |
| **user** | **1** | 상속 `findAll(Pageable)` — `UsersService:64` |

> `MissingPetBoard`·`Comment`·`MissingPetComment` 는 **이미 `countQuery` 를 다 붙여놨다.** 손댈 필요 없다.

처방 후보 (위에서부터 우선 검토):
1. **`countQuery` 명시로 JOIN 제거** — `users` 조인이 필터에만 쓰이고 결과 수에 영향이 없으면 걷어낼 수 있다. 총건수 정확도를 **포기하지 않는다.**
2. **`PageableExecutionUtils.getPage()`** — 이미 `JpaUsersAdapter` 가 쓰는 패턴. 한 페이지에 다 들어가면 COUNT 자체를 건너뛴다. **총건수 정확도를 포기하지 않는다.**
3. `Slice` 전환 (COUNT 없음) → 무한스크롤/"더보기" UI 필요. 이미 `MeetupService.getAvailableMeetups()` 하나가 이 방식이다.
4. 총건수 캐싱 (Redis, TTL 수 분) → 총건수가 잠시 어긋나도 되는 화면 한정.

> 1·2번을 먼저 검토하라. 초안은 "총건수 정확도를 포기해야 한다"를 전제로 깔았는데, **16개 중 상당수는 포기하지 않고도 고쳐진다.**

### 5.2 스케줄러 — 9개

사용자가 체감하지 못하므로 **더 오래 숨는다.**

`MeetupChatRoomRecoveryScheduler.findWithoutChatRoom()` 에서
**1만 행 검사 / 0행 반환 / 인덱스 미사용** 발견. 나머지 8개 미확인.

`StatisticsScheduler`, `CareRequestScheduler`, `BoardPopularityScheduler`,
`LocationServiceScoreScheduler`, `UserSanctionScheduler`, `UserDormantScheduler`,
`MeetupScheduler`, `BoardListQueryPlanMaintainer`

> 감사 방법: cron 을 앞당기거나 메서드를 직접 호출한 뒤 digest 를 본다.

### 5.3 네이티브 쿼리 — 21곳

`nativeQuery = true` 는 Hibernate 가 생성하지 않고 **사람이 직접 쓴 SQL** 이므로
옵티마이저 계획을 개별 확인해야 한다.

board, boardReaction, boardViewLog, commentReaction, care, chat, location, meetup, dailyStatistics

(공간 검색처럼 잘 짜인 것도 있다 — baseline §2 에서 `ST_Within` 이 인덱스를 제대로 타는 것 확인)

### 5.4 🔴 쓰기 경로 — 초안에서 통째로 빠졌던 범위

초안의 스캔 필터는 `DIGEST_TEXT LIKE 'SELECT%'` 였다. **INSERT/UPDATE/DELETE 가 전부 제외됐다.**
그런데 `CLAUDE.md` 가 꼽는 이 프로젝트의 핵심 동시성 패턴은 **전부 쓰기 경로**다.

| 패턴 | 위치 | 봐야 할 것 |
|---|---|---|
| 비관적 락 (`findByIdForUpdate`) | 펫코인, 에스크로 | `SELECT ... FOR UPDATE` 가 **PK 로 정확히 1행만 잠그는가.** 인덱스를 못 타면 잠그는 행이 불어나 락 경합으로 번진다 |
| 원자적 증가 UPDATE | 경고 횟수, 모임 인원 | `UPDATE ... SET x = x + 1 WHERE ...` 의 WHERE 가 인덱스를 타는가 |
| 대량 UPDATE | `UserDormantScheduler` 등 | 한 트랜잭션이 몇 행을 잠그는가 |

**감사 방법:** 패스 C(쓰기 경로, 검사행순)로 스캔하고, 의심되는 UPDATE 는 `EXPLAIN UPDATE ...` 로 계획을 본다.
쓰기 엔드포인트를 curl 로 실제 태워야 digest 에 잡힌다 (절차 [2]).

#### ⚠️ 이 감사로 관측할 수 있는 것과 없는 것

| | 관측 가능? | 이유 |
|---|---|---|
| **과잉 락** (잠글 필요 없는 행까지 잠금) | ✅ 가능 | 패스 C 의 `검사 >> 변경` 으로 보인다. **요청 1개로도 관측된다** |
| **락 경합** (실제로 서로 기다림) | ❌ **불가능** | curl 을 **한 번에 하나씩** 던지므로 경합할 상대가 없다. 경합은 원리적으로 발생하지 않는다 |

**락 경합은 이 감사의 범위 밖이다. 별도의 동시성 테스트가 필요하다** (같은 자원에 동시 요청을 쏘는 부하 테스트).
그때 볼 지표는 digest 가 아니라 이것들이다:

```sql
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';   -- 테스트 전후로 diff (waits / time / current_waits)
SELECT * FROM performance_schema.data_lock_waits;   -- 대기 중인 순간에만 행이 보인다
```

> 단, **과잉 락을 먼저 없애면 경합은 대부분 따라서 사라진다.** 잠그는 행이 줄면 부딪힐 확률도 준다.
> 그래서 이 감사(과잉 락 제거)를 **먼저** 하고 동시성 테스트를 **나중에** 하는 순서가 맞다.

### 5.5 N+1 — 초안이 한 번도 언급하지 않았던 것

`CLAUDE.md` 가 성능 항목 **1번**으로 꼽는 것이 N+1 인데 초안에는 단어조차 없었다.
그리고 초안의 스캔 쿼리(`ORDER BY SUM_ROWS_EXAMINED DESC`)로는 **구조적으로 안 잡힌다** —
N+1 은 개별 쿼리가 값싸서 검사행 순위에서 밀리기 때문이다.

**패스 B(호출횟수순)가 이걸 잡으라고 있는 것이다** (§3).
목록 엔드포인트를 curl 1회 호출했는데 같은 digest 가 20번 찍히면 N+1 이다.

> 지연로딩 연관이 있는 엔티티(`Board.user`, `Meetup.host`, `ChatMessage.sender` 등)를
> 목록에서 건드리는 모든 경로가 후보다.

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
| 🆕 **COUNT 자동생성 16개** — 본문 JOIN 을 그대로 물고 COUNT (board 180,003행이 이 케이스) | §5.1 |
| 🆕 **`UsersService:64` `findAll(pageable)`** — users 1만 행 COUNT, 초안 집계에서 누락 | §0.1 |
| 🆕 **쓰기 경로 전체 미감사** — 비관적 락·원자적 UPDATE 의 락 범위 미확인 | §5.4 |
| 🆕 **N+1 전체 미감사** — 도구가 못 잡는 상태였음 | §5.5 |
| 🆕 **`CLAUDE.md` 문서 드리프트** — "게시글 상세 캐시(`@Cacheable`)"라 적혀 있으나 `BoardService:156` 에서 주석 처리됨 | §1-1 |
| 🆕 **스케줄러가 측정을 오염시킴** — `MeetupChatRoomRecoveryScheduler` 5분마다 발화, 끌 스위치가 없음 | §2-1 |
| 🆕 **락 경합은 이 감사로 관측 불가** — 단일 curl 이라 경합 자체가 안 생김. 별도 동시성 테스트 필요 | §5.4 |
| 🆕 **`DIGEST_TEXT` 1024자 절단** — 넓은 SELECT 는 `FROM`·`FOR UPDATE` 가 잘려나가 텍스트 필터가 무력화됨 | §3-1 |

---

## 8. 착수 전 선행 조건 — 도메인 1번보다 먼저 할 것

계기판이 틀린 채로 감사를 시작하면 **11개 도메인 문서를 전부 다시 돌려야 한다.**
아래 2개를 먼저 끝내고 시작한다.

| # | 할 일 | 왜 먼저인가 | 상태 |
|---|---|---|---|
| **1** | **`SchedulingConfig` 분리 + `petory.scheduling.enabled` 스위치** (§2-1) | 5분마다 도는 `MeetupChatRoomRecoveryScheduler`(1만 행 검사)가 **모든 측정에 섞여 들어온다.** 코드 변경 1건, 운영 동작 불변 | ✅ **완료 · A/B 검증됨** (아래) |
| **2** | **§3 의 3-패스 스캔 쿼리 확정** | 초안 쿼리로는 **N+1 이 정렬에서 밀려 안 보이고, 쓰기 경로는 필터에서 잘려 안 보인다** | ✅ 완료 (실측 검증) |
| **3** | **`max_digest_length` 1024 → 4096** (§3-1) | digest 텍스트가 잘려 **`FOR UPDATE`(비관적 락)를 못 잡는다.** MySQL 재시작 필요 | ✅ 완료 (`my.cnf`, 백업 `my.cnf.bak-20260714`) |

> **선행 조건 3개 모두 완료. 계기판이 검증됐다.** board 감사부터 이 도구로 수행했다 → [board-2026-07-14.md](board-2026-07-14.md)

**선행 조건 1 — A/B 검증 결과 (2026-07-14)**

원칙 6(2단계) 대로, **끄기 전에 문제 신호가 실제로 나타나는지부터** 확인했다.
`fixedDelay` 는 기동 직후 즉시 1회 발화하므로 이것으로 A/B 가 된다.

| | 명령 | digest 결과 |
|---|---|---|
| **A. 스케줄러 ON** | `bootRun --args='--server.port=8081'` | 🔴 `SELECT m1_0.idx, ...` **1회 / 10,000행 검사** — 오염 확인 |
| **B. 스케줄러 OFF** | `bootRun --args='... --petory.scheduling.enabled=false'` | ✅ **5,000행 이상 쿼리 0건** — 사라짐 |

> 스케줄러 로그가 안 찍히는 것은 증거가 아니다 — 고아 모임이 0건이면 이 스케줄러는 **로그를 남기지 않는다.**
> digest 를 봐야 알 수 있다.

> **계기판부터 고친다. 그게 §0 의 교훈이다.**
> 그리고 계기판을 고쳤으면 **계기판 자체를 실측으로 검증한다** — `SUM_LOCK_TIME` 이 그래서 걸렸다 (§3).
