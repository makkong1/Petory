# BoardPopularityService 배치 집계 리팩토링

## 개요

[board-popularity-snapshot-batch-analysis.md](./board-popularity-snapshot-batch-analysis.md) 분석 문서의 개선 방향을 반영한 리팩토링 결과입니다.

---

## 적용된 개선 사항

### 1. 병렬 실행 (우선순위 1)

**변경 전**: 3개 배치 메서드 순차 실행
```java
Map<Long, Integer> likeCountsMap = getLikeCountsBatch(boardIds);
Map<Long, Integer> commentCountsMap = getCommentCountsBatch(boardIds);
Map<Long, Integer> viewCountsMap = getViewCountsBatch(boardIds);
```

**변경 후**: `CompletableFuture`로 병렬 실행
```java
Map<Long, BoardCounts> countsMap = fetchBoardCountsInParallel(boardIds);
```

- `getLikeCountsBatch`, `getCommentCountsBatch`, `getViewCountsBatch`를 `CompletableFuture.supplyAsync()`로 동시 호출
- `CompletableFuture.allOf()`로 결과 통합

### 2. LIKE 전용 쿼리 (우선순위 2)

**변경 전**: `countByBoardsGroupByReactionType` — LIKE/DISLIKE 전체 반환 후 Java에서 필터
```java
if (reactionType == ReactionType.LIKE) {
    countsMap.put(boardId, count.intValue());
}
```

**변경 후**: `countByBoardsAndReactionType(batch, ReactionType.LIKE)` — DB에서 LIKE만 조회

- **추가된 메서드**:
  - `BoardReactionRepository.countByBoardsAndReactionType(List<Long>, ReactionType)`
  - JPQL: `WHERE br.board.idx IN :boardIds AND br.reactionType = :reactionType`
- **효과**: 불필요한 데이터 전송 감소, 코드 단순화

### 3. 통합 DTO (우선순위 3)

**변경 전**: Map 3개 + `getOrDefault` 3번
```java
int likes = likeCountsMap.getOrDefault(board.getIdx(), 0);
int comments = commentCountsMap.getOrDefault(board.getIdx(), 0);
int views = viewCountsMap.getOrDefault(board.getIdx(), 0);
```

**변경 후**: `BoardCounts` record 하나로 통합
```java
record BoardCounts(int likes, int comments, int views) {
    static final BoardCounts ZERO = new BoardCounts(0, 0, 0);
}
// Map<Long, BoardCounts> countsMap
BoardCounts counts = countsMap.getOrDefault(board.getIdx(), BoardCounts.ZERO);
```

- **효과**: 가독성·유지보수 향상

---

## 변경된 파일

| 파일 | 변경 내용 |
|------|----------|
| `BoardReactionRepository.java` | `countByBoardsAndReactionType` 메서드 추가 |
| `SpringDataJpaBoardReactionRepository.java` | LIKE 전용 JPQL 쿼리 추가 |
| `JpaBoardReactionAdapter.java` | 새 메서드 구현 |
| `BoardPopularityService.java` | 병렬 실행, BoardCounts, getLikeCountsBatch 수정 |

---

## 참고

- 기존 `countByBoardsGroupByReactionType`는 `BoardService` 등에서 그대로 사용
- `BoardPopularityService`만 새 메서드 `countByBoardsAndReactionType` 사용
