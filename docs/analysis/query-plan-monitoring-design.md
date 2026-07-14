---
date: 2026-07-14
domains: [board, meetup, global]
type: design-analysis
problem: query-plan-monitoring-generalization
status: analysis-only
metric: "실제 API 호출로 재검증 — /api/boards 의 비용은 COUNT 쿼리(180,003행/141ms)가 지배. 히스토그램으로 고친 목록 SELECT 는 120행/4ms. digest 스캔 1회에 문제 쿼리 3건 즉시 포착"
related: [docs/analysis/entity-schema/evidence/query-baseline-2026-07-13.md]
---

# 쿼리 계획 감시의 일반화 — 설계 분석 (v2)

> **구현 전 분석 문서다.**
> v1(2026-07-13 작성)의 근거가 잘못되어 실제 API 호출로 재검증하고 전면 개정했다. §9 참고.
> 전제가 되는 실측: [query-baseline-2026-07-13.md](./entity-schema/evidence/query-baseline-2026-07-13.md)

---

## 0. 배경

게시글 목록 쿼리가 옵티마이저의 선택도 오판으로 매 페이지마다 전체 정렬을 하던 문제를
`BoardListQueryPlanMaintainer` 로 고쳤다(목록 SELECT 0.17s → 0.00s).

**그런데 그게 board 목록 쿼리 하나에만 하드코딩되어 있다.** 프로젝트 전체 쿼리는 손도 대지 않았다.
이 문서는 그것을 어떻게 일반화할지에 대한 분석이다.

**그리고 재검증 결과, 그 "고쳤다"는 것도 절반만 맞았다.** (§2)

---

## 1. 🔴 재검증에서 드러난 것 — 고친 것은 더 싼 쪽이었다

앱을 실제로 띄우고 **시드 계정으로 로그인해 `GET /api/boards` 를 3회 호출**한 뒤
`performance_schema` 를 확인했다. (v1 에서는 이 검증을 하지 않았다 — §9)

| 쿼리 | 호출 | 반환행 | **검사행** | **총 시간** | |
|---|---|---|---|---|---|
| `SELECT COUNT(b1_0.idx) FROM board b1_0 JOIN users ...` | 3 | 3 | **180,003** | **141ms** | ❌ |
| `SELECT m1_0.idx, ... FROM meetup ... NOT EXISTS(...)` | 1 | 0 | **10,000** | 32ms | ❌ |
| `SELECT b1_0.idx, b1_0.title, ...` ← **히스토그램으로 고친 목록 SELECT** | 3 | 60 | **120** | **4ms** | ✅ |
| `SELECT br1_0.board_idx, br1_0.reaction_type ...` (리액션 배치) | 3 | 51 | 120 | 5ms | ✅ |

### 무슨 뜻인가

- **고친 목록 SELECT: 120행 / 4ms.** 히스토그램이 제대로 먹혔다 (검사행이 50,000 → 120)
- **안 고친 COUNT: 180,003행 / 141ms.** 목록 SELECT 보다 **35배 비싸다**

`/api/boards` 응답이 여전히 ~100ms 인 이유가 이것이다.
**히스토그램은 문제의 작은 절반만 고쳤고, 더 비싼 절반은 손도 대지 않았다.**

> **왜 몰랐나**: 어제는 SQL 을 직접 던져 측정했지, **API 를 한 번도 호출하지 않았다.**
> 리포지토리의 `Page<BoardListItemDTO>` 는 목록 SELECT 와 **COUNT 쿼리 2개**를 날린다.
> 나는 그중 하나만 보고 있었다.

### COUNT 쿼리의 실행 계획

```
table: u (users)   type: ALL   key: NULL   rows: 9,632   Extra: Using where
table: b (board)   type: ref   key: idx_board_user_deleted_created   Extra: Using index
```

`ORDER BY` 가 없으니 filesort 는 안 생긴다. 하지만 **여전히 users 를 풀스캔하고
게시글 전체를 세므로 매 페이지 로드마다 60,001행을 검사한다.**
`Using index`(커버링 인덱스)라 0.02s 로 버티고 있을 뿐, 데이터가 늘면 선형으로 증가한다.

**히스토그램으로는 이걸 못 고친다.** COUNT 는 어차피 모든 매칭 행을 세야 하기 때문이다.
해결하려면 **정확한 총건수를 포기**해야 한다:

| 방법 | 대가 |
|---|---|
| `Slice` 사용 (COUNT 안 함) | 총 페이지 수를 못 보여줌 → 무한스크롤/"더보기" UI 로 변경 필요 |
| 총건수 캐싱 (Redis, TTL 수 분) | 총건수가 몇 분 낡음. 대부분의 게시판에서 허용 가능 |
| 근사치 (`information_schema.tables.table_rows`) | 부정확 + 사용자 필터를 반영 못 함 |

