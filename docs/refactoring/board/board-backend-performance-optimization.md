# Board 백엔드 성능 최적화 리팩토링

## 개요

Board 도메인의 백엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**대상 도메인**:
- **일반 게시글**: Board, Comment, Reaction, BoardViewLog
- **실종 제보**: MissingPetBoard, MissingPetComment
- **인기 게시글**: BoardPopularitySnapshot

**참고 문서**: [User 백엔드 성능 최적화](../user/user-backend-performance-optimization.md) 형식 준수

---

## 아키텍처 요약

```
BoardController          → BoardService, CommentService, ReactionService, BoardPopularityService
MissingPetBoardController → MissingPetBoardService, MissingPetCommentService, ConversationService
AdminBoardController     → BoardService, CommentService
```

---

## 🔴 Critical (긴급) - 리팩토링

### 1. AdminBoardController - 전체 게시글 메모리 로드

**파일**: `AdminBoardController.java` (Lines 31-71)

**현재 문제**:
- `GET /api/admin/boards` (페이징 없음): `getAllBoards(category)` 호출 → **전체 게시글 메모리 로드**
- `deleted=true` 요청 시: `getAllBoards(null)` **2번 호출** (category 필터용 1회 + deleted 포함 1회)
- 메모리 필터링 (status, deleted, q) → 게시글 수 증가 시 OOM/지연 위험

```java
// 현재 코드 (listBoards - 페이징 없음)
List<BoardDTO> all = boardService.getAllBoards(category);  // 전체 로드
if (Boolean.TRUE.equals(deleted)) {
    List<BoardDTO> allIncludingDeleted = boardService.getAllBoards(null);  // 또 전체 로드!
    // ...
}
// 메모리에서 stream().filter() ...
```

**해결 방안**:
1. **권장**: `GET /api/admin/boards` 엔드포인트 제거 또는 `/paging`으로 리다이렉트
2. 프론트엔드가 이미 `/paging` 사용 중이면 기존 엔드포인트 deprecate 후 제거
3. User 도메인 `getAllUsers()` 제거 패턴과 동일

**적용 결과** ✅:
- ✅ `listBoards` (GET /api/admin/boards) 엔드포인트 제거
- ✅ `GET /api/admin/boards/{id}` 단일 게시글 조회 API 추가 (`getBoardForAdmin`)
- ✅ ReportDetailModal: `listBoards` → `getBoard(id)` 변경 (전체 로드 → 단건 조회)

---

### 2. AdminBoardController - 관리자 페이징 메모리 필터링

**파일**: `AdminBoardController.java` (Line 83), `BoardService.java` (Lines 79-164)

**현재 문제**:
- `GET /api/admin/boards/paging` → `getAdminBoardsWithPaging()` 호출
- `getAdminBoardsWithPaging()`: **전체 게시글 조회** (`findAllForAdmin` 또는 `findAllByIsDeletedFalseForAdmin`)
- 메모리에서 category, status, deleted, q 필터링 후 `subList()`로 페이징
- 게시글 1만 건 시 → 1만 건 전부 로드 후 20건만 반환

**해결 방안**:
- `BoardService.getAdminBoardsWithPagingOptimized()` **이미 구현됨** (Lines 458-521)
- Specification + DB 레벨 필터링 + 페이징
- AdminBoardController에서 `getAdminBoardsWithPaging` → `getAdminBoardsWithPagingOptimized` 변경

```java
// BoardService - 이미 존재하는 최적화 버전
public BoardPageResponseDTO getAdminBoardsWithPagingOptimized(
        String status, Boolean deleted, String category, String q, int page, int size) {
    Specification<Board> spec = ...;  // DB 레벨 필터링
    Page<Board> boardPage = boardRepository.findAll(spec, pageable);
    // ...
}
```

**적용 결과** ✅:
- ✅ AdminBoardController `listBoardsWithPaging` → `getAdminBoardsWithPagingOptimized` 호출로 변경
- ✅ DB 레벨 필터링 + 페이징 (메모리 필터링 제거)

---

### 3. MissingPetCommentService - getCommentCount 비효율

**파일**: `MissingPetCommentService.java` (Lines 214-219)

**현재 문제**:
- `getCommentCount(MissingPetBoard board)`: 댓글 **전체 조회** 후 `size()` 반환
- 단일 게시글 상세 조회 시 사용 → 댓글 1000개면 1000건 로드

