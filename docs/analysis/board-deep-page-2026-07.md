---
date: 2026-07-15
domains: [board]
type: query-fix-evidence
problem: board-deep-page-lazy-join
status: verified
metric: "깊은 페이지(OFFSET 49980) 커버링 인덱스 스캔 24~32ms(비교군 66~84ms, 구코드 재현 133ms) · COUNT 단일 테이블 7~25ms(구코드 재현 22~32ms) · 너덜너덜 증명: 전체 2,500페이지 중 596페이지(23.8%)에 숨김 대상 글 유입 · k6 30s/20VU 15,555req 100% 200 · p95 63.91ms"
related: [docs/analysis/query-audit/fixes-2026-07-14.md, docs/superpowers/specs/2026-07-15-board-deep-page-pagination-design.md, docs/superpowers/plans/2026-07-15-board-deep-page-pagination.md]
---
# board 깊은 페이지 — 2단계 지연 조인 + author_visible 비정규화, 전후 실측

> Task 4(`perf(board): 목록을 2단계 지연 조인으로 + COUNT 단일 테이블`, `2f824f27`)가 적용한
> `author_visible` 비정규화 + 커버링 인덱스 + 단일 테이블 COUNT를 **실측**했다.
> DB: 로컬 `petory` (board 50,000행, 그중 `author_visible=0` 2,000행 — 밴/탈퇴 회원 글).
> 스케줄러는 껐다(`--petory.scheduling.enabled=false`), 포트 8081 (도커 8080 안 건드림).

---

## 0. 한눈에 보기

| # | 측정 | 수정 전(재현) | 수정 후 | 효과 |
|---|---|---|---|---|
| ① | 깊은 페이지 스캔 (OFFSET 49980) | 인덱스 무시 시 **66~84ms** · 비커버링(행 조회 필요) | **24~32ms** · 커버링 인덱스 단독 | **~2.5배** |
| ① | 〃 (구코드 형태: board JOIN users) | **133ms** · Nested loop 50,000회 PK 단건조회 | 〃 | **~4~5배** |
| ② | COUNT | users 조인: **22~32ms** · 2테이블(users 10,001행 스캔 + board 46,000행) | 단일 테이블: **7~25ms** · board 48,000행만 | 조인 제거, 검사 대상 테이블 1개 |
| ③ | 너덜너덜 증명 | `is_deleted`만으로 skip 시 — | 전체 2,500페이지 중 **596페이지(23.8%)**에 숨김 대상 글 유입(합 2,000건) | 비정규화 없인 페이지가 그만큼 샌다 |
| ④ | k6 (30s, 20VU, 페이지 0/1000/2000/2499 혼합) | — | **15,555 요청**, 100% 200, **p95 63.91ms**, avg 38.51ms | 페이지 깊이 무관하게 평탄 |

**변경 파일(Task 4 기준):** Flyway `V6__board_author_visible.sql` + `Board` 엔티티 + Repository/Service(2단계 지연 조인) + 회귀 테스트.
이 문서는 그 위에 **측정만** 추가한다(코드 변경 없음).

---

## 1. ① 깊은 페이지 스캔 A/B — `EXPLAIN ANALYZE`

### 수정 후 — 커버링 인덱스

```sql
SELECT idx FROM board
WHERE is_deleted=0 AND author_visible=1
ORDER BY created_at DESC LIMIT 20 OFFSET 49980;
```

```
-> Limit/Offset: 20/49980 row(s)  (actual time=24.1..31.8 rows=0 loops=1)
    -> Covering index lookup on board using idx_board_visible_created
       (is_deleted=0, author_visible=1)  (actual time=0.08..28.9 rows=48000 loops=1)
```

`idx_board_visible_created (is_deleted, author_visible, created_at DESC)`가 **커버링**(SELECT 컬럼이 `idx`뿐이라 인덱스만으로 해결, 행 데이터 조회 없음)으로 잡혔다. 두 번 반복 측정 **24.1ms / 31.8ms**.