**이건 별도 과제다. 이 문서의 범위가 아니지만 반드시 기록해둔다.**

---

## 2. 데이터가 늘면 어떻게 악화되나

"선형으로 증가하나?" 에 대한 답 — **선형보다 나쁘다. 계단이 있다.**

| | 비용 증가 |
|---|---|
| **고친 목록 SELECT** | **1페이지는 거의 상수** (인덱스 20행 읽고 종료). 단, `OFFSET` 이 커지면 그만큼 인덱스를 걸어야 하므로 O(OFFSET) |
| **수정 전 목록 SELECT** | 임시테이블 N행 적재 + N log N 정렬 → 데이터에 비례. **`OFFSET` 과 무관하게 항상 전체 정렬** |
| **COUNT 쿼리** (미해결) | O(N). 매칭 행을 전부 세야 함 |

### 그리고 이미 절벽을 넘어 있었다 (수정 전 기준)

```
tmp_table_size = max_heap_table_size = 16MB
목록 쿼리 1회 실행 → Created_tmp_disk_tables: 493 → 494   (+1)
```

임시테이블이 메모리 한도(16MB)를 넘겨 **디스크에 기록**되고 있었다.
게시글 50,000행 × `content`(LONGTEXT) 가 16MB 를 초과하기 때문이다.

> **정확한 표현**: 데이터에 비례해 증가하다가, **임계점(`tmp_table_size`)에서 디스크로 넘어가며
> 계단식으로 급증**한다. 단순 선형이 아니라 **계단이 있는 증가**다.

> `tmp_table_size` 를 올리는 것은 답이 아니다. 디스크 I/O 를 메모리 낭비로 바꿀 뿐이고,
> 근본 원인(잘못된 계획)은 그대로다. 계획을 고치면 임시테이블 자체가 생기지 않는다.

---

## 3. 현재 구현(`BoardListQueryPlanMaintainer`)의 한계

### 3.1 하드코딩

board 목록 쿼리 전용이다. 감시할 쿼리가 늘면 클래스를 복붙하게 된다.

> **용어 정정**: "다형성이 부서졌다"는 정확한 표현이 아니다. 다형성(polymorphism)은
> *같은 인터페이스로 여러 구현을 갈아끼우는 것*인데, 이 클래스는 애초에 인터페이스가 없다.
> **"깨진" 게 아니라 "처음부터 일반화되지 않은"** 것이다. 지적의 본질은 맞다.

### 3.2 🔴 체크 쿼리가 리포지토리와 갈라진다

Maintainer 안에 **SQL 문자열이 복사되어 있다.** `SpringDataJpaBoardRepository` 의 JPQL 이 바뀌면
**체크는 낡은 쿼리를 검사하면서 조용히 초록불을 켠다.**

**2026-07-13 오전에 잡은 "스키마 사본 4개" 문제와 정확히 같은 종류다.** 같은 함정을 다시 판 셈이다.

### 3.3 🔴 애초에 감시 대상을 잘못 골랐다

§1 에서 드러났듯 **더 비싼 쿼리(COUNT)를 감시 대상에서 빠뜨렸다.**
사람이 손으로 대상을 고르면 이런 누락이 반드시 생긴다.
**→ 대상을 사람이 고르는 방식 자체가 문제다.**

---

## 4. `performance_schema` — 실제로 작동함을 검증했다

MySQL 이 **앱이 날린 모든 쿼리를 이미 기록하고 있다.**

```sql
SELECT LEFT(REPLACE(DIGEST_TEXT,'\n',' '), 50)             AS 쿼리,
       COUNT_STAR                                          AS 횟수,
       SUM_ROWS_SENT                                       AS 반환,
       SUM_ROWS_EXAMINED                                   AS 검사,
       ROUND(SUM_ROWS_EXAMINED/GREATEST(SUM_ROWS_SENT,1))  AS 배율,
       SUM_CREATED_TMP_DISK_TABLES                         AS 디스크임시,
       SUM_SORT_SCAN                                       AS 정렬스캔,
       SUM_NO_INDEX_USED                                   AS 인덱스미사용,
       ROUND(SUM_TIMER_WAIT/1000000000)                    AS 총ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'petory' AND DIGEST_TEXT LIKE 'SELECT%'
ORDER BY SUM_ROWS_EXAMINED DESC;
```

**API 3회 호출만으로 문제 쿼리 3건이 즉시 잡혔다** (§1 표). 사람이 고르지 않았는데도.

### 쓸 수 있는 신호

