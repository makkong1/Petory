# 트랜잭션 관리 & 동시성 제어 실제 사례

## 🔄 트랜잭션 관리 사례

### 1. 게시글 삭제 시 댓글 일괄 삭제 (Cascade Delete)

**문제 상황:**
- 게시글 삭제 시 연관된 댓글도 함께 삭제해야 함
- 게시글 삭제는 성공했지만 댓글 삭제가 실패하면 데이터 불일치 발생

**해결 코드:**
```java
// BoardService.java
@Transactional  // 하나의 트랜잭션으로 처리
public void deleteBoard(long idx) {
    Board board = boardRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("Board not found"));

    // Soft delete: 게시글 삭제
    board.setStatus(ContentStatus.DELETED);
    board.setIsDeleted(true);
    board.setDeletedAt(LocalDateTime.now());
    
    // 연관된 댓글도 함께 삭제 (같은 트랜잭션 내)
    if (board.getComments() != null) {
        board.getComments().forEach(c -> {
            c.setStatus(ContentStatus.DELETED);
            c.setIsDeleted(true);
            c.setDeletedAt(LocalDateTime.now());
        });
    }
    
    boardRepository.saveAndFlush(board);  // 즉시 플러시하여 트랜잭션 확정
}
```

**효과:**
- 게시글과 댓글이 원자적으로 삭제됨
- 중간에 실패하면 전체 롤백되어 데이터 일관성 유지

---

### 2. 댓글 추가 시 게시글 카운트 동기화

**문제 상황:**
- 댓글 추가 시 게시글의 `commentCount`도 증가해야 함
- 댓글 저장은 성공했지만 카운트 업데이트가 실패하면 불일치 발생

**해결 코드:**
```java
// CommentService.java
@CacheEvict(value = "boardDetail", key = "#boardId")
@Transactional  // 댓글 저장 + 카운트 업데이트를 하나의 트랜잭션으로
public CommentDTO addComment(Long boardId, CommentDTO dto) {
    Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new IllegalArgumentException("Board not found"));
    Users user = usersRepository.findById(dto.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // 댓글 저장
    Comment comment = Comment.builder()
            .board(board)
            .user(user)
            .content(dto.getContent())
            .build();
    Comment saved = commentRepository.save(comment);

    // commentCount 실시간 업데이트 (같은 트랜잭션 내)
    incrementBoardCommentCount(board);
    boardRepository.save(board);

    // 파일 첨부 (같은 트랜잭션)
    if (dto.getCommentFilePath() != null) {
        attachmentFileService.syncSingleAttachment(
            FileTargetType.COMMENT, 
            saved.getIdx(), 
            dto.getCommentFilePath(), 
            null
        );
    }

    // 알림 발송 (비동기, 트랜잭션 외부)
    if (!board.getUser().getIdx().equals(user.getIdx())) {
        notificationService.createNotification(...);
    }

    return mapWithReactionCounts(saved);
}

private void incrementBoardCommentCount(Board board) {
    Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
    board.setCommentCount(currentCount + 1);
}
```

**효과:**
- 댓글 저장과 카운트 업데이트가 원자적으로 처리됨
- 파일 첨부도 같은 트랜잭션에 포함되어 일관성 유지

---

### 3. 읽기 전용 트랜잭션 최적화

**문제 상황:**
- 조회 작업에서 불필요한 쓰기 락 발생
- 읽기 작업이 많을수록 성능 저하

**해결 코드:**
```java
// BoardService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본값: 읽기 전용
public class BoardService {
    
    // 읽기 작업: 기본값(readOnly = true) 사용
    public List<BoardDTO> getAllBoards(String category) {
        // ...
    }
    
    // 쓰기 작업: 명시적으로 @Transactional 사용
    @Transactional  // readOnly = false
    public BoardDTO createBoard(BoardDTO dto) {
        // ...
    }
}
```

