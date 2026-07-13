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

### 원인

`users` 에 `(is_deleted, status)` 인덱스가 없다. 옵티마이저는 `users` 필터를 적용하려면
풀스캔이 필요하다고 보고, 그 비용을 근거로 `users` 를 드라이빙 테이블로 골랐다.

### 아직 고치지 않았다

JPQL 은 `STRAIGHT_JOIN` 을 지원하지 않으므로 단순 힌트 추가로는 안 된다. 선택지:

1. `users(is_deleted, status)` 인덱스 추가 → 풀스캔은 없어지지만 **filesort 는 남는다**
2. 네이티브 쿼리로 조인 순서 고정 (`STRAIGHT_JOIN`)
3. 쿼리 재구성 — 단, `u.status` 필터가 `LIMIT` **이전에** 적용되어야 하므로
   단순 deferred join 으로는 의미가 바뀐다

**어느 쪽이든 이 문서의 수치가 전후 비교 기준점이다.**

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