> `is_deleted=0 AND author_visible=1`을 만족하는 행은 48,000개뿐인데 `OFFSET 49,980`은 그 범위를 넘는다. 그래서 최종 `rows=0`(빈 페이지)이 정상이다 — 이 쿼리는 브리프가 지정한 그대로, **인덱스 전체를 끝까지 훑어야 하는 최악 근접 케이스**를 재는 것이다.

### A/B — 인덱스를 강제로 무시(`IGNORE INDEX`)

```
-> Limit/Offset: 20/49980 row(s)  (actual time=66.1..84.4 rows=0 loops=1)
    -> Filter: (board.author_visible = 1)  (actual time=0.42..83.2 rows=48000 loops=1)
        -> Index lookup on board using idx_board_deleted_created (is_deleted=0)
           (actual time=0.42..81.1 rows=50000 loops=1)
```

`idx_board_deleted_created(is_deleted, created_at)`에는 `author_visible`이 없어 **비커버링**이다. 인덱스로 50,000행을 훑은 뒤 `author_visible` 필터링을 위해 행 데이터를 다시 봐야 한다. **66.1ms / 84.4ms** — 수정 후 대비 **~2.5배**.

### 참고 — 구코드 형태 재현(`board JOIN users`)

Task 4 이전 코드는 `users`를 즉시 조인해서 필터링했다. 그 형태를 그대로 재현:

```sql
SELECT b.idx FROM board b JOIN users u ON u.idx=b.user_idx
WHERE b.is_deleted=0 AND u.is_deleted=0 AND u.status='ACTIVE'
ORDER BY b.created_at DESC LIMIT 20 OFFSET 49980;
```

```
-> Limit/Offset: 20/49980 row(s)  (actual time=133..133 rows=0 loops=1)
    -> Nested loop inner join  (actual time=0.42..132 rows=46000 loops=1)
        -> Index lookup on b using idx_board_deleted_created (is_deleted=0)  (rows=50000)
        -> Single-row index lookup on u using PRIMARY (idx=b.user_idx)  (rows≈1, loops=50000)
```

**133ms** — board 인덱스를 훑는 매 행마다 `users` PK 단건 조회가 50,000번 딸려 붙는다. 기존 기록("구코드 깊은 페이지 147ms")과 같은 자릿수로 재현됐다.

### OFFSET 비용 곡선 (수정 전, 실측)

바로 위와 같은 "구코드 형태" 쿼리를 오프셋 여러 지점에서 실측해 O(offset) 곡선을 직접 확인했다. 목록 화면에 필요한 컬럼(`title`, `username`, `location`)을 실제로 select해 위 133ms 측정(`b.idx`만 select)보다 컬럼이 많다 — 그만큼 행 조회 비용이 더 들어가 있다:

```sql
SELECT b.idx, b.title, u.username, u.location
FROM board b JOIN users u ON u.idx=b.user_idx
WHERE b.is_deleted=0 AND u.is_deleted=0 AND u.status='ACTIVE'
ORDER BY b.created_at DESC LIMIT 20 OFFSET <OFF>;
```

| OFFSET | 실행시간 (`actual time` 종료값, 2회 측정) |
|---|---|
| 0 (1페이지) | 0.4ms / 0.9ms |
| 10,000 (500페이지) | 63.3ms / 65.3ms |
| 25,000 (1,250페이지) | 71.6ms / 74.8ms |
| 40,000 (2,000페이지) | 92.8ms / 94.0ms |
| 49,980 (맨 뒤, 2,500페이지) | 107ms / 110ms |

오프셋이 커질수록 `Nested loop inner join`이 훑어야 하는 board 행 수(offset+20)와 그에 딸린 `users` PK 단건 조회 횟수가 함께 늘어 선형으로 증가한다 — §0/§1의 O(offset) 주장을 이 곡선이 직접 뒷받침한다. 로컬 단일 실행 환경이라 버퍼풀 상태에 따라 흔들리므로(§6 참고) 2회씩 기록했다.

---

## 2. ② COUNT A/B

### 수정 후 — 단일 테이블

