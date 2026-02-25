# Missing Pet (실종 제보) 백엔드 성능 최적화 리팩토링

## 개요

Missing Pet 도메인의 백엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**대상 도메인**:
- **실종 제보 게시글**: MissingPetBoard, MissingPetBoardService
- **실종 제보 댓글**: MissingPetComment, MissingPetCommentService
- **관리자**: AdminMissingPetController

**참고 문서**: [Board 백엔드 성능 최적화](../board/board-backend-performance-optimization.md), [User 백엔드 성능 최적화](../user/user-backend-performance-optimization.md) 형식 준수

---

## 아키텍처 요약

```
MissingPetBoardController  → MissingPetBoardService, MissingPetCommentService, ConversationService
AdminMissingPetController  → MissingPetBoardService, MissingPetCommentService
```

---

## ✅ 이미 적용된 리팩토링 (Board 문서에서 공유)

| 항목 | 적용 결과 |
|------|----------|
| MissingPetCommentService getCommentCount | N건 로드 → `countByBoardAndIsDeletedFalse` 1 COUNT 쿼리 ✅ |
| MissingPetBoardService 성능 측정 로그 | 프로덕션 로그 제거 ✅ |
| extractPrimaryFileUrl | AttachmentFileService 공통화 ✅ |
| getBoards / getBoardsWithPaging | 파일 배치 조회, 댓글 수 배치 조회, toBoardDTOWithoutComments 사용 ✅ |
| getCommentsWithPaging / getComments | 댓글 파일 배치 조회 ✅ |

---

## 🔴 Critical (긴급) - 리팩토링

### 1. AdminMissingPetController - 전체 메모리 로드 및 메모리 필터링 ✅ **해결 완료**

**파일**: `AdminMissingPetController.java` (Lines 38-64)

**이전 문제**:
- `GET /api/admin/missing-pets`: `getBoards(status)` 호출 → **전체 실종 제보 메모리 로드**
- 메모리에서 `deleted`, `q` 필터링 후 반환
- 실종 제보 1만 건 시 → 1만 건 전부 로드 후 필터링

```java
// 현재 코드
List<MissingPetBoardDTO> all = missingPetBoardService.getBoards(status);
if (deleted != null) {
    all = all.stream().filter(b -> Boolean.TRUE.equals(b.getDeleted()) == wantDeleted)...
}
if (q != null && !q.isBlank()) {
    all = all.stream().filter(b -> ...contains(keyword))...
}
return ResponseEntity.ok(all);
```

**추가 문제**: `getBoards()`는 `isDeleted = false`만 조회하므로, Admin에서 `deleted=true` 요청 시 **삭제된 게시글이 조회되지 않음** (필터 결과 항상 빈 목록).

**해결 방안**:
1. **권장**: Admin용 페이징 API 추가 (`getAdminMissingPetsWithPaging`)
2. Specification 또는 `@Query`로 DB 레벨 필터링 (status, deleted, q)
3. Board 도메인 `getAdminBoardsWithPagingOptimized` 패턴 참고

**적용 결과** ✅:
- ✅ `GET /api/admin/missing-pets` → `GET /api/admin/missing-pets/paging` 변경
- ✅ `MissingPetBoardService.getAdminBoardsWithPaging()` 추가 (Specification + DB 페이징)
- ✅ `SpringDataJpaMissingPetBoardRepository`에 `JpaSpecificationExecutor` 추가
- ✅ `MissingPetBoardRepository`에 `findAll(Specification, Pageable)` 추가
- ✅ 프론트엔드 `listMissingPetsWithPaging` API 및 페이징 UI 적용

---

### 2. MissingPetCommentService - deleteAllCommentsByBoard N건 루프 save

**파일**: `MissingPetCommentService.java` (Lines 241-247)

**테스트**: `MissingPetCommentServiceDeleteAllCommentsTest.java` - `@Transactional`로 롤백되어 실 DB 영향 없음

**현재 문제**:
- `deleteAllCommentsByBoard(board)`: 댓글 **전체 조회** 후 루프마다 `save()` 호출
- 댓글 1000개 시 → 1 (SELECT) + 1000 (UPDATE) = 1001 쿼리

```java
// 현재 코드
public void deleteAllCommentsByBoard(MissingPetBoard board) {
    List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
    for (MissingPetComment c : comments) {
        c.setIsDeleted(true);
        c.setDeletedAt(java.time.LocalDateTime.now());
        commentRepository.save(c);  // N번 호출
    }
}
```

**해결 방안**:
- `@Modifying` + `@Query`로 배치 업데이트
- `clearAutomatically = true`: bulk update는 영속성 컨텍스트를 무시하고 DB만 수정하므로, PC와 DB 정합성 유지를 위해 실행 후 PC 초기화

```java
// SpringDataJpaMissingPetCommentRepository 추가
@Modifying(clearAutomatically = true)
@Query("UPDATE MissingPetComment mc SET mc.isDeleted = true, mc.deletedAt = :deletedAt WHERE mc.board.idx = :boardIdx AND mc.isDeleted = false")
int softDeleteAllByBoardIdx(@Param("boardIdx") Long boardIdx, @Param("deletedAt") LocalDateTime deletedAt);
```

---

## 🟠 High Priority - 리팩토링

### 3. MissingPetBoardController - updateStatus valueOf 예외 처리

**파일**: `MissingPetBoardController.java` (Lines 117-124), `AdminMissingPetController.java` (Lines 84-87)

**현재 문제**:
- `MissingPetStatus.valueOf(statusValue)`: 잘못된 값 시 `IllegalArgumentException` 발생
- 예외 메시지가 사용자 친화적이지 않음 (예: "No enum constant...")

