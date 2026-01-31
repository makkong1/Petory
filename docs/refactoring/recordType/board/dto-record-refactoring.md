# Board 도메인 DTO → record 리팩토링

## 개요

Board 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (7개)

### 1. BoardPageResponseDTO

| 항목 | 내용 |
|------|------|
| **용도** | 게시글 페이징 응답 |
| **필드 수** | 7 (boards, totalCount, totalPages, currentPage, pageSize, hasNext, hasPrevious) |
| **전환 이유** | Response 전용, setter 미사용, 단순 구조. Jackson 직렬화 문제 없음 |
| **사용처** | BoardService (getAllBoardsWithPaging, searchBoardsWithPaging, getAdminBoardsWithPaging 등), BoardController, AdminBoardController |

### 2. CommentPageResponseDTO

| 항목 | 내용 |
|------|------|
| **용도** | 댓글 페이징 응답 |
| **필드 수** | 7 (comments, totalCount, totalPages, currentPage, pageSize, hasNext, hasPrevious) |
| **전환 이유** | Response 전용, setter 미사용, 단순 구조 |
| **사용처** | CommentService (getCommentsWithPaging), BoardController |

### 3. MissingPetBoardPageResponseDTO

| 항목 | 내용 |
|------|------|
| **용도** | 실종동물 게시글 페이징 응답 |
| **필드 수** | 7 (boards, totalCount, totalPages, currentPage, pageSize, hasNext, hasPrevious) |
| **전환 이유** | Response 전용, setter 미사용, 단순 구조 |
| **사용처** | MissingPetBoardService (getBoardsWithPaging), MissingPetBoardController |

### 4. MissingPetCommentPageResponseDTO

| 항목 | 내용 |
|------|------|
| **용도** | 실종동물 댓글 페이징 응답 |
| **필드 수** | 7 (comments, totalCount, totalPages, currentPage, pageSize, hasNext, hasPrevious) |
| **전환 이유** | Response 전용, setter 미사용, 단순 구조 |
| **사용처** | MissingPetCommentService (getCommentsWithPaging), MissingPetBoardService (getBoard 시 댓글 페이징), MissingPetBoardController |

### 5. ReactionRequest

| 항목 | 내용 |
|------|------|
| **용도** | 좋아요/싫어요 반응 요청 (`@RequestBody`) |
| **필드 수** | 2 (userId, reactionType) |
| **전환 이유** | Request 전용, 필드 2개로 단순. Jackson 역직렬화(생성자) 정상 동작 |
| **사용처** | BoardController (reactToBoard, reactToComment) |

### 6. ReactionSummaryDTO

| 항목 | 내용 |
|------|------|
| **용도** | 좋아요/싫어요 요약 응답 |
| **필드 수** | 3 (likeCount, dislikeCount, userReaction) |
| **전환 이유** | Response 전용, 필드 3개, setter 미사용 |
| **사용처** | ReactionService (buildBoardSummary, buildCommentSummary), BoardController |

### 7. BoardPopularitySnapshotDTO

| 항목 | 내용 |
|------|------|
| **용도** | 인기 게시글 스냅샷 응답 |
| **필드 수** | 14 |
| **전환 이유** | Response 전용, 이전에 이미 record로 전환 완료 |
| **사용처** | BoardPopularityService, BoardPopularitySnapshotConverter, BoardController |

---

## record로 전환하지 않은 DTO (4개)

### BoardDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | `BoardService.applyReactionCounts()`, `applyAttachmentInfo()`에서 **setter로 인-place 수정** 사용. record는 불변이므로 적용 불가. 전환 시 매핑 로직 전면 수정 필요 |

### CommentDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 이번에는 미적용 |
| **이유** | Request+Response 혼용. CommentService에서 `dto.setAttachments()`, `dto.setCommentFilePath()`로 **가변 수정** 사용. record 전환 시 mapWithReactionCountsWithoutFiles 등 로직 수정 필요 |

### MissingPetBoardDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | **필드 27개** → record 생성자 과도하게 김. Request 역직렬화 복잡. 빌더 유지가 가독성·유지보수에 유리 |

### MissingPetCommentDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 이번에는 미적용 |
| **이유** | MissingPetCommentService에서 `dto.setAttachments()`, `dto.setImageUrl()`로 **가변 수정** 사용. record 전환 시 변환 로직 수정 필요 |

---

## 변경 사항 요약

| 변경 유형 | 내용 |
|----------|------|
| **DTO 정의** | Lombok `@Data` `@Builder` 제거 → `public record XxxDTO(...)` |
| **생성** | `.builder().field(x).build()` → `new XxxDTO(...)` |
| **접근** | `dto.getXxx()` → `dto.xxx()` (record accessor) |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