**효과:**
- 읽기 작업에서 쓰기 락 미발생 → 성능 향상
- 명시적 트랜잭션 경계로 의도 명확화

---

### 4. 펫케어 요청 생성 시 펫 소유자 검증

**문제 상황:**
- 펫케어 요청 생성 시 펫 소유자 확인 필요
- 펫 정보 설정과 요청 저장이 분리되면 검증 타이밍 이슈 발생

**해결 코드:**
```java
// CareRequestService.java
@Transactional  // 펫 검증 + 요청 저장을 하나의 트랜잭션으로
public CareRequestDTO createCareRequest(CareRequestDTO dto) {
    Users user = usersRepository.findById(dto.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

    CareRequest.CareRequestBuilder builder = CareRequest.builder()
            .title(dto.getTitle())
            .description(dto.getDescription())
            .date(dto.getDate())
            .user(user)
            .status(CareRequestStatus.OPEN);

    // 펫 정보 설정 (선택사항)
    if (dto.getPetIdx() != null) {
        Pet pet = petRepository.findById(dto.getPetIdx())
                .orElseThrow(() -> new RuntimeException("Pet not found"));
        
        // 펫 소유자 확인 (같은 트랜잭션 내에서 검증)
        if (!pet.getUser().getIdx().equals(user.getIdx())) {
            throw new RuntimeException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
        }
        builder.pet(pet);
    }

    CareRequest saved = careRequestRepository.save(builder.build());
    return careRequestConverter.toDTO(saved);
}
```

**효과:**
- 펫 소유자 검증과 요청 저장이 원자적으로 처리됨
- 검증 실패 시 전체 롤백

---

## 🔒 동시성 제어 사례

### 1. 게시글 조회수 중복 방지

**문제 상황:**
- 동시에 같은 게시글을 조회하면 조회수가 중복 증가
- 사용자가 새로고침할 때마다 조회수 증가

**해결 코드:**
```java
// BoardService.java
@Transactional
public BoardDTO getBoard(long idx, Long viewerId) {
    Board board = boardRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("Board not found"));

    // 중복 조회 방지 로직
    if (shouldIncrementView(board, viewerId)) {
        incrementViewCount(board);
    }

    return mapWithReactions(board);
}

private boolean shouldIncrementView(Board board, Long viewerId) {
    if (viewerId == null) {
        return true;  // 비로그인 사용자는 항상 증가
    }

    Users viewer = usersRepository.findById(viewerId).orElse(null);
    if (viewer == null) {
        return true;
    }

    // BoardViewLog 테이블에서 이미 조회한 기록 확인
    boolean alreadyViewed = boardViewLogRepository.existsByBoardAndUser(board, viewer);
    if (alreadyViewed) {
        return false;  // 이미 조회했으면 증가 안 함
    }

    // 조회 기록 추가 (같은 트랜잭션 내)
    BoardViewLog log = BoardViewLog.builder()
            .board(board)
            .user(viewer)
            .build();
    boardViewLogRepository.save(log);
    return true;
}

private void incrementViewCount(Board board) {
    Integer current = board.getViewCount();
    board.setViewCount((current == null ? 0 : current) + 1);
    boardRepository.save(board);
}
```

**효과:**
- 사용자당 1회만 조회수 증가
- 정확한 조회 수 추적 가능

**개선 계획:**
- Redis Set을 활용한 조회 기록 관리
- TTL 24시간으로 자동 만료

---

### 2. 좋아요/싫어요 중복 방지

**문제 상황:**
- 동시에 같은 게시글에 좋아요를 클릭하면 중복 반응 발생
- 더블 클릭 시 반응이 2개 생성됨