```java
// 현재 코드 - TODO 주석 있음
public int getCommentCount(MissingPetBoard board) {
    List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
    return comments.size();  // N건 로드 후 개수만 반환
}
```

**해결 방안**:
- `SpringDataJpaMissingPetCommentRepository`에 `countByBoardAndIsDeletedFalse(MissingPetBoard board)` 추가
- 또는 `countByBoardIdxAndIsDeletedFalse(Long boardIdx)` (boardIdx만 전달)

```java
@Query("SELECT COUNT(mc) FROM MissingPetComment mc JOIN mc.user u " +
       "WHERE mc.board = :board AND mc.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
long countByBoardAndIsDeletedFalse(@Param("board") MissingPetBoard board);
```

**적용 결과** ✅:
- ✅ `countByBoardAndIsDeletedFalse` COUNT 쿼리 추가 (Repository, Adapter)
- ✅ `getCommentCount`: N건 로드 → 1 COUNT 쿼리

---

## 🔴 트러블슈팅 (런타임 발견 이슈)

### 4. N+1 쿼리 - CommentService 댓글 반응(좋아요/싫어요)

**파일**: `CommentService.java` (Lines 256-278)

**발견 경로**: `getCommentsWithPaging()`, `getComments()`, `getCommentsForAdmin()` 호출 시 댓글 수가 많을수록 쿼리 수 급증 → 프로파일링으로 N+1 발견

**문제 원인**:
- `mapWithReactionCountsWithoutFiles()`: 댓글마다 `countByCommentAndReactionType` 2회 호출 (LIKE, DISLIKE)
- 댓글 N개 시: 1 (댓글 페이징) + **2N** (반응 카운트) = N+1 유사 패턴
- `mapWithReactionCounts()`: 2N + 파일 N 쿼리 추가

```java
// mapWithReactionCountsWithoutFiles - 댓글마다 2회 DB 조회
private CommentDTO mapWithReactionCountsWithoutFiles(Comment comment) {
    long likeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.LIKE);   // 1
    long dislikeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.DISLIKE); // 2
    // ...
}
```

**해결 방안**:
1. `CommentReactionRepository`에 `countByCommentsGroupByReactionType(List<Long> commentIds)` 배치 조회 추가
2. `CommentService.getCommentsWithPaging()`에서 댓글 ID 목록 추출 후 배치 조회
3. Board의 `BoardReactionRepository.countByBoardsGroupByReactionType` 패턴 참고

```java
// SpringDataJpaCommentReactionRepository 추가
@Query("SELECT cr.comment.idx as commentId, cr.reactionType, COUNT(cr) " +
       "FROM CommentReaction cr WHERE cr.comment.idx IN :commentIds " +
       "GROUP BY cr.comment.idx, cr.reactionType")
List<Object[]> countByCommentsGroupByReactionType(@Param("commentIds") List<Long> commentIds);
```

**적용 결과** ✅:
- ✅ `countByCommentsGroupByReactionType` 배치 조회 메서드 추가 (Repository, Adapter)
- ✅ `getCommentsWithPaging`, `getComments`, `getCommentsForAdmin` 배치 조회 적용
- ✅ 댓글 N개 시 2N 쿼리 → 1~2 쿼리로 감소

**상세**: [comment-reaction-query/troubleshooting.md](./comment-reaction-query/troubleshooting.md)

---

## 🟠 High Priority - 리팩토링

### 5. BoardService.getBoard - shouldIncrementView 시 User 중복 조회

**파일**: `BoardService.java` (Lines 204-424)

**현재 문제**:
- `getBoard(idx, viewerId)`: `viewerId != null`이면 `shouldIncrementView()` 호출
- `shouldIncrementView()`: `usersRepository.findById(viewerId)` 1회
- `viewerId`는 이미 인증된 사용자 ID → Controller에서 전달 시점에 검증됨
- 단, `viewerId`가 null이 아닌 모든 요청에서 User 조회 발생 (필요한 경우에만)

**개선 포인트**:
- `existsByBoardAndUser` 전에 User 조회가 필수인지 검토
- `BoardViewLogRepository`에 `existsByBoardIdxAndUserId(Long boardIdx, Long userId)` 추가 시 User 엔티티 없이 조회 가능
- BoardViewLog 엔티티는 `board_id`, `user_id` 컬럼 사용 (Board.idx, Users.idx 참조)