```sql
SELECT COUNT(*) FROM board WHERE is_deleted=0 AND author_visible=1;
```

```
-> Aggregate: count(0)  (actual time=7.47..25.2 rows=1 loops=1)
    -> Covering index lookup on board using idx_board_visible_created
       (is_deleted=0, author_visible=1)  (actual time=0.02..22.2 rows=48000 loops=1)
```

**7.47ms / 25.2ms**(2회 측정, 버퍼풀 상태에 따라 변동). `board` 한 테이블, 커버링 인덱스로 48,000행만 훑는다.

### 수정 전 재현 — `users` 조인

```sql
SELECT COUNT(*) FROM board b JOIN users u ON u.idx=b.user_idx
WHERE b.is_deleted=0 AND u.is_deleted=0 AND u.status='ACTIVE';
```

```
-> Aggregate: count(0)  (actual time=21.9..32.2 rows=1 loops=1)
    -> Nested loop inner join  (actual time=0.03..30.8 rows=46000 loops=1)
        -> Filter: (u.status='ACTIVE' AND u.is_deleted=0)  (actual time=0.02..5.07 rows=9201 loops=1)
            -> Table scan on u  (actual time=0.02..3.63 rows=10001 loops=1)
        -> Covering index lookup on b using idx_board_user_deleted_created
           (user_idx=u.idx, is_deleted=0)  (rows≈5, loops=9201)
```

**21.9ms / 32.2ms**. `users` 10,001행 풀스캔 + `board` 인덱스 조회 9,201회(사용자별). **테이블 2개**를 오가야 한다.

### 반환값 차이 — 성능만이 아니라 의미도 바뀌었다

| | 반환값 | 왜 다른가 |
|---|---|---|
| 수정 후(`author_visible=1`) | **48,000** | `author_visible` = "미탈퇴 AND status≠BANNED" |
| 수정 전(`u.status='ACTIVE'`) | **46,000** | `ACTIVE`만 허용 — `SUSPENDED`(정지, 400명) 회원 글도 숨겨버림 |

`users` 상태 분포: `ACTIVE`(정상) 9,201 / `ACTIVE`(탈퇴, `is_deleted=1`) 200 / `SUSPENDED` 400 / `BANNED` 200.
`V6__board_author_visible.sql`의 설계 의도대로 **정지(SUSPENDED)는 일시적이므로 글을 계속 노출**한다 — 구코드의 `status='ACTIVE'` 단일 조건은 정지 회원 글까지 실수로 가려버리던 **부작용**이었다. 이번 비정규화는 성능 개선이면서 동시에 그 부작용을 고친 것이다.

---

## 3. ③ 너덜너덜 증명 — 비정규화 없이 `is_deleted`만으로 skip하면

브리프 지정 쿼리(OFFSET 40000)와 다른 오프셋들로 표본을 떠봤다:

| OFFSET | 숨어야 할 글 유입 수 (20건 중) |
|---|---|
| 0 / 5,000 / 15,000~45,000 / 49,980 | 0 |
| 10,000 | 1 |
| **49,900** | **4** |

오프셋에 따라 0건인 곳도 있어서, **전체 2,500페이지**(50,000행 ÷ 20)를 윈도우 함수로 한 번에 검사했다:

```sql
WITH ranked AS (
  SELECT b.idx, b.user_idx,
         FLOOR((ROW_NUMBER() OVER (ORDER BY b.created_at DESC) - 1) / 20) AS page_no
  FROM board b WHERE b.is_deleted=0
),
flagged AS (
  SELECT r.page_no, (u.is_deleted=1 OR u.status='BANNED') AS should_hide
  FROM ranked r JOIN users u ON u.idx = r.user_idx
)
SELECT COUNT(DISTINCT page_no) total_pages,
       SUM(should_hide) total_hidden_rows,
       COUNT(DISTINCT CASE WHEN should_hide THEN page_no END) pages_with_leak,
       ROUND(100*COUNT(DISTINCT CASE WHEN should_hide THEN page_no END)/COUNT(DISTINCT page_no),1) pct_leak
FROM flagged;
```

