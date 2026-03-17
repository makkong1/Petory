# BoardPopularityService 배치 집계 로직 분석

## 문서 목적

`BoardPopularityService`의 인기 게시글 스냅샷 생성 시 사용하는 **배치 조회 메서드들**에 대해 다음을 정리합니다.

1. **메서드 흐름**: 전체 처리 과정
2. **자료구조**: 반환 타입과 상태 보장
3. **시간 복잡도**: O(1) vs O(n) 구분
4. **최적화 여지**: 개선 가능한 부분

---

## 1. 전체 흐름

### 1.1 진입점

```
getPopularBoards(periodType)
    → 스냅샷 조회 시도 (4단계 fallback)
    → 없으면 generateSnapshots() 호출
```

### 1.2 generateSnapshots() 흐름

```
generateSnapshots(periodType, range)
│
├─ 1. "자랑" 카테고리 게시글 조회 (기간 내)
│     boardRepository.findByCategoryAndCreatedAtBetween(...)
│
├─ 2. boardIds 추출
│     prideBoards → List<Long> boardIds
│
├─ 3. ★ 배치 조회 (실시간 집계) ★  ← 이 문서의 핵심
│     getLikeCountsBatch(boardIds)     → Map<Long, Integer>
│     getCommentCountsBatch(boardIds)  → Map<Long, Integer>
│     getViewCountsBatch(boardIds)     → Map<Long, Integer>
│
├─ 4. 인기도 점수 계산 및 정렬
│     prideBoards.stream().map(board → BoardScore).sorted().limit(30)
│
├─ 5. 기존 스냅샷 삭제
│
└─ 6. 새 스냅샷 생성 및 저장
```

### 1.3 배치 메서드 3개 호출 위치

```java
// BoardPopularityService.java:117-121
Map<Long, Integer> likeCountsMap = getLikeCountsBatch(boardIds);
Map<Long, Integer> commentCountsMap = getCommentCountsBatch(boardIds);
Map<Long, Integer> viewCountsMap = getViewCountsBatch(boardIds);
```

---

## 2. 배치 메서드 내부 흐름 (공통 패턴)

세 메서드 모두 **동일한 구조**를 가집니다.

```
getXxxCountsBatch(boardIds)
│
├─ 1. boardIds.isEmpty() → 빈 HashMap 반환
│
├─ 2. 바깥 for: 배치 단위 순회 (BATCH_SIZE = 1000)
│     for (i = 0; i < boardIds.size(); i += 1000)
│     │
│     ├─ batch = boardIds.subList(i, end)
│     │
│     ├─ DB 조회 (메서드별 상이)
│     │   • getLikeCountsBatch    → countByBoardsGroupByReactionType(batch)
│     │   • getCommentCountsBatch → countByBoardsAndIsDeletedFalse(batch)
│     │   • getViewCountsBatch    → countByBoards(batch)
│     │
│     └─ 안쪽 for: results 순회
│         Object[] → boardId, count (또는 reactionType) 파싱
│         countsMap.put(boardId, count)
│
└─ 3. putIfAbsent: 누락된 boardId는 0으로 초기화
    boardIds.forEach(id → countsMap.putIfAbsent(id, 0))
```

---

## 3. 자료구조 (Map<Long, Integer>)

### 3.1 반환 타입

| 항목 | 설명 |
|------|------|
| **Key** | `Long` — 게시글 ID (`board.idx`) |
| **Value** | `Integer` — 해당 게시글의 카운트 (좋아요/댓글/조회수) |

### 3.2 상태 보장

각 메서드 마지막에 다음 코드가 있습니다:

```java
boardIds.forEach(id -> countsMap.putIfAbsent(id, 0));
```

따라서:

- **모든 boardIds에 대해 키가 존재** (누락 없음)
- **카운트가 0인 게시글**도 `0`으로 명시적으로 포함

### 3.3 사용 방식

```java
// BoardPopularityService.java:124-126
int likes = likeCountsMap.getOrDefault(board.getIdx(), 0);
int comments = commentCountsMap.getOrDefault(board.getIdx(), 0);
int views = viewCountsMap.getOrDefault(board.getIdx(), 0);
```

`putIfAbsent`로 이미 모든 boardId가 맵에 있으므로, `getOrDefault`의 기본값 `0`은 사실상 사용되지 않습니다. 방어적 코딩으로 유지해도 무방합니다.