**해결 방안**:
```java
try {
    status = MissingPetStatus.valueOf(statusValue);
} catch (IllegalArgumentException e) {
    throw new IllegalArgumentException(
        "유효하지 않은 상태입니다. MISSING, FOUND, CLOSED 중 하나를 선택해주세요.");
}
```

---

### 4. MissingPetBoardController - startMissingPetChat 불필요한 전체 조회

**파일**: `MissingPetBoardController.java` (Lines 204-214)

**현재 문제**:
- `getBoard(boardIdx, null, null)` 호출 → 게시글 전체 조회 (파일, 댓글 수 등)
- 필요한 것은 `reporterId`(userId)만

**해결 방안**:
- `MissingPetBoardRepository`에 `findUserIdByIdx(Long idx)` 또는 `Optional<Long> findUserIdByIdx(Long idx)` 추가
- 프로젝션 쿼리로 userId만 조회

```java
@Query("SELECT b.user.idx FROM MissingPetBoard b WHERE b.idx = :idx AND b.isDeleted = false")
Optional<Long> findUserIdByIdx(@Param("idx") Long idx);
```

---

### 5. AdminMissingPetController - listComments 삭제 필터 한계

**파일**: `AdminMissingPetController.java` (Lines 113-126)

**현재 문제**:
- `getComments(boardId)`는 `findByBoardAndIsDeletedFalseOrderByCreatedAtAsc` 사용 → **삭제된 댓글 미포함**
- Admin에서 `deleted=true` 요청 시 필터 결과 항상 빈 목록

**해결 방안**:
- Admin용 `getCommentsForAdmin(boardId, includeDeleted)` 메서드 추가
- `includeDeleted=true` 시 `findByBoardOrderByCreatedAtAsc` 사용 (삭제 포함)

---

### 6. restoreMissingPet - 미구현

**파일**: `AdminMissingPetController.java` (Lines 104-107)

**현재 문제**:
- `POST /api/admin/missing-pets/{id}/restore` → `UnsupportedOperationException` 발생

**해결 방안**:
- `MissingPetBoardService.restoreBoard(Long id)` 메서드 추가
- `isDeleted = false`, `deletedAt = null` 설정

---

## 🟡 Medium Priority

### 7. MissingPetConverter - toBoardDTOList Lazy Loading 위험

**파일**: `MissingPetConverter.java` (Lines 100-105)

**현재 문제**:
- `toBoardDTOList()`는 `toBoardDTO()` 사용 → `board.getComments()` 접근 시 **Lazy Loading 트리거**
- 현재 사용처 없음 (dead code 가능성)

**해결 방안**:
- 사용하지 않으면 제거
- 사용한다면 `toBoardDTOWithoutComments` 기반으로 변경

---

### 8. updateBoard - findById vs findByIdWithUser

**파일**: `MissingPetBoardService.java` (Line 256)

**현재 문제**:
- `boardRepository.findById(id)` 사용 → User 조인 없음
- `board.getUser()` 호출 시 Lazy Loading 가능 (ManyToOne 기본 EAGER이지만 확인 필요)

**해결 방안**:
- 이메일 인증 확인을 위해 User 필요 → `findByIdWithUser(id)` 사용으로 통일

---

## 🟢 Low Priority

### 9. 데이터베이스 인덱스

**Entity 인덱스 검토**:
- `missing_pet_board`: `user_idx`, `status`, `is_deleted`, `created_at`, `latitude`, `longitude`
- `missing_pet_comment`: `board_idx`, `user_idx`, `is_deleted`, `created_at`

**활용 쿼리**:
- `findAllByOrderByCreatedAtDesc`, `findByStatusOrderByCreatedAtDesc`: `is_deleted`, `created_at`
- `findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc`: `board_idx`, `is_deleted`
- 위치 기반 검색: `idx_missing_pet_location` (SPATIAL 또는 복합 인덱스)

---

## 체크리스트

- [x] AdminMissingPetController `listMissingPets` → 페이징 API 추가, DB 레벨 필터링 ✅
- [x] MissingPetCommentService `deleteAllCommentsByBoard` → 배치 UPDATE 쿼리 ✅
- [ ] MissingPetBoardController `updateStatus` → valueOf 예외 처리
- [ ] MissingPetBoardController `startMissingPetChat` → findUserIdByIdx 경량 조회
- [ ] AdminMissingPetController `listComments` → 삭제된 댓글 조회 옵션
- [ ] AdminMissingPetController `restoreMissingPet` → restoreBoard 구현
- [ ] MissingPetConverter `toBoardDTOList` → 사용처 확인 후 제거 또는 수정
- [ ] MissingPetBoardService `updateBoard` → findByIdWithUser 사용

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| Admin listMissingPets | 전체 메모리 로드, 메모리 필터링 | DB 레벨 필터링 + 페이징 |
| deleteAllCommentsByBoard (1000댓글) | 1001 쿼리 | 1 쿼리 (배치 UPDATE) |
| startMissingPetChat | getBoard 전체 조회 | userId 프로젝션 1 쿼리 |

---

## 관련 문서

- [Board 백엔드 성능 최적화](../board/board-backend-performance-optimization.md) - MissingPet 일부 항목 공유
- [Board DTO Record 리팩토링](../recordType/board/dto-record-refactoring.md) - MissingPet DTO 포함
- [Missing Pet 트러블슈팅 potential-issues](../../troubleshooting/missing-pet/potential-issues.md)
- [Missing Pet N+1 이슈](../../troubleshooting/missing-pet/n-plus-one-query-issue.md)
