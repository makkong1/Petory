---
date: 2026-07-13
domains: [board, location, care, meetup]
type: performance-baseline
problem: query-plan-baseline
status: verified
metric: "board 목록 1페이지 0.09s (전 페이지 동일 — 매번 5만행 filesort). 공간인덱스는 정상(검사행 215 vs 22,737). carerequest 주변검색은 인덱스 없음"
related: [docs/analysis/entity-schema/01-index-analysis.md, scripts/seed/README.md]
---

# 쿼리 성능 baseline (2026-07-13)

## 왜 이 문서가 필요했나

기존 성능 테스트는 **100~500행짜리 픽스처**에서 측정한다. 그 규모에서는:

- MySQL 옵티마이저가 **인덱스를 무시하고 풀스캔**한다 → 인덱스를 걸든 말든 측정값이 같다
- ms 차이가 노이즈에 묻힌다
- 결과적으로 **리팩토링 전후 비교가 무의미**해진다

N+1 테스트(쿼리 *개수* 측정)는 행 수와 무관하므로 여전히 유효하다. 하지만 **인덱스 효과와
쿼리 계획**은 실데이터 규모에서만 드러난다. 그래서 시드 데이터(`scripts/seed/`)를
board 50,000 / comment 150,000 규모로 만들고, 거기에 직접 측정했다.

**측정 환경**: MySQL 8.4.10, 로컬 `petory` DB, `SQL_NO_CACHE`, 3회 중 최소값

---

## 1. 🔴 게시글 목록 페이징 — 매 페이지가 전체 정렬 비용을 낸다

`SpringDataJpaBoardRepository.findBoardListItems` (`BOARD_LIST_ITEM_SELECT`)

```sql
SELECT ... FROM board b JOIN users u ON u.idx = b.user_idx
WHERE b.is_deleted = 0 AND u.is_deleted = 0 AND u.status = 'ACTIVE'
ORDER BY b.created_at DESC LIMIT 20 OFFSET ?
```

### 현재 실행 계획

```
table: u (users)   type: ALL   key: NULL   rows: 9,886
                   Extra: Using where; Using temporary; Using filesort
table: b (board)   type: ref   key: idx_board_user_deleted_created   rows: 4
```

**`users` 를 풀스캔한 뒤, 조인 결과 전체를 임시테이블 + filesort 로 정렬한다.**
`board.created_at` 인덱스가 정렬에 쓰이지 않는다.

### 측정 (OFFSET 별 실행시간)

| OFFSET | 현재 계획 | `board` 를 먼저 읽을 때 (STRAIGHT_JOIN) |
|---|---|---|
| **0** (1페이지) | **0.09s** | **0.00s** |
| 1,000 | 0.09s | 0.00s |
| 25,000 | 0.11s | 0.05s |
| 49,000 | 0.11s | 0.09s |

**현재 계획은 모든 페이지가 똑같이 느리다.** OFFSET 과 무관하게 매번 5만 행을 정렬하기 때문이다.
`board` 를 먼저 읽으면 `Using temporary; Using filesort` 가 사라지고, 인덱스 순서대로 읽다가
`LIMIT` 에서 조기 종료한다:

```
table: b   type: ref     key: idx_board_deleted_created   rows: 24,310   Extra: NULL
table: u   type: eq_ref  key: PRIMARY                     rows: 1
```

**사용자가 실제로 보는 것은 1~2페이지다.** 정확히 거기서 손해를 전부 보고 있다.

### 원인 — 왜 하필 `users` 를 먼저 읽었나

`EXPLAIN` 의 **`filtered`** 컬럼에 답이 있다.

```
table: u   type: ALL   rows: 9894   filtered: 1.00     ← 1%
```

`filtered: 1.00` = **"이 테이블을 다 읽으면 그중 1%만 조건을 통과할 것이다"** 라는 옵티마이저의 예상이다.
`users.status` 에 값 분포 통계가 없어서, MySQL 이 등호 조건에 쓰는 **기본 추측값(약 1%)** 이 그대로 들어갔다.

실제로는 **10,001명 중 10,001명 전부가 통과**한다. 100배 오판이다.

#### 옵티마이저는 이렇게 계산했다

| 방법 | 예상 비용 |
|---|---|
| **users 먼저** | 9,894행 훑음 → 1%만 남아 **99명** → 각자 board 4건씩 조회 → **≈ 400행** |
| board 먼저 | board 24,310행 → 각각 users 조회 → **24,310번** |

**400 vs 24,310.** 이러면 users 를 먼저 읽는 게 압도적으로 싸 보인다.
**옵티마이저의 추론 자체는 완벽했다. 입력값이 틀렸을 뿐이다.**