```java
// SpringDataJpaBoardViewLogRepository
@Query("SELECT COUNT(bvl) > 0 FROM BoardViewLog bvl WHERE bvl.board.idx = :boardIdx AND bvl.user.idx = :userId")
boolean existsByBoardIdxAndUserId(@Param("boardIdx") Long boardIdx, @Param("userId") Long userId);
```

- `shouldIncrementView`에서 `Board`, `Users` 대신 `boardIdx`, `userId`만 사용하도록 변경

---

### 6. BoardController - 디버그 로그 제거 ✅

**파일**: `BoardController.java` (Line 49)

**현재 문제**:
- `System.out.println("=== API 호출됨: GET /api/boards ===");` 프로덕션 코드에 남아있음

**적용 결과** ✅:
- ✅ `System.out.println` 제거

---

### 7. MissingPetBoardService - 프로덕션 성능 측정 로그 ✅

**파일**: `MissingPetBoardService.java` (Lines 54-128, 151-211, 224-276)

**현재 문제**:
- `getBoardsWithPaging()`, `getBoards()`, `getBoard()` 내부에 상세 성능 측정 로그 (`log.info`)
- 실행 시간, 메모리 사용량, 게시글당 평균 시간 등 매 요청마다 출력
- 프로덕션 로그 과다 → 로그 스토리지/가독성 저하

**적용 결과** ✅:
- ✅ 성능 측정 코드 전체 제거 (`getBoardsWithPaging`, `getBoards`, `getBoard`)

---

### 8. BoardService.getBoard - @Cacheable + @Transactional 혼용 ✅

**파일**: `BoardService.java` (Lines 202-214)

**현재 문제**:
- `@Cacheable(value = "boardDetail", key = "#idx")` + `@Transactional` 동시 적용
- `getBoard()`는 조회수 증가 로직 포함 → **캐시 시 조회수 미반영** 가능
- 동일 게시글 재요청 시 캐시에서 반환 → `incrementViewCount` 미실행

**적용 결과** ✅:
- ✅ `@Cacheable` 제거 (조회수 실시간 반영 우선)

---

## 🟡 Medium Priority

### 9. extractPrimaryFileUrl 중복 코드 ✅

**파일**: `BoardService`, `CommentService`, `MissingPetBoardService`, `MissingPetCommentService`

**현재 문제**:
- 동일 로직이 4개 서비스에 중복 구현
- `attachments` null/empty 체크 → 첫 번째 파일의 `downloadUrl` 또는 `buildDownloadUrl(filePath)` 반환

**적용 결과** ✅:
- ✅ `AttachmentFileService.extractPrimaryFileUrl(List<? extends FileDTO>)` 추가
- ✅ 4개 서비스에서 중복 메서드 제거, `attachmentFileService.extractPrimaryFileUrl()` 호출로 변경

---

### 10. CommentService - getComments, getCommentsForAdmin N+1 ✅

**파일**: `CommentService.java` (Lines 120-143)

**현재 문제**:
- `getComments()`, `getCommentsForAdmin()`: `mapWithReactionCounts()` 사용
- 댓글별 반응 2회 + 파일 1회 = **3N 쿼리**
- Admin 댓글 목록, 비페이징 댓글 목록 API에서 사용

**적용 결과** ✅:
- ✅ 트러블슈팅 4 적용 시 함께 해결됨
- ✅ `getReactionCountsBatch` + `getAttachmentsBatch` + `mapCommentsWithReactionCountsBatch` 사용
- ✅ 3N 쿼리 → 3~4 쿼리로 감소

---

### 11. BoardConverter.toDTO - comments Lazy Loading 위험 ✅

**파일**: `BoardConverter.java` (Lines 18-21)

**현재 문제**:
```java
if (aggregatedCommentCount == null && board.getComments() != null) {
    aggregatedCommentCount = board.getComments().size();  // Lazy Loading 트리거!
}
```
- `board.getComments()` 접근 시 Lazy Loading → N+1

**적용 결과** ✅:
- ✅ `board.getComments()` 접근 제거
- ✅ `board.getCommentCount()`만 사용 (null 시 0)

