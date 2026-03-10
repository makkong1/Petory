# Board 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size (또는 쿼리 레벨 Fetch Join)

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| Board 단건 상세 | `getBoard`, `getBoardForAdmin` | Fetch Join | 적용 필요 |
| Board 페이징 목록 | `getAllBoardsWithPaging`, `searchBoardsWithPaging` 등 | Fetch Join (쿼리) | ✅ 이미 적용됨 |
| Board Admin 페이징 | `getAdminBoardsWithPagingOptimized` | Fetch Join | 적용 필요 (Spec에 user fetch 없음) |
| Comment 페이징 | `getCommentsWithPaging` | Fetch Join (쿼리) | ✅ 이미 적용됨 |
| MissingPetBoard 단건 | `getBoard` | Fetch Join | ✅ 이미 적용됨 (findByIdWithUser) |
| MissingPetBoard 페이징 | `getBoardsWithPaging` | Fetch Join (쿼리) | ✅ 이미 적용됨 |
| MissingPetBoard Admin 페이징 | `getAdminBoardsWithPaging` | Fetch Join | 적용 필요 (Spec에 user fetch 없음) |
| MissingPetComment 페이징 | `getCommentsWithPaging` | Fetch Join (쿼리) | ✅ 이미 적용됨 |

---

## 1. Board (일반 게시글)

### 1.1 단건 상세 조회 (Fetch Join) — 적용 필요

**대상**
- `getBoard(idx, viewerId)` — 게시글 상세 (`GET /api/boards/{id}`)
- `getBoardForAdmin(idx)` — 관리자 단건 조회 (`GET /api/admin/boards/{id}`)

**현재 문제**
```java
// BoardService.getBoard (224행), getBoardForAdmin (207행)
Board board = boardRepository.findById(idx).orElseThrow(...);  // User 미포함
return mapBoardWithDetails(board);  // boardConverter.toDTO() → board.getUser() Lazy Load
```
→ Board 1회 + User 1회 = 2회 쿼리

**적용 위치**
| 파일 | 작업 |
|------|------|
| `SpringDataJpaBoardRepository` | `findByIdWithUser(Long idx)` 추가 (JOIN FETCH b.user) |
| `BoardRepository` | 메서드 시그니처 추가 |
| `JpaBoardAdapter` | 메서드 구현 |
| `BoardService.getBoard`, `getBoardForAdmin` | `findById` → `findByIdWithUser` |

---

### 1.2 페이징 목록 조회 — ✅ 이미 적용됨

**대상**
- `getAllBoardsWithPaging`, `getAllBoards`, `searchBoardsWithPaging`, `getMyBoards` 등

**현재 상태**
- `findAllByIsDeletedFalseOrderByCreatedAtDesc`, `findByCategory...`, `searchByNicknameWithPaging` 등 **모두 `JOIN FETCH b.user`** 포함
- `BoardConverter.toDTO()`에서 `board.getUser()` 접근 시 추가 쿼리 없음

---

### 1.3 Admin 페이징 — 적용 필요

**대상**
- `getAdminBoardsWithPagingOptimized(status, deleted, category, q, page, size)`

**현재 문제**
```java
// BoardService (410행)
Page<Board> boardPage = boardRepository.findAll(spec, pageable);
// spec에 검색어(q) 있을 때만 root.join("user") — WHERE용, fetch 아님
// spec에 q 없으면 user 미조인 → boardConverter.toDTO() 시 N+1
```

**적용**
- Specification에 user fetch 추가 (항상 `root.fetch("user")`) 또는
- `@EntityGraph(attributePaths = "user")` 적용된 별도 메서드 사용

---

### 1.4 Board.comments

- `BoardConverter`는 `commentCount` 필드만 사용, `board.getComments()` 미접근
- 페이징 목록에서 comments 접근 없음 → Batch Size 불필요
- `deleteBoard`에서만 `board.getComments()` 사용 (단건) → Fetch Join 검토 가능

---

## 2. Comment (일반 게시글 댓글)

### 2.1 페이징 목록 — ✅ 이미 적용됨

**대상**
- `getCommentsWithPaging`, `getComments`, `getCommentsForAdmin`

**현재 상태**
- `findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc`, `findByBoardAndIsDeletedFalseOrderByCreatedAtAsc` 등 **`JOIN FETCH c.user`** 포함
- `CommentConverter.toDTO()`에서 `comment.getUser()`, `comment.getBoard()` 접근 — user는 이미 fetch, board는 동일 게시글이라 1회 로드

---

## 3. MissingPetBoard (실종 제보)

### 3.1 단건 상세 — ✅ 이미 적용됨

**대상**
- `getBoard(id, commentPage, commentSize)` — `GET /api/missing-pets/{id}`

**현재 상태**
- `findByIdWithUser(id)` — `JOIN FETCH b.user` 이미 적용

---

### 3.2 페이징 목록 — ✅ 이미 적용됨

**대상**
- `getBoardsWithPaging`, `getBoards`

**현재 상태**
- `findAllByOrderByCreatedAtDesc`, `findByStatusOrderByCreatedAtDesc` — **`JOIN FETCH b.user`** 포함

---

### 3.3 Admin 페이징 — 적용 필요

**대상**
- `getAdminBoardsWithPaging(status, deleted, q, page, size)`

**현재 문제**
```java
// MissingPetBoardService (346행)
Page<MissingPetBoard> boardPage = missingPetBoardRepository.findAll(spec, pageable);
// spec에 q 있을 때만 root.join("user") — fetch 아님
// q 없으면 toBoardDTOWithoutComments() 시 board.getUser() N+1
```

**적용**
- Specification에 user fetch 추가

---

### 3.4 MissingPetBoard.comments

- `toBoardDTOWithoutComments` 사용 — comments 미접근
- 페이징 목록에서 N+1 없음

---

## 4. MissingPetComment (실종 제보 댓글)

### 4.1 페이징 목록 — ✅ 이미 적용됨

**대상**
- `getCommentsWithPaging`, `getComments`

**현재 상태**
- `findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc`, `findByBoardAndIsDeletedFalseOrderByCreatedAtAsc` — **`JOIN FETCH mc.user`** 포함

---

## 5. 적용 불필요 구간

| 구간 | 이유 |
|------|------|
| `Board.comments` (목록) | BoardConverter가 commentCount만 사용 |
| `MissingPetBoard.comments` (목록) | toBoardDTOWithoutComments 사용 |
| `Comment` 단건 (addComment, updateComment 등) | 저장/수정 후 mapWithReactionCounts — 단건 1회 |
| `BoardReaction`, `CommentReaction` | 별도 조회, 연관 컬렉션 N+1 없음 |

---

## 6. 적용 위치 정리

| 파일 | 작업 |
|------|------|
| `SpringDataJpaBoardRepository` | `findByIdWithUser(Long idx)` 추가 |
| `BoardRepository`, `JpaBoardAdapter` | 위 메서드 추가 |
| `BoardService.getBoard`, `getBoardForAdmin` | `findByIdWithUser` 사용 |
| `BoardService.getAdminBoardsWithPagingOptimized` | Specification에 user fetch 추가 |
| `MissingPetBoardService.getAdminBoardsWithPaging` | Specification에 user fetch 추가 |

---

## 7. 참고 문서

- [board-backend-performance-optimization.md](../../board/board-backend-performance-optimization.md)
- [board/comment-reaction-query/troubleshooting.md](../../board/comment-reaction-query/troubleshooting.md)