실제로는 99명이 아니라 10,001명이 통과했고, `10,001 × 5 = 50,000행` 이 쏟아졌다.

#### 그리고 드라이빙 테이블이 정렬 순서를 결정한다

| | 행이 나오는 순서 | 정렬 |
|---|---|---|
| **users 먼저** | 유저별로 뭉쳐서 나옴 | ❌ `created_at` 순이 아니므로 **전부 모아 따로 정렬** (filesort) |
| **board 먼저** | `idx_board_deleted_created` = `(is_deleted, created_at)` → **이미 created_at 순** | ✅ 정렬 불필요, **20행 채우고 즉시 종료** |

**`ORDER BY` 대상 컬럼(`b.created_at`)이 board 것인데 board 가 드라이빙 테이블이 아니면,
board 의 정렬 인덱스를 쓸 방법이 없다.** 그래서 filesort 가 강제된다.

#### "users 가 행이 적어서 고른 것 아닌가?"

절반만 맞다. **행 수 자체보다 "필터 통과 후 몇 행 남느냐"(선택도) 가 결정적이다.**

옵티마이저가 처음부터 `filtered: 100.00` 인 걸 알았다면:

```
users 먼저: 9,894 × 100% = 9,894명 → × 5건 = 50,000행 → 정렬까지   ← 비쌈
board 먼저: 인덱스 순서대로 20행 읽고 종료                          ← 쌈
```

**당연히 board 를 골랐을 것이다.** 실제로 히스토그램을 주자 정확히 그렇게 바뀌었다.

> `users(is_deleted, status)` 인덱스를 추가하는 것으로는 해결되지 않는다.
> 풀스캔은 없어지지만 **users 가 드라이빙 테이블인 한 filesort 는 그대로 남는다.**
> 문제는 접근 경로가 아니라 **조인 순서**이고, 조인 순서를 바꾸려면 **비용 계산의 입력(통계)** 을 고쳐야 한다.

### 해결: 히스토그램 (`BoardListQueryPlanMaintainer`)

```sql
ANALYZE TABLE users UPDATE HISTOGRAM ON status, is_deleted WITH 16 BUCKETS;
```

값 분포를 알려주면 옵티마이저가 `board` 를 먼저 읽고, `idx_board_deleted_created` 로 정렬 없이
`LIMIT` 에서 조기 종료한다. **`Using temporary; Using filesort` 가 사라진다.**

```
table: b   type: ref     key: idx_board_deleted_created   rows: 20   Extra: NULL
table: u   type: eq_ref  key: PRIMARY                     rows: 1
```

| | 1페이지 |
|---|---|
| 히스토그램 없음 | **0.17s** |
| 히스토그램 있음 | **0.00s** |

인과는 A/B/A 로 확인했다 — 히스토그램 적용(0.00s) → 제거(0.17s) → 재적용(0.00s).

#### ⚠️ `ANALYZE TABLE` 만으로는 고쳐지지 않는다 (검증함)

| | 통계상 board 행 수 | 계획 | 시간 |
|---|---|---|---|
| ① 현재 (통계 낡음) | 10,872 | ❌ filesort | 0.17s |
| ② `ANALYZE TABLE users, board` | 48,620 (정확해짐) | ❌ **여전히 filesort** | 0.18s |
| ③ ② + 히스토그램 | 48,620 | ✅ 정상 | **0.00s** |

**행 수 통계를 정확히 알려줘도 소용없다. 값 분포(히스토그램)가 있어야 한다.**

> 부수 발견: 대량 INSERT/TRUNCATE 후 InnoDB 통계가 낡은 채로 남는다(board 를 10,872건으로 알고
> 있었으나 실제 50,000건). 이 쿼리의 원인은 아니지만 별도 문제이므로 Maintainer 가 `ANALYZE TABLE` 도 함께 돈다.

### 히스토그램의 약점과 보완

히스토그램은 **자동 갱신되지 않고, 실패해도 앱은 정상 동작한다. 조용히 느려질 뿐이라 아무도 모른다.**
(오늘 발견한 이 문제 자체가 그렇게 숨어 있었다.) 그래서 `BoardListQueryPlanMaintainer` 는
**갱신만 하고 끝내지 않는다:**

1. `ANALYZE TABLE users, board` — 행 수 통계
2. `ANALYZE TABLE users UPDATE HISTOGRAM ON status, is_deleted` — 값 분포
3. **`EXPLAIN FORMAT=JSON` 으로 실제 계획을 다시 뽑아 `using_filesort` / `using_temporary_table` 이 없는지 검증**
4. 회귀했으면 `ERROR` 로그 + 메트릭 `petory.board.list_query_plan_healthy = 0` (Prometheus 알람)

**성공 조건은 "ANALYZE 를 실행했다" 가 아니라 "계획에서 filesort 가 사라진 것을 확인했다" 이다.**

