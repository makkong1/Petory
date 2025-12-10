# 삭제/밴된 사용자 콘텐츠 필터링 구현

## 개요

삭제되거나 밴된 사용자가 작성한 게시글, 댓글, 펫케어 요청, 실종 제보 등이 일반 사용자에게 표시되지 않도록 쿼리 레벨 필터링을 구현했습니다.

## 문제점

**기존 상황:**
- 사용자가 삭제되거나 밴되어도 작성한 콘텐츠가 그대로 표시됨
- 개인정보 보호 문제
- 서비스 품질 저하

**예시:**
- 사용자 A가 탈퇴 → 작성한 게시글이 여전히 표시됨
- 사용자 B가 밴됨 → 작성한 댓글이 여전히 표시됨

## 해결 방법

### 쿼리 레벨 필터링 (방법 1)

**개념:**
- 모든 조회 쿼리에 작성자 상태 체크 추가
- `user.isDeleted = false AND user.status = 'ACTIVE'` 조건 추가

**장점:**
- ✅ 실시간 반영 (사용자 상태 변경 시 즉시 적용)
- ✅ 성능 좋음 (DB 레벨 필터링)
- ✅ 일관성 유지
- ✅ 복구 시 자동 반영

**단점:**
- ⚠️ 모든 쿼리 수정 필요

## 구현 내용

### 1. BoardRepository 수정

**수정된 쿼리:**
```java
// 전체 게시글 조회 - 작성자도 활성 상태여야 함
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();

// 카테고리별 조회 - 작성자도 활성 상태여야 함
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE b.category = :category " +
       "AND b.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(@Param("category") String category);

// 검색 쿼리 - 작성자도 활성 상태여야 함
@Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) as relevance " +
               "FROM board b " +
               "INNER JOIN users u ON b.user_idx = u.idx " +
               "WHERE b.is_deleted = false " +
               "AND u.is_deleted = false " +
               "AND u.status = 'ACTIVE' " +
               "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) " +
               "ORDER BY relevance DESC, b.created_at DESC", 
       nativeQuery = true)
Page<Board> searchByKeywordWithPaging(@Param("kw") String keyword, Pageable pageable);
```

**관리자용 쿼리 (작성자 상태 체크 없음):**
```java
// 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 콘텐츠도 포함)
@Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false ORDER BY b.createdAt DESC")
Page<Board> findAllByIsDeletedFalseForAdmin(Pageable pageable);
```

### 2. CommentRepository 수정

**수정된 쿼리:**
```java
// 삭제되지 않은 댓글만 조회 - 작성자도 활성 상태여야 함
@Query("SELECT c FROM Comment c JOIN FETCH c.user u " +
       "WHERE c.board = :board " +
       "AND c.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY c.createdAt ASC")
List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") Board board);

// 댓글 카운트 조회 - 작성자도 활성 상태여야 함
@Query("SELECT c.board.idx as boardId, COUNT(c) as count " +
       "FROM Comment c JOIN c.user u " +
       "WHERE c.board.idx IN :boardIds " +
       "AND c.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "GROUP BY c.board.idx")
List<Object[]> countByBoardsAndIsDeletedFalse(@Param("boardIds") List<Long> boardIds);
```

**관리자용 쿼리:**
```java
// 관리자용: 작성자 상태 체크 없이 조회 (삭제된 사용자 댓글도 포함)
@Query("SELECT c FROM Comment c JOIN FETCH c.user u WHERE c.board = :board AND c.isDeleted = false ORDER BY c.createdAt ASC")
List<Comment> findByBoardAndIsDeletedFalseForAdmin(@Param("board") Board board);
```

### 3. MissingPetBoardRepository 수정

**수정된 쿼리:**
```java
@Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

@Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u " +
       "WHERE b.status = :status " +
       "AND b.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(@Param("status") MissingPetStatus status);
```

### 4. MissingPetCommentRepository 수정

**수정된 쿼리:**
```java
@Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u " +
       "WHERE mc.board = :board " +
       "AND mc.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY mc.createdAt ASC")
List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") MissingPetBoard board);
```

### 5. CareRequestRepository 수정

**수정된 쿼리:**
```java
// 전체 케어 요청 조회 - 작성자도 활성 상태여야 함
@Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u " +
       "WHERE cr.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findAllActiveRequests();

// 상태별 케어 요청 조회 - 작성자도 활성 상태여야 함
@Query("SELECT cr FROM CareRequest cr JOIN FETCH cr.user u " +
       "WHERE cr.status = :status " +
       "AND cr.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findByStatusAndIsDeletedFalse(@Param("status") CareRequestStatus status);
```