**해결 코드:**
```java
// ReactionService.java
@Transactional
public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) {
    Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new IllegalArgumentException("Board not found"));
    Users user = usersRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // 기존 반응 확인
    Optional<BoardReaction> existing = boardReactionRepository.findByBoardAndUser(board, user);
    ReactionType previousReactionType = null;

    if (existing.isPresent() && existing.get().getReactionType() == reactionType) {
        // 같은 반응을 다시 클릭하면 삭제 (토글)
        previousReactionType = existing.get().getReactionType();
        boardReactionRepository.delete(existing.get());
    } else if (existing.isPresent()) {
        // 반응 타입 변경 (예: 좋아요 -> 싫어요)
        previousReactionType = existing.get().getReactionType();
        BoardReaction reaction = existing.get();
        reaction.setReactionType(reactionType);
        boardReactionRepository.save(reaction);
        board.setLastReactionAt(LocalDateTime.now());
    } else {
        // 새로운 반응 추가
        BoardReaction reaction = BoardReaction.builder()
                .board(board)
                .user(user)
                .reactionType(reactionType)
                .build();
        boardReactionRepository.save(reaction);
        board.setLastReactionAt(LocalDateTime.now());
    }

    // likeCount 실시간 업데이트
    updateBoardLikeCount(board, previousReactionType, reactionType);
    boardRepository.save(board);

    return buildBoardSummary(board, user);
}
```

**DB 제약조건:**
```sql
-- 중복 반응 방지: Unique 제약조건
CREATE UNIQUE INDEX uk_board_reaction_unique 
ON board_reaction(board_idx, user_idx);
```

**효과:**
- DB 레벨에서 중복 방지
- 동시 클릭 시 하나만 저장됨

**예외 처리:**
```java
try {
    boardReactionRepository.save(reaction);
} catch (DataIntegrityViolationException e) {
    // Unique 제약 위반 → 중복 클릭 무시
    log.warn("Duplicate reaction attempt: board={}, user={}", boardId, userId);
}
```

---

### 3. 댓글 수 동기화 문제 (개선 필요)

**현재 구현:**
```java
// CommentService.java
@Transactional
public CommentDTO addComment(Long boardId, CommentDTO dto) {
    // ...
    incrementBoardCommentCount(board);  // 메모리에서 증가
    boardRepository.save(board);
    // ...
}

private void incrementBoardCommentCount(Board board) {
    Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
    board.setCommentCount(currentCount + 1);  // Lost Update 가능성!
}
```

**문제점:**
- 동시에 댓글을 작성하면 둘 다 같은 `commentCount`를 읽음
- 둘 다 +1을 해서 저장하면 실제로는 +2가 되어야 하는데 +1만 됨 (Lost Update)

**개선 방안:**
```java
// Repository에 추가
@Modifying
@Query("UPDATE Board b SET b.commentCount = b.commentCount + 1 WHERE b.idx = :boardId")
void incrementCommentCount(@Param("boardId") Long boardId);

// Service에서 사용
@Transactional
public CommentDTO addComment(Long boardId, CommentDTO dto) {
    // ...
    Comment saved = commentRepository.save(comment);
    
    // 원자적 증가 (DB 레벨에서 처리)
    boardRepository.incrementCommentCount(boardId);
    
    return mapWithReactionCounts(saved);
}
```

**효과:**
- DB 레벨에서 원자적 연산
- Lost Update 완전 방지

---

### 4. 펫케어 지원 승인 동시성 문제 (개선 필요)

**문제 상황:**
- 펫케어 요청에 여러 지원이 있을 때
- 요청자가 2명의 지원을 동시에 승인하려 하면 둘 다 승인될 수 있음
- 비즈니스 규칙: 1개 요청당 1명만 승인 가능

**현재 구현 (문제 있음):**
```java
// CareRequestService.java (가정)
@Transactional
public void approveApplication(long requestId, long applicationId) {
    CareRequest request = careRequestRepository.findById(requestId).orElseThrow();
    
    // 이미 승인된 지원 확인
    boolean hasApproved = applicationRepository.existsByRequestAndStatus(
        request, CareApplicationStatus.APPROVED
    );
    
    if (hasApproved) {
        throw new IllegalStateException("이미 승인된 지원이 있습니다.");
    }
    
    // 승인 처리
    CareApplication application = applicationRepository.findById(applicationId).orElseThrow();
    application.setStatus(CareApplicationStatus.APPROVED);
    applicationRepository.save(application);
    
    request.setStatus(CareRequestStatus.IN_PROGRESS);
    careRequestRepository.save(request);
}
```