---

### 12. AdminBoardController listBoards - getAllBoards 2회 호출 ✅

**파일**: `AdminBoardController.java` (Lines 38-48)

**현재 문제** (1번과 연계):
- `deleted=true` 요청 시 `getAllBoards` 2회 호출

**적용 결과** ✅:
- ✅ Critical 1 적용 시 함께 해결됨 (`listBoards` 엔드포인트 제거)

---

## 🟢 Low Priority

### 13. ReactionService - buildBoardSummary/buildCommentSummary 중복 쿼리 ✅

**파일**: `ReactionService.java` (Lines 123-151)

**현재 문제**:
- `reactToBoard()` 완료 후 `buildBoardSummary()` 호출 → count 2회 + find 1회
- `reactToComment()` 완료 후 `buildCommentSummary()` 호출 → count 2회 + find 1회

**적용 결과** ✅:
- ✅ Board 엔티티에 `dislikeCount` 추가, `updateBoardReactionCounts`로 like/dislike 실시간 업데이트
- ✅ `reactToBoard`: `buildBoardSummaryFromCounts` 사용 → **0 추가 쿼리** (엔티티 값만 사용)
- ✅ `reactToComment`: `buildCommentSummaryWithUserReaction` 사용 → userReaction 계산값 전달, **findByCommentAndUser 1회 제거**
- ✅ BoardConverter: `dislikes`에 `board.getDislikeCount()` 사용
- ✅ DB 마이그레이션: `docs/migration/db/add_board_dislike_count_column.sql`

---

### 14. 데이터베이스 인덱스

**Entity 인덱스 검토**:
- `board`: `user_idx`, `category`, `is_deleted`, `created_at`, `status`
- `comment`: `board_idx`, `user_idx`, `is_deleted`
- `board_reaction`: `board_idx`, `user_idx` (unique)
- `board_view_log`: `board_id`, `user_id` (unique)
- FULLTEXT: `board.title`, `board.content` (검색용, 이미 적용된 것으로 추정)

---

### 15. BoardPopularityService - TARGET_CATEGORY 하드코딩

**파일**: `BoardPopularityService.java` (Line 33)

**현재 문제**:
- `TARGET_CATEGORY = "자랑"` 하드코딩
- "PRIDE" 레거시 호환 로직 존재

**해결**: `application.yml` 또는 상수 클래스로 분리

---

## 체크리스트

- [x] AdminBoardController `listBoards` (페이징 없음) 제거 또는 `/paging` 전환 ✅
- [x] AdminBoardController `listBoardsWithPaging` → `getAdminBoardsWithPagingOptimized` 사용 ✅
- [x] CommentService 댓글 반응 배치 조회 (countByCommentsGroupByReactionType) ✅
- [x] MissingPetCommentService getCommentCount → COUNT 쿼리 ✅
- [x] BoardController System.out.println 제거 ✅
- [x] MissingPetBoardService 성능 측정 로그 제거 ✅
- [x] BoardService getBoard @Cacheable 제거 (조회수 실시간 반영) ✅
- [x] extractPrimaryFileUrl 공통화 ✅
- [x] BoardConverter toDTO comments Lazy Loading 방지 (commentCount만 사용) ✅
- [ ] BoardViewLogRepository existsByBoardIdxAndUserId 추가 (shouldIncrementView 최적화)

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| Admin listBoards (페이징 없음) | 전체 메모리 로드, 2회 조회 | 엔드포인트 제거 또는 페이징 |
| Admin listBoardsWithPaging | 전체 로드 후 메모리 필터링 | DB 레벨 필터링 + 페이징 |
| Comment 목록 (20개) | 40+ 쿼리 (반응 2N) | 3~4 쿼리 (배치) |
| MissingPet getCommentCount | N건 로드 | 1 COUNT 쿼리 |
| 프로덕션 로그 | 매 요청 성능 로그 | debug 레벨 또는 제거 |

---

## 관련 문서

- [User 백엔드 성능 최적화](../user/user-backend-performance-optimization.md)
- [CommentService 댓글 반응 N+1 트러블슈팅](./comment-reaction-query/troubleshooting.md)
- [Board 검색 최적화](../recordType/board/board-search-optimization.md)
- [Board DTO Record 리팩토링](../recordType/board/dto-record-refactoring.md)