### 6. Service 레이어 수정

**CommentService:**
```java
// 일반 사용자용: 작성자 상태 체크 포함
public List<CommentDTO> getComments(Long boardId) {
    // findByBoardAndIsDeletedFalseOrderByCreatedAtAsc 사용 (작성자 상태 체크 포함)
}

// 관리자용: 작성자 상태 체크 없음
public List<CommentDTO> getCommentsForAdmin(Long boardId) {
    // findByBoardAndIsDeletedFalseForAdmin 사용 (작성자 상태 체크 없음)
}
```

**BoardService:**
```java
// 관리자용 게시글 조회 - 작성자 상태 체크 없이 조회
public BoardPageResponseDTO getAdminBoardsWithPaging(...) {
    // findAllByIsDeletedFalseForAdmin 사용 (작성자 상태 체크 없음)
}
```

**CareRequestService:**
```java
// 전체 케어 요청 조회 - 작성자 상태 체크 포함
public List<CareRequestDTO> getAllCareRequests(String status, String location) {
    // findAllActiveRequests 또는 findByStatusAndIsDeletedFalse 사용 (작성자 상태 체크 포함)
}
```

## 필터링 조건

### 일반 사용자용 쿼리
```sql
WHERE content.isDeleted = false 
AND user.isDeleted = false 
AND user.status = 'ACTIVE'
```

### 관리자용 쿼리
```sql
WHERE content.isDeleted = false
-- 작성자 상태 체크 없음 (삭제된 사용자 콘텐츠도 조회 가능)
```

## 적용 범위

### 필터링되는 콘텐츠
- ✅ 커뮤니티 게시글 (Board)
- ✅ 커뮤니티 댓글 (Comment)
- ✅ 실종 제보 게시글 (MissingPetBoard)
- ✅ 실종 제보 댓글 (MissingPetComment)
- ✅ 펫케어 요청 (CareRequest)

### 필터링 조건
- 사용자가 삭제됨 (`isDeleted = true`)
- 사용자가 밴됨 (`status = 'BANNED'`)
- 사용자가 정지됨 (`status = 'SUSPENDED'`)

### 예외 사항
- 관리자 페이지에서는 삭제된 사용자 콘텐츠도 조회 가능
- 통계/분석용 쿼리는 별도 처리 필요 (현재는 일반 쿼리 사용)

## 효과

### 개선 사항
1. **개인정보 보호**
   - 탈퇴한 사용자의 콘텐츠가 자동으로 숨겨짐
   - 밴된 사용자의 콘텐츠가 자동으로 숨겨짐

2. **서비스 품질 향상**
   - 활성 사용자의 콘텐츠만 표시
   - 사용자 경험 개선

3. **실시간 반영**
   - 사용자 상태 변경 시 즉시 적용
   - 별도 배치 작업 불필요

4. **성능**
   - DB 레벨 필터링으로 효율적
   - 인덱스 활용 가능

### 사용자 상태별 동작

| 사용자 상태 | 일반 사용자 조회 | 관리자 조회 |
|------------|----------------|------------|
| ACTIVE | ✅ 표시 | ✅ 표시 |
| SUSPENDED | ❌ 숨김 | ✅ 표시 |
| BANNED | ❌ 숨김 | ✅ 표시 |
| isDeleted = true | ❌ 숨김 | ✅ 표시 |

## 주의사항

1. **관리자 페이지**
   - 관리자는 삭제된 사용자 콘텐츠도 조회 가능해야 함
   - 별도 쿼리 사용 (`ForAdmin` 접미사)

2. **통계/분석**
   - 통계용 쿼리는 작성자 상태 체크 없이 조회해야 할 수 있음
   - 필요 시 별도 쿼리 사용

3. **인덱스**
   - `user.isDeleted`, `user.status` 필드에 인덱스 추가 권장
   - 조인 성능 최적화

4. **복구 시**
   - 사용자 복구 시 작성한 콘텐츠가 자동으로 다시 표시됨
   - 별도 작업 불필요

## 향후 개선 사항

1. **인덱스 추가**
   ```sql
   CREATE INDEX idx_users_status_deleted ON users(status, is_deleted);
   ```

2. **캐시 무효화**
   - 사용자 상태 변경 시 관련 콘텐츠 캐시 무효화

3. **통계 쿼리 분리**
   - 통계용 쿼리는 작성자 상태 체크 없이 조회
   - 별도 메서드로 분리

4. **배치 작업**
   - 삭제된 사용자 콘텐츠 자동 정리 (선택사항)
   - 오래된 삭제 데이터 아카이빙

