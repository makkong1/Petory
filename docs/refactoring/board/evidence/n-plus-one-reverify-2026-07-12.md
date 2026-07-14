---
date: 2026-07-12
domains: [board]
type: performance-evidence
problem: n-plus-one
status: verified
metric: "301→3 queries (-99%), 561ms→55ms (10.2x), 21MB→3MB"
before_commit: 3a7a581d
after_commit: 19b7c120
related: [docs/troubleshooting/board/performance-optimization.md]
---

# Board 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)

> 목적: `troubleshooting/board/performance-optimization.md`(301→3쿼리, 745ms→30ms)의 수치를 **다시 실행해 재현성을 확인**하고, 개별조회/배치조회 각 쿼리의 **실제 실행계획(EXPLAIN)**을 추가로 남긴다. 추정치가 아니라 이 저장소에서 실행한 결과다.

## 0. 방법론

- 실제 해결 커밋: [`19b7c120`](https://github.com/makkong1/Petory/commit/19b7c120) (2025-11-19, `게시글 목록 조회 n+1 문제해결`). 직전 커밋은 [`3a7a581d`](https://github.com/makkong1/Petory/commit/3a7a581d).
- 재현 대상: `backend/test/java/.../BoardPerformanceComparisonTest.java` (기존 테스트, 신규 작성 아님)
- 실행: `./gradlew test --tests BoardPerformanceComparisonTest --rerun` — Hibernate Statistics로 쿼리 수·실행시간·메모리 측정
- 환경: 로컬 MySQL 8(`petory`, `ddl-auto=none`), `@Transactional` 롤백이라 실데이터(board 10,264건) 비영향
- EXPLAIN 대상: 테스트가 재현하는 두 쿼리 패턴(개별 COUNT 반복 vs IN절 배치)을 **실제 로컬 DB의 실제 board_idx 값**으로 별도 실행
- **추가 검증(§1.5)**: §1의 결과는 테스트 헬퍼로 옛 패턴을 재구성한 것이었다. 이 재구성이 실제 역사와 같은지 `git worktree`로 직접 확인했다 — `3a7a581d`(before)를 실제로 checkout해서 그 커밋의 `BoardService.getAllBoards()`를 **재구성 없이 그대로 호출**하고, 같은 fixture로 dev(after) 코드도 동일하게 호출해 비교했다.

## 1. 통합테스트 재실행 결과 (재현성 확인, 헬퍼로 재구성한 코드 기준)

### 테스트 3 — 전체 시나리오 (작성자 LAZY + 반응 개별 + 첨부파일 개별 → 배치 전환)

| 항목 | Before | After | 원 문서(참고) |
|---|---|---|---|
| 쿼리 수 | 301개 | 3개 (**-99.0%**) | 301→3 (동일) |
| 실행 시간 | 561ms | 55ms (**10.2배**) | 745→30ms (24.8배) |
| 메모리 | 21.00MB | 3.00MB | 22.5→2MB |

### 테스트 1 — 반응 카운트만 (작성자는 Fetch Join 고정, 반응만 개별 vs 배치)

| 항목 | Before | After |
|---|---|---|
| 쿼리 수 | 201개 | 2개 (**-99.0%**) |
| 실행 시간 | 221ms | 27ms (8.2배) |
| 메모리 | 9.99MB | 517KB |

### 테스트 2 — Fetch Join vs LAZY (한계 재확인)

`entityManager.clear()` + 2차 캐시 evict를 해도 같은 `@Transactional` 트랜잭션 내에서는 Hibernate가 이미 로드한 `Users`를 재사용해 LAZY N+1이 실제로 재현되지 않는다(쿼리 1→1, 0% 감소). 이건 이 테스트 환경의 알려진 한계이며, 옛 문서에도 동일하게 기록돼 있다. 프로덕션에서는 요청마다 새 영속성 컨텍스트이므로 이 캐싱 효과가 없다.

**해석**: 절대 시간(561ms vs 옛 문서 745ms)은 실행할 때마다 달라진다(로컬 머신 부하, JIT, 커넥션풀 워밍업 등) — 이건 예상된 변동이다. 재현성의 핵심은 **패턴이 고정된다는 것**: 쿼리 수는 정확히 301→3, 201→2로 100% 재현됐고, 시간은 매번 8~10배 이상 개선된다.

## 1.5. worktree 검증 — 재구성이 아니라 실제 그 커밋의 코드를 실행

§1은 테스트 헬퍼로 옛 패턴을 손으로 옮겨 적은 것이다. 이게 실제 역사와 같은지, `git worktree`로 완전히 격리된 공간을 만들어 확인했다.

**방법**:
1. `git worktree add`로 별도 디렉토리 생성 후 `git checkout 3a7a581d`(before, detached HEAD)
2. 이 시점엔 `Users` 엔티티에 `nickname` 필드가 없고, `kakao.rest-api-key` 설정이 필요했다(지금은 `naver` 지도로 전환돼 있음) — **8개월치 스키마·설정 드리프트가 실제로 존재함을 그 자리에서 확인**했다. fixture를 이 시점 엔티티 구조에 맞게 조정하고, 더미 설정을 임시로 추가해 컨텍스트 로딩만 통과시켰다(비즈니스 로직 변경 없음)
3. 재구성 없이 **이 커밋에 실제로 존재하는 `BoardService.getAllBoards(category)`를 직접 호출**하는 테스트 1개만 새로 작성 (fixture 100개, 카테고리로 격리)
4. 같은 브랜치에서 `git checkout <dev 최신>`으로 되돌아와 **동일 테스트를 재실행** (재구성이 아니라 마찬가지로 `getAllBoards()`를 그대로 호출 — 다만 dev의 최신 구현은 내부적으로 배치 조회를 쓴다)

**결과**:

| | Before(`3a7a581d`, 실제 커밋 코드) | After(dev, 실제 프로덕션 코드) |
|---|---|---|
| 쿼리 수 | **301개** | **3개** |
| 실행 시간 | 787ms | 38ms |
| 메모리 | 3.1MB | (거의 0, GC 영향으로 음수 측정) |
| 결과 수 | 100건 | 100건 |

쿼리 수(301→3)가 §1의 재구성 테스트와 **정확히 일치**한다 — 재구성이 실제 역사를 정확히 반영했음이 이제 이중으로 확인됐다(소스 대조 + 실행 결과 대조). 시간(787ms)은 §1(561ms)보다 느린데, 워크트리가 이번 세션에서 처음 기동돼 JIT/커넥션풀 워밍업이 없었기 때문으로 보인다 — 시간의 절대값보다 **쿼리 수 301→3, 개선 배율(약 20배)**이 핵심 증거다.

## 2. EXPLAIN — 개별조회 vs 배치조회의 실행계획

두 쿼리 모두 같은 인덱스(`board_reaction(board_idx, user_idx)` UNIQUE)를 사용한다는 게 핵심이다. **인덱스가 없어서 느린 게 아니라, 왕복 횟수가 문제라는 것**을 EXPLAIN으로 직접 확인했다.

### 인덱스 상황

```
board_reaction:
  PRIMARY(idx)
  UNIQUE(board_idx, user_idx)   ← 이 쿼리들이 타는 인덱스
  INDEX(user_idx)               ← FK
```
`reaction_type`은 별도 인덱스에 없다 (UNIQUE의 두 번째 컬럼이 아니라 board_idx 단일 레인지 스캔 후 필터).

### Before — 개별조회 1건 (이게 200번 반복됨)

```sql
EXPLAIN ANALYZE
SELECT COUNT(idx) FROM board_reaction WHERE board_idx = ? AND reaction_type = 'LIKE';
```

```
-> Aggregate: count(board_reaction.idx)  (cost=0.415 rows=1) (actual time=0.0469..0.0469 rows=1 loops=1)
    -> Filter: (board_reaction.reaction_type = 'LIKE')  (cost=0.3 rows=0.5) (actual time=0.0437..0.0449 rows=1 loops=1)
        -> Index lookup on board_reaction using UK(board_idx,user_idx) (board_idx = ?)  (cost=0.3 rows=1) (actual time=0.0412..0.0423 rows=1 loops=1)
```

인덱스 lookup, `actual time` **0.047ms**. 쿼리 자체는 빠르다. 문제는 이게 `likes`+`dislikes` 조합으로 게시글 100개 × 2 = **200번 반복 실행**된다는 것 — DB 실행시간 자체(0.047ms×200 ≈ 9.4ms)보다, 매 호출마다 발생하는 JDBC 왕복·PreparedStatement 준비·Hibernate 세션 오버헤드가 누적돼 221ms까지 벌어진다.

### After — 배치조회 1건 (100개 board_idx를 IN절로, 1회만 실행)

```sql
EXPLAIN ANALYZE
SELECT board_idx, reaction_type, COUNT(idx)
FROM board_reaction
WHERE board_idx IN (27,28,...,155)  -- 100개
GROUP BY board_idx, reaction_type;
```

```
-> Table scan on <temporary>  (actual time=2.83..2.85 rows=53 loops=1)
    -> Aggregate using temporary table  (actual time=2.83..2.83 rows=53 loops=1)
        -> Index range scan on board_reaction using UK(board_idx,user_idx)
           over (board_idx = 27) OR (board_idx = 28) OR (98 more)
           (cost=75.4 rows=112) (actual time=0.038..2.64 rows=65 loops=1)
```

100개 board_idx에 대한 인덱스 range scan + GROUP BY용 임시테이블, `actual time` **2.85ms 단 1회**.

### 해석

| | 실행 횟수 | 1회당 실행시간 | 순수 DB 실행시간 총합 |
|---|---|---|---|
| Before(개별) | 200회 | 0.047ms | ≈9.4ms |
| After(배치) | 1회 | 2.85ms | 2.85ms |

DB 실행시간만 비교하면 격차가 3~4배 수준이지만, 실측 애플리케이션 레벨 시간(221ms→27ms, 8.2배)이 훨씬 크게 벌어지는 이유는 **DB 실행시간이 아니라 JDBC 왕복 200회의 오버헤드**라는 게 EXPLAIN 비교로 드러난다. 이게 N+1이 "느린 쿼리" 문제가 아니라 "쿼리 개수" 문제라고 말하는 근거다.

## 3. 재현 방법

```bash
# 통합테스트
./gradlew test --tests "com.linkup.Petory.domain.board.service.BoardPerformanceComparisonTest" --rerun --info

# EXPLAIN (로컬 petory DB, 실제 board_idx 100개 샘플)
mysql -uroot -p petory <<'SQL'
SET @ids = (SELECT GROUP_CONCAT(idx) FROM (SELECT idx FROM board ORDER BY idx LIMIT 100) t);
SELECT idx INTO @one FROM board LIMIT 1;
EXPLAIN ANALYZE SELECT COUNT(idx) FROM board_reaction WHERE board_idx = @one AND reaction_type = 'LIKE';
-- 배치는 GROUP_CONCAT 결과를 문자열로 IN절에 붙여 PREPARE/EXECUTE로 실행 (동적 IN절이라 바인드 불가)
SQL
```

## 4. 관련 문서

- 원본(추정 기반 최초 측정): [`troubleshooting/board/performance-optimization.md`](../../../troubleshooting/board/performance-optimization.md)
- 대표 사례 선정: [`portfolio-refactoring-troubleshooting-selection.md`](../../portfolio-refactoring-troubleshooting-selection.md)