| 지표 | 의미 |
|---|---|
| **검사행 / 반환행 배율** | 3행 주려고 180,003행을 읽으면 60,001배. **가장 강력한 단일 지표** |
| `SUM_CREATED_TMP_DISK_TABLES` | 디스크 임시테이블 — 확실한 적신호 |
| `SUM_SORT_SCAN` | 정렬을 위해 전체를 훑음 |
| `SUM_NO_INDEX_USED` | 인덱스 없이 실행됨 |
| `SUM_TIMER_WAIT` | 총 소요 시간 — 실제 영향도 판단 |

---

## 5. `performance_schema` 의 함정 (v1 에서 빠뜨린 것)

### 5.1 🔴 누적값이다 — 회귀 감지에는 델타가 필요하다

모든 지표가 `SUM_*` 다. **서버 기동 이후 누적**이므로:

- `SUM_CREATED_TMP_DISK_TABLES > 0` 은 **한 번이라도 발생하면 영원히 참**이다
- 문제를 고쳐도 이 값은 0으로 돌아가지 않는다 → **고친 뒤에도 알람이 계속 울린다**

**해결책**: ① 스캔할 때마다 이전 스냅샷과의 **델타**를 계산하거나,
② `TRUNCATE TABLE performance_schema.events_statements_summary_by_digest` 로 주기적으로 리셋한다.

> **이건 구현의 핵심 설계 포인트다.** 누적값을 그대로 임계값과 비교하면 감시 장치가 무용지물이 된다.

### 5.2 휘발성이다

- **MySQL 재시작 시 초기화**된다. 장기 추이는 남지 않는다
- `performance_schema_digests_size = 10000` — 이를 넘으면 초과분이 하나의 익명 digest 로 뭉친다
- `performance_schema_max_digest_length = 1024` — 긴 쿼리는 잘려서 서로 다른 쿼리가 같은 digest 로 합쳐질 수 있다

**→ 장기 보관이 필요하면 스캔 결과를 앱 쪽 테이블에 스냅샷으로 남겨야 한다.**

### 5.3 오탐이 있다

- **정합성 검증 쿼리**(`verify-data-integrity.sql`)가 검사/반환 152,769배로 1등을 차지했다.
  전체 집계 쿼리는 **원래** 많이 읽는 게 정상이다
- 통계 배치, 관리자 리포트도 마찬가지다

**→ 자동 알람이 아니라 "어디가 아픈지 찾는 레이더"다. 제외 목록을 두고 사람이 판단해야 한다.**

---

## 6. 제안 구조 — 2층

### Layer 1 · 레이더 (넓게, 자동)

digest 를 주기적으로 스캔해 이상 쿼리 후보를 뽑는다.

- **설정 0.** 프로젝트 전체 쿼리를 자동 커버. 새 쿼리가 생겨도 자동 포착
- **대상을 사람이 고르지 않는다** — §3.3(COUNT 누락) 같은 사고를 구조적으로 막는다
- 델타 기반(§5.1). 제외 목록으로 오탐 관리(§5.3)
- 출력은 **후보 목록**이며 사람이 판단한다

### Layer 2 · 가드레일 (좁게, 정밀)

레이더로 확인된 **진짜 문제 쿼리**에만 회귀 방지 가드를 건다.

```java
interface QueryPlanGuard {
    String name();                  // 메트릭 태그
    DigestMatcher target();         // ★ 어떤 쿼리를 지키는가 — v1 인터페이스에 빠져 있던 것
    Set<Violation> forbidden();     // TEMP_DISK / SORT_SCAN / NO_INDEX / RATIO 임계
    long minRowsToVerify();         // 이 아래면 검증이 무의미하므로 생략
}
```

도메인별 구현체를 만들고 스케줄러가 `List<QueryPlanGuard>` 를 주입받아 순회한다.
**이것이 진짜 다형성이다.** `BoardListQueryPlanMaintainer` 는 이 인터페이스의 첫 구현체가 된다.

> **v1 인터페이스의 결함**: `name()/forbidden()/minRowsToVerify()` 만 있고
> **"어떤 쿼리를 지키는지" 식별자가 없었다.** 인터페이스로서 성립하지 않는다.

### 별개 관심사 — 통계/히스토그램 유지보수

"어떤 테이블의 어떤 컬럼에 히스토그램이 필요한가" 는 쿼리별 가드와 **다른 관심사**다.
선언 목록(`(table, columns)`)을 모아 중복 제거 후 `ANALYZE` 를 돌리는 별도 컴포넌트가 맞다.

---

## 7. 구현 전에 풀어야 할 설계 문제

### (a) 🔴 체크 쿼리가 리포지토리와 갈라지는 문제 (§3.2)

