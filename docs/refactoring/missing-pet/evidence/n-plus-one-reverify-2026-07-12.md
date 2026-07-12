---
date: 2026-07-12
domains: [missingpet, file]
type: performance-evidence
problem: n-plus-one
status: verified
metric: "worktree 실측(실제 커밋 코드): 267→4 queries (-98.5%), 762ms→88ms. 재구성 테스트: 201→4 (-98%), 428ms→38ms"
before_commit: 9c7e0d68
after_commit: 9dbf85ba
related: [docs/troubleshooting/missing-pet/performance-measurement-results.md]
---

# MissingPet 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)

> 목적: `troubleshooting/missing-pet/performance-measurement-results.md`(207→3쿼리, 571ms→79ms)를 현재 코드로 다시 실행해 재현성을 확인한다. 기존에 자동화 테스트가 없어 신규 통합테스트를 작성했고, 실제 프로덕션 코드 경로(`MissingPetBoardService.getBoardsWithPaging()`)를 직접 호출해 측정했다.

## 0. 방법론

- 실제 해결 커밋: [`9dbf85ba`](https://github.com/makkong1/Petory/commit/9dbf85ba) (2025-12-31, `실종 도메인 n+1 트러블슈팅 해결`). 직전 커밋 [`9c7e0d68`](https://github.com/makkong1/Petory/commit/9c7e0d68)에 `attachmentFileService.getAttachments()` 단건 호출이 실제로 있음을 확인.
- 신규 테스트: `backend/test/.../board/service/MissingPetNPlusOneReverifyTest.java`
- Fixture: 작성자 1명, 게시글 100개, 게시글당 댓글 3개, 게시글당 첨부파일 1개
- Before: JOIN FETCH 없는 EntityManager 쿼리로 게시글 조회 → `missingPetCommentService.getCommentCount()`(단건)/`attachmentFileService.getAttachments()`(단건)를 게시글마다 호출 — 원 문서가 기술한 "게시글 1 + 댓글 N + 파일 N" 패턴을 재현
- After: 재현 코드가 아니라 **실제 서비스가 쓰는 코드 그대로** — `missingPetBoardService.getBoardsWithPaging(null, 0, 100)` 호출(내부적으로 댓글수 배치·파일 배치 조회)
- 환경: 로컬 MySQL 8(`petory`), `@Transactional` 롤백으로 실데이터(missing_pet_board 103건) 비영향
- **추가 검증(§1.5)**: `git worktree`로 `9c7e0d68`(before)를 실제 checkout해 그 시점 `MissingPetBoardService.getBoards()`(dev엔 이미 사라진 메서드 — 페이징 도입으로 `getBoardsWithPaging()`으로 대체됨)를 재구성 없이 직접 호출했다.

## 1. 통합테스트 실행 결과

| 항목 | Before | After | 원 문서(참고) |
|---|---|---|---|
| 쿼리 수 | 201개 | 4개 (**-98.0%**) | 207개 → 3개 |
| 실행 시간 | 428ms | 38ms (**-91.1%**) | 571ms → 79ms |

201개 구성: 메인 쿼리 1개 + 댓글수 개별조회 100개 + File 개별조회 100개 = 201개. 원 문서의 207개(게시글1+댓글103+파일103)와 정확히 같은 패턴이다(이번 fixture는 103개가 아니라 100개라 수치가 조금 다를 뿐). After 4개는 메인쿼리(게시글+작성자 JOIN, count 쿼리 포함 2개) + 파일 배치(IN절) 1개 + 댓글수 배치(IN절+GROUP BY) 1개.

## 1.5. worktree 검증 — 실제 그 커밋의 코드는 어땠나

`git worktree`로 `9c7e0d68`(before)를 실제 checkout해서 `MissingPetBoardService.getBoards(status)`를 재구성 없이 그대로 호출했다(fixture 30개 + 실데이터 103건 = 133건 전체조회). dev(after)에는 이 메서드 자체가 없어져서(페이징 도입으로 `getBoardsWithPaging()`으로 대체) 그 메서드를 그대로 호출해 비교했다. Care·Chat에서 Hibernate Statistics API가 신뢰할 수 없음을 확인했으므로 실제 SQL 로그 카운트를 기준으로 삼았다.

| | Before(`9c7e0d68`, 실제 커밋 코드) | After(dev, 실제 프로덕션 코드) |
|---|---|---|
| **실제 SQL 카운트** | **267개**(메인1 + 댓글 개별 133 + File 개별 133) | **4개**(무관한 meetup 쿼리 1개 별도 — 메인 count/본조회 2 + File 배치 1 + 댓글수 배치 1) |
| Statistics API 값(참고, 과소보고) | 134 | 3 |
| 실행 시간 | 762ms | 88ms |
| 결과 수 | 133건(fixture 30 + 실데이터 103) | 133건 이상(동일 조건) |

267→4(**-98.5%**)로, §1의 재구성 테스트(201→4, -98.0%)와 감소율이 거의 일치한다 — 이번엔 재구성이 실제 역사를 정확히 반영했다. Statistics API는 여기서도 실제값의 절반 수준(134/267, 3/4)만 보고해 Care·Chat과 같은 함정이 재확인됐다.

## 2. EXPLAIN — 댓글수 개별조회 vs 배치조회

### 인덱스 상황

```
missing_pet_comment:
  PRIMARY(idx)
  INDEX(board_idx)                    ← FK
  INDEX(board_idx, is_deleted)        ← 커버링 인덱스, 개별/배치 조회 둘 다 사용
```
Board/Chat과 마찬가지로 필요한 인덱스는 이미 있다.

### Before — 댓글수 개별조회 (100번 반복됨)

```sql
EXPLAIN ANALYZE SELECT COUNT(idx) FROM missing_pet_comment WHERE board_idx=10 AND is_deleted=0;
```

```
-> Aggregate: count(idx)  (cost=0.283 rows=1) (actual time=0.0343..0.0344 rows=1 loops=1)
    -> Filter: (is_deleted = 0)  (cost=0.26 rows=0.1) (actual time=0.0326..0.0326 rows=0 loops=1)
        -> Index lookup on missing_pet_comment (board_idx = 10)  (cost=0.26 rows=1) (actual time=0.0285..0.0306 rows=1 loops=1)
```

인덱스 lookup, `actual time` 0.034ms — 빠르지만 100번 반복.

### After — 댓글수 배치조회 (IN절 + GROUP BY, 1회)

```sql
EXPLAIN ANALYZE
SELECT board_idx, COUNT(idx) FROM missing_pet_comment
WHERE board_idx IN (100개) AND is_deleted=0 GROUP BY board_idx;
```

```
-> Group aggregate: count(idx)  (cost=50.3 rows=102) (actual time=0.0576..0.239 rows=5 loops=1)
    -> Filter: (is_deleted=0 and board_idx in (...))  (cost=23.5 rows=116) (actual time=0.0427..0.219 rows=115 loops=1)
        -> Covering index range scan on idx_missing_pet_comment_board_is_deleted
           over (board_idx=10 AND is_deleted=0) OR ... (4 more)  (cost=23.5 rows=116) (actual time=0.0396..0.178 rows=115 loops=1)
```

`(board_idx, is_deleted)` **커버링 인덱스**만으로 range scan, `actual time` 0.24ms 단 1회.

### 해석

Board·Chat과 같은 패턴 — 인덱스는 이미 있어서 개별조회 1건 자체는 빠르다(0.034ms). 문제는 100번 반복하는 왕복 비용이지, 실행계획 자체의 결함이 아니다. `board_idx IN (100개)`로 한 번에 묶으면 커버링 인덱스 range scan 1회로 끝난다.

## 3. File 조회는 Care와 동일한 인덱스 부재 이슈를 공유한다

MissingPet의 첨부파일도 `AttachmentFileService.getAttachments()`(단건)를 쓰므로, `file` 테이블에 `(target_type, target_idx)` 인덱스가 없다는 문제를 **Care N+1 재검증과 동일하게** 그대로 물려받는다(`docs/refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md` §3 참고 — 매번 240행 풀스캔). 상세 EXPLAIN은 중복 게재하지 않고 그 문서를 근거로 삼는다.

## 4. 재현 방법

```bash
./gradlew test --tests "com.linkup.Petory.domain.board.service.MissingPetNPlusOneReverifyTest" --rerun --info

# EXPLAIN
mysql -uroot -p petory <<'SQL'
SET @one = (SELECT board_idx FROM missing_pet_comment LIMIT 1);
EXPLAIN ANALYZE SELECT COUNT(idx) FROM missing_pet_comment WHERE board_idx=@one AND is_deleted=0;
-- 배치는 100개 board_idx를 IN절로 묶어 GROUP BY
SQL
```

## 5. 관련 문서

- 원본(207→3쿼리 최초 측정): [`troubleshooting/missing-pet/performance-measurement-results.md`](../../../troubleshooting/missing-pet/performance-measurement-results.md)
- File 인덱스 부재 상세: [`refactoring/care/evidence/n-plus-one-reverify-2026-07-12.md`](../../care/evidence/n-plus-one-reverify-2026-07-12.md)