**문제점:**
- 두 요청이 동시에 `hasApproved`를 확인하면 둘 다 false
- 둘 다 승인 처리 진행

**개선 방안 1: 비관적 락**
```java
// Repository에 추가
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT cr FROM CareRequest cr WHERE cr.idx = :id")
CareRequest findByIdWithLock(@Param("id") Long id);

// Service에서 사용
@Transactional
public void approveApplication(long requestId, long applicationId) {
    // 락 획득 (다른 트랜잭션은 대기)
    CareRequest request = careRequestRepository.findByIdWithLock(requestId);
    
    boolean hasApproved = applicationRepository.existsByRequestAndStatus(
        request, CareApplicationStatus.APPROVED
    );
    
    if (hasApproved) {
        throw new IllegalStateException("이미 승인된 지원이 있습니다.");
    }
    
    // 승인 처리
    CareApplication application = applicationRepository.findById(applicationId).orElseThrow();
    application.setStatus(CareApplicationStatus.APPROVED);
    applicationRepository.save(application);
    
    request.setStatus(CareRequestStatus.IN_PROGRESS);
    careRequestRepository.save(request);
}
```

**개선 방안 2: Unique 제약조건 (부분 인덱스)**
```sql
-- MySQL 8.0.13+ 또는 PostgreSQL
CREATE UNIQUE INDEX idx_unique_approved_application 
ON care_application(care_request_idx) 
WHERE status = 'APPROVED';
```

```java
@Transactional
public void approveApplication(long requestId, long applicationId) {
    try {
        CareApplication application = applicationRepository.findById(applicationId).orElseThrow();
        application.setStatus(CareApplicationStatus.APPROVED);
        applicationRepository.save(application);  // Unique 제약 체크
        
        CareRequest request = application.getCareRequest();
        request.setStatus(CareRequestStatus.IN_PROGRESS);
        careRequestRepository.save(request);
        
    } catch (DataIntegrityViolationException e) {
        throw new IllegalStateException("이미 승인된 지원이 있습니다.");
    }
}
```

---

## 📊 트랜잭션 격리 수준

### 현재 설정
- **기본값**: `REPEATABLE_READ` (MySQL InnoDB)
- **특징**: 같은 트랜잭션 내에서 같은 데이터를 여러 번 읽어도 같은 값
- **Phantom Read 방지**: InnoDB의 Next-Key Lock으로 방지

### 필요 시 명시적 설정
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void someMethod() {
    // 커밋된 데이터만 읽음
}

@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalMethod() {
    // 가장 높은 격리 수준 (성능 저하)
}
```

---

## 🎯 요약

### 구현 완료 ✅
1. **트랜잭션 경계 명확화**: Service 레이어에서 트랜잭션 관리
2. **읽기 전용 최적화**: `@Transactional(readOnly = true)` 기본값 사용
3. **조회수 중복 방지**: BoardViewLog를 통한 사용자별 1회 제한
4. **반응 중복 방지**: Unique 제약조건으로 DB 레벨 보장

### 개선 필요 🔄
1. **댓글 수 동기화**: UPDATE 쿼리로 원자적 증가 필요
2. **펫케어 지원 승인**: 비관적 락 또는 Unique 제약조건 필요
3. **모임 참여 인원**: 낙관적/비관적 락 적용 필요

### 성능 영향
- **읽기 전용 트랜잭션**: 쓰기 락 미발생으로 조회 성능 향상
- **트랜잭션 범위 최소화**: 불필요한 락 유지 시간 감소