SQL 을 복붙하면 사본이 된다. 후보:

1. Hibernate `StatementInspector` 로 실제 나가는 SQL 캡처 → `EXPLAIN`
   - **막히는 지점**: 캡처한 SQL 은 `?` 플레이스홀더다. `EXPLAIN` 하려면 파라미터 바인딩이 필요
2. `EXPLAIN FOR CONNECTION <id>` — 실행 중인 순간을 잡아야 해 스케줄러 방식과 안 맞는다
3. **`EXPLAIN` 을 포기하고 digest 실측 지표로 대체**
   - digest 에 `디스크임시 / 정렬스캔 / 인덱스미사용 / 검사배율` 이 이미 기록된다
   - **쿼리 문자열을 들고 있을 필요가 사라진다** → (a) 문제가 **원천 소멸**
   - 대신 "계획이 어떻게 생겼는지"는 못 본다. **증상만 본다**

> **3번이 유력하다.** 사본을 만들지 않는 유일한 길이고, §1 에서 실제로 작동함을 확인했다.
> **증상 감지(자동)** 와 **원인 진단(사람이 `EXPLAIN`)** 을 분리한다.
> 다만 §5.1(델타 처리)을 반드시 함께 풀어야 한다.

### (b) "나쁜 계획"의 정의가 쿼리마다 다르다

`Using filesort` 가 항상 나쁜 것은 아니다. 작은 결과셋 정렬은 저렴하다.
관리자 통계 쿼리의 풀스캔은 정상이다. **금지 신호를 쿼리별로 선언**해야 한다.

### (c) 검증 없는 가드는 헛돈다

각 가드마다 **"히스토그램/인덱스를 없애면 실제로 그 신호가 나타나는지"** 를
CI 에서 먼저 확인해야 한다. 없으면 가드가 아무것도 지키지 못한 채 초록불만 켠다.
(`BoardListQueryPlanMaintainerTest` 의 2단계 구조가 첫 사례다.)

---

## 8. 제안하는 순서

v1 의 "N=1 이라 추상화할 수 없다" 는 전제는 **이제 무효다.**
digest 스캔 1회에 문제 쿼리 3건이 나왔다. **셀 도구가 검증됐으므로 바로 셀 수 있다.**

```
1. Layer 1 (digest 스캐너) 구현 — 델타 처리(§5.1) 포함
2. 앱의 주요 API 를 한 바퀴 돌려 전체 쿼리 스캔      ← 몇 개인지 "센다"
3. 문제 쿼리 목록 확보 (현재 알려진 것: board COUNT, meetup findWithoutChatRoom, +?)
4. 그 목록을 보고 Layer 2 추상화 설계
5. 각 가드에 CI 2단계 테스트 (필수)
6. board Maintainer 의 SQL 복붙 문제(§3.2)도 함께 해소
```

---

## 9. v1(2026-07-13)에서 틀렸던 것 — 기록

**v1 §4 의 근거가 가짜였다.** "digest 에서 board 목록 쿼리가 잡혔다" 고 썼지만,
실제로 잡힌 것은 **내가 터미널에서 손으로 친 `SELECT SQL_NO_CACHE ...` 쿼리**였다.
앱은 그 세션에서 `/api/boards` 를 **한 번도 처리한 적이 없었다.**

즉 **"digest 로 앱 쿼리를 잡을 수 있다"를 증명한 적이 없으면서 증명했다고 썼다.**

이번에 앱을 띄우고 시드 계정으로 로그인해 실제 API 를 호출하고 나서야:
- digest 가 **앱의 Hibernate 쿼리를 실제로 잡는다는 것**을 확인했고 (아이디어는 옳았다)
- 동시에 **더 비싼 COUNT 쿼리를 통째로 놓치고 있었다는 것**도 드러났다 (§1)

> **교훈: SQL 을 직접 던져 측정한 것은 "앱이 실제로 하는 일"이 아니다.**
> 어제 3번이나 "실제 코드가 날리는 쿼리를 봐야 한다"고 적어놓고, 정작 같은 실수를 했다.

---

## 10. 아직 확인하지 않은 것

- **프로젝트 전체에 이런 쿼리가 몇 개인가** — 게시판 목록 API 하나만 호출했다.
  care / meetup / chat / location 등 나머지 API 는 아직 안 돌려봤다
- **`Page<>` COUNT 쿼리 문제는 board 만의 것이 아니다** — 페이징을 쓰는 모든 목록 API 가
  같은 구조다. 전수 확인 필요
- **`carerequest` 주변 검색에 공간 인덱스 없음** (별건, baseline 문서 §4)
- **`file.idx_file_target`** 미측정 (시드가 file 테이블을 만들지 않음)