실행 시점: **앱 기동 직후**(신규 배포·빈 DB 에 데이터가 쌓인 뒤 재기동하는 경우를 커버) + **매일 03:10**.
게시글이 1,000건 미만이면 옵티마이저 선택이 무의미하므로 검증을 건너뛴다(빈 DB 오탐 방지).

### CI 회귀 테스트 (`BoardListQueryPlanMaintainerTest`)

**500명 / 2,500건이면 나쁜 계획이 재현된다**(실측 — 이보다 큰 데이터는 CI 시간만 늘린다).

테스트는 2단계다:

1. 히스토그램을 지우고 → **계획에 filesort 가 실제로 나타나는지 먼저 확인**
2. `Maintainer.refresh()` 실행 → filesort 가 사라졌는지 확인

**1번이 핵심이다.** 없으면, 테스트 데이터가 부족해 애초에 나쁜 계획이 안 나오는 경우에도 2번이 그냥 통과해
**아무것도 검증하지 못한 채 초록불**이 켜진다.

---

## 2. ✅ 공간 인덱스는 제대로 작동한다

`SpringDataJpaLocationServiceRepository` 의 반경 검색은 **`ST_Within(경계 폴리곤)` 으로
공간 인덱스를 태우고, 그 뒤 `ST_Distance_Sphere` 로 정밀 필터링**한다. 올바른 설계다.

| 쿼리 형태 | 실행 계획 | 검사 행 | 시간 |
|---|---|---|---|
| **앱의 실제 쿼리** (`ST_Within` + `ST_Distance_Sphere`) | `range` / `idx_locationservice_location_spatial` | **215** | 0.03s |
| `ST_Distance_Sphere` 단독 | `ALL` / 인덱스 없음 | **22,737** | 0.08s |

**검사 행 수가 100배 차이난다.** 시간 차이(0.03s vs 0.08s)보다 이 숫자가 더 중요하다 —
데이터가 커지면 시간 차이는 그대로 벌어지지만, 검사 행 수의 비율은 유지된다.

> ⚠️ 공간 인덱스는 `ST_Distance_Sphere(...) <= r` 같은 **거리 조건으로는 탈 수 없다.**
> 반드시 `ST_Within` / `MBRContains` 같은 **경계상자 조건**이어야 한다.
> 주변 검색 쿼리를 새로 짤 때 이 패턴을 깨뜨리지 말 것.

---

## 3. ✅ 풀텍스트 인덱스도 작동한다

| | 시간 |
|---|---|
| `MATCH(title, content) AGAINST('산책' IN BOOLEAN MODE)` | **0.00s** |
| `title LIKE '%산책%' OR content LIKE '%산책%'` | 0.05s |

---

## 4. 🟡 carerequest 주변 검색에는 인덱스가 없다

`meetup` 과 `locationservice` 에는 공간 인덱스가 있지만, **`carerequest` 에는 `latitude`/`longitude`
컬럼만 있고 공간 인덱스도 `(latitude, longitude)` 복합 인덱스도 없다.**

```
carerequest 주변검색 EXPLAIN → type: ALL   key: NULL   rows: 3,055
```

현재 3,000행이라 0.00s 로 빠르지만 **풀스캔이다.** 케어 요청이 쌓이면 선형으로 느려진다.
`meetup` 이 이미 `geo_point` + `SPATIAL` 인덱스 + 트리거로 해결한 것과 같은 방식을 적용할 수 있다.

---

## 5. 측정하지 못한 것

- **`file.idx_file_target`** — 시드 스크립트가 `file` 테이블에 데이터를 만들지 않아 비어 있다.
  2026-07-12 에 추가한 이 인덱스의 효과는 아직 실측되지 않았다.
- **딥 페이징(OFFSET) 자체의 비용** — board 5만 행에서는 아직 문제가 드러나지 않는다.
  §1 의 문제는 딥 페이징이 아니라 **매 페이지의 전체 정렬**이다. (당초 "5만이면 딥 페이징
  문제가 보인다"고 예상했으나 **측정 결과 틀렸다.** 더 큰 규모가 필요하다.)

---

## 재현 방법

```bash
# 1. 시드 (board 50,000 / comment 150,000 / locationservice 22,905)
mysql -h127.0.0.1 -P3306 -uroot -p petory < scripts/seed/seed-dev-data.sql

# 2. 정합성 확인 (25개 항목 전부 0)
mysql -h127.0.0.1 -P3306 -uroot -p -t petory < scripts/seed/verify-data-integrity.sql

# 3. 측정 — SQL_NO_CACHE 를 반드시 붙이고, EXPLAIN 의 type/key/rows/Extra 를 함께 기록할 것
```