```
total_pages: 2500   total_hidden_rows: 2000   pages_with_leak: 596   pct_leak: 23.8
```

**결과: 전체 2,500페이지 중 596페이지(23.8%)에 밴/탈퇴 회원 글이 하나 이상 섞인다.** (합계 2,000건이 흩어져 들어감 — 페이지당 평균 0.8건이지만 몰리는 곳은 한 페이지에 4건까지.)

> `is_deleted`만으로 board를 skip하고 화면단이나 애플리케이션 레벨에서 뒤늦게 작성자를 걸러내면(순진한 지연조인), 걸러낸 만큼 **그 페이지는 20건을 못 채우고 빈다.** 이번 표본에서는 **전체 페이지의 4분의 1 가까이**가 영향권이다. `author_visible` 비정규화로 DB가 처음부터 숨김 대상을 인덱스에서 제외하므로 이 문제 자체가 사라진다.

---

## 4. ④ k6 종단 측정

`scripts/k6/board-pagination.js` — 얕은(0)/중간(1000,2000)/맨뒤(2499) 페이지를 무작위로 섞어 20 VU, 30초.

```bash
T=$(curl -s -X POST http://localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"id":"seed_user_1","password":"Seed1234!"}' | python3 -c "import sys,json;print(json.load(sys.stdin).get('accessToken',''))")
k6 run -e TOKEN="$T" scripts/k6/board-pagination.js
```

```
checks_succeeded...: 100.00% 15555 out of 15555
✓ 200

http_req_duration: avg=38.51ms min=9.96ms med=36.27ms max=174.85ms p(90)=57.94ms p(95)=63.91ms
http_req_failed..: 0.00%
http_reqs........: 15555   518.04/s
```

**15,555건 전부 200, 실패 0건, p95 63.91ms.** 페이지가 0이든 2499(맨 뒤)든 같은 분포 안에서 섞여 응답했다 — 깊이에 따른 급격한 열화가 없다는 뜻이다(개별 페이지별 분리 측정은 하지 않았고, §1의 EXPLAIN이 그 인과를 이미 증명했으므로 여기서는 종단 처리량/지연만 확인했다).

---

## 5. 다른 도메인에도 같은 처방을 적용할까 — 판단

- **board만 실익이 있다.** 목록이 페이지 번호로 깊이 파고드는 소셜 피드형 UI이고, 규모(5만 행)에서 offset이 실제로 아프다.
- **missing_pet · meetup · care**: 현재 행 수 규모(수천~1만 대)에서는 깊은 offset 문제가 체감되지 않는다. 같은 패턴(작성자 가시성 비정규화)이 필요해지면 그때 재검토 — 지금은 보류.
- **관리자 목록**: 관리자는 "몇 번째 글, 전체 몇 건"이 업무 요구사항이라 오프셋 페이징과 정확한 총계(COUNT)를 유지해야 한다. 키셋 페이징 대상이 아니다.
- **chat**: 목록 페이징 자체가 해당 없음(대화방 단위).

---

## 6. 남은 것 (범위 밖 — 정직하게)

| 항목 | 왜 안 고쳤나 |
|---|---|
| `ORDER BY created_at DESC`에 동점(tie-break) 키 없음 | 같은 밀리초에 여러 글이 생성되면 페이지 경계에서 순서가 안정적이지 않을 수 있다(선재 이슈, Task 4 이전부터 존재). `idx` 등 2차 정렬 키 추가는 이번 범위 밖. |
| §1 측정치 노이즈(24~32ms, 66~84ms 등 범위로 기록) | 로컬 단일 실행 환경이라 버퍼풀 상태에 따라 흔들린다. 방향성(순서·배율)은 재현 시마다 일관됐다. |
| k6를 "수정 전" 코드에도 돌려보지 않음 | 구 코드는 이미 Task 4에서 대체됐다(별도 브랜치 롤백 없이는 재현 불가). 인과는 §1의 EXPLAIN A/B(인덱스 유무)로 이미 증명했으므로 종단 재현은 생략했다. |

---