### 3.4 메서드별 Repository 결과 구조

| 메서드 | Repository | 결과 Object[] 구조 |
|--------|------------|-------------------|
| getLikeCountsBatch | countByBoardsGroupByReactionType | [boardId, reactionType, count] — LIKE만 사용 |
| getCommentCountsBatch | countByBoardsAndIsDeletedFalse | [boardId, count] |
| getViewCountsBatch | countByBoards | [boardId, count] |

---

## 4. 시간 복잡도

### 4.1 혼동하기 쉬운 부분

- **Map 조회 1회** (`get`, `getOrDefault`): **O(1)** (HashMap 평균)
- **전체 루프**: **O(1)이 아님** — O(n)

### 4.2 배치 메서드 내부 (for 루프)

| 구간 | 복잡도 | 설명 |
|------|--------|------|
| 바깥 for (배치 순회) | O(n/1000) | 반복 횟수 ≈ ⌈n/1000⌉ |
| 안쪽 for (결과 순회) | O(n) | 전체 board에 대한 결과 처리 |
| putIfAbsent | O(n) | 모든 boardId에 0 초기화 |
| **전체** | **O(n)** | n = boardIds.size() |

### 4.3 스냅샷 생성 시 Map 사용

```java
prideBoards.stream().map(board -> {
    likeCountsMap.getOrDefault(...);   // O(1) × n
    commentCountsMap.getOrDefault(...); // O(1) × n
    viewCountsMap.getOrDefault(...);   // O(1) × n
    ...
})
```

- Map 조회 1회: O(1)
- prideBoards n개 순회: **O(n)**

---

## 5. 최적화 여지

### 5.1 현재 구조의 한계

| 항목 | 현재 | 영향 |
|------|------|------|
| DB 호출 | 3번 **순차** 실행 | 총 대기 시간 = t1 + t2 + t3 |
| getLikeCountsBatch | LIKE/DISLIKE 등 **전체** 반환 후 Java에서 필터 | 불필요한 데이터 전송 |
| 코드 | 3개 메서드 거의 동일 패턴 | 중복 |

### 5.2 개선 방향

#### 1) 병렬 실행 (우선 적용 권장)

세 쿼리는 서로 독립적이므로 병렬 실행 가능.

```
현재:  like(100ms) → comment(100ms) → view(100ms)  ≈ 300ms
개선:  like(100ms)
       comment(100ms)  } 동시 실행  ≈ 100ms
       view(100ms)
```

`CompletableFuture` 등으로 3개 배치 메서드를 동시 호출.

#### 2) getLikeCountsBatch — LIKE만 DB에서 조회

현재: `GROUP BY reactionType`으로 모든 타입 반환 → Java에서 `if (reactionType == LIKE)` 필터

개선: JPQL에 `AND br.reactionType = 'LIKE'` 추가 → DB에서 LIKE만 조회.

#### 3) 통합 DTO (선택)

```java
// 현재: Map 3개 + getOrDefault 3번
// 개선: record BoardCounts(int likes, int comments, int views)
//       Map<Long, BoardCounts> 하나로 통합
```

가독성·유지보수 향상. 성능 영향은 미미.

### 5.3 우선순위

| 순위 | 개선 | 효과 | 난이도 |
|------|------|------|--------|
| 1 | 병렬 실행 | 응답 시간 약 1/3 수준 | 낮음 |
| 2 | LIKE 전용 쿼리 | 전송량·코드 단순화 | 낮음 |
| 3 | 통합 DTO | 가독성·유지보수 | 낮음 |

---

## 6. 요약

| 질문 | 답변 |
|------|------|
| **메서드 흐름** | generateSnapshots → boardIds 추출 → 3개 배치 조회 → 점수 계산·정렬 → 스냅샷 저장 |
| **자료구조** | `Map<Long, Integer>` — 모든 boardId 키 보장, 없으면 0 |
| **for 루프 복잡도** | O(1) 아님. 전체 O(n) |
| **최선인가** | 아님. 병렬 실행·LIKE 전용 쿼리로 개선 가능 |

---

## 참고

- **관련 파일**: `BoardPopularityService.java` (Lines 117-282)
- **관련 문서**: [Board 백엔드 성능 최적화](./board-backend-performance-optimization.md)
