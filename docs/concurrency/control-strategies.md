# 동시성 제어 전략

## 개요

Petory 백엔드에서 발생할 수 있는 동시성 문제와 해결 방안을 정리한 문서입니다.

---

## 1. 게시글 조회수 동시성 문제

### 문제 상황

**시나리오:**
1. 사용자 A와 B가 동시에 같은 게시글 조회
2. 둘 다 현재 조회수 100을 읽음
3. 둘 다 101로 증가시켜 저장
4. **결과: 조회수 101 (기대값: 102)**

### Lost Update 문제

```java
// 문제 코드
@Transactional
public BoardDTO getBoard(long idx) {
    Board board = boardRepository.findById(idx).orElseThrow();
    
    // 동시 실행 시 같은 값을 읽음
    int currentCount = board.getViewCount();
    
    // 같은 값에 +1
    board.setViewCount(currentCount + 1);
    
    boardRepository.save(board);  // Lost Update 발생!
    
    return converter.toDTO(board);
}
```

### 해결책 1: BoardViewLog (현재 방식)

**원리:** 사용자당 1회만 조회수 증가

```java
@Transactional
public BoardDTO getBoard(long idx, Long viewerId) {
    Board board = boardRepository.findById(idx).orElseThrow();
    
    if (shouldIncrementView(board, viewerId)) {
        incrementViewCount(board);
    }
    
    return converter.toDTO(board);
}

private boolean shouldIncrementView(Board board, Long viewerId) {
    if (viewerId == null) {
        return true;
    }
    
    Users viewer = usersRepository.findById(viewerId).orElse(null);
    if (viewer == null) {
        return true;
    }
    
    // 이미 조회한 기록이 있는지 확인
    boolean alreadyViewed = viewLogRepository.existsByBoardAndUser(board, viewer);
    if (alreadyViewed) {
        return false;  // 조회수 증가 안 함
    }
    
    // 조회 기록 추가
    BoardViewLog log = BoardViewLog.builder()
        .board(board)
        .user(viewer)
        .build();
    viewLogRepository.save(log);
    
    return true;
}
```

**장점:**
- 정확한 조회 수 추적
- 동시성 문제 회피 (중복 조회 자체를 방지)

**단점:**
- ViewLog 테이블 크기 증가
- 추가 쿼리 발생

### 해결책 2: 낙관적 락 (Optimistic Lock)

```java
@Entity
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @Version  // 낙관적 락
    private Long version;
    
    private Integer viewCount;
}
```

```java
@Transactional
public void incrementViewCount(long boardId) {
    try {
        Board board = boardRepository.findById(boardId).orElseThrow();
        board.setViewCount(board.getViewCount() + 1);
        boardRepository.save(board);
    } catch (OptimisticLockException e) {
        // 재시도 또는 무시
        log.warn("Optimistic lock exception for board {}", boardId);
    }
}
```

**장점:**
- 구현 간단
- 충돌 빈도 낮으면 효율적

**단점:**
- 충돌 시 예외 발생 (재시도 필요)
- 조회수는 정확하지 않아도 됨 (굳이 락 필요 없음)

### 해결책 3: UPDATE 쿼리로 직접 증가

```java
@Modifying
@Query("UPDATE Board b SET b.viewCount = b.viewCount + 1 WHERE b.idx = :boardId")
void incrementViewCount(@Param("boardId") Long boardId);
```

**장점:**
- 원자적 연산 (Atomic)
- Lost Update 방지

**단점:**
- 중복 조회도 증가 (정확하지 않음)

### 해결책 4: Redis (개선안)

```java
@Service
public class ViewCountService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final BoardRepository boardRepository;
    
    // 조회수 증가 (Redis)
    public void incrementViewCount(Long boardId, Long userId) {
        String viewerKey = "board:" + boardId + ":viewers";
        String countKey = "board:" + boardId + ":viewCount";
        
        // Set에 사용자 추가 (중복 자동 제거)
        Boolean added = redisTemplate.opsForSet().add(viewerKey, userId.toString());
        
        if (Boolean.TRUE.equals(added)) {
            // 조회수 증가 (Redis)
            redisTemplate.opsForValue().increment(countKey);
            
            // TTL 24시간
            redisTemplate.expire(viewerKey, Duration.ofDays(1));
        }
    }
    
    // 주기적으로 Redis → DB 동기화
    @Scheduled(cron = "0 */10 * * * ?")  // 10분마다
    public void syncViewCountsToDB() {
        Set<String> keys = redisTemplate.keys("board:*:viewCount");
        
        for (String key : keys) {
            Long boardId = extractBoardId(key);
            String countStr = redisTemplate.opsForValue().get(key);
            
            if (countStr != null) {
                int count = Integer.parseInt(countStr);
                boardRepository.updateViewCount(boardId, count);
                
                // Redis 키 삭제
                redisTemplate.delete(key);
            }
        }
    }
}
```

**장점:**
- 빠른 속도
- 정확한 조회 수 추적
- DB 부담 감소

**단점:**
- Redis 의존성 추가
- 동기화 로직 필요

---

## 2. 좋아요/싫어요 동시성 문제

### 문제 상황

**시나리오:**
1. 사용자 A가 게시글에 좋아요 클릭
2. 동시에 사용자 A가 다시 좋아요 클릭 (더블 클릭)
3. 둘 다 "좋아요 없음" 확인
4. **결과: 좋아요 2개 생성 (기대값: 1개)**

### 해결책: Unique 제약 조건 + 예외 처리

```sql
CREATE UNIQUE INDEX idx_board_reaction_unique 
ON board_reaction(board_idx, user_idx);
```

```java
@Transactional
public void toggleBoardReaction(long boardId, long userId, ReactionType type) {
    Board board = boardRepository.findById(boardId).orElseThrow();
    Users user = usersRepository.findById(userId).orElseThrow();
    
    try {
        Optional<BoardReaction> existing = reactionRepository.findByBoardAndUser(board, user);
        
        if (existing.isPresent()) {
            if (existing.get().getType() == type) {
                // 같은 타입 → 취소
                reactionRepository.delete(existing.get());
                decrementReactionCount(board, type);
            } else {
                // 다른 타입 → 변경
                decrementReactionCount(board, existing.get().getType());
                existing.get().setType(type);
                reactionRepository.save(existing.get());
                incrementReactionCount(board, type);
            }
        } else {
            // 새로 추가
            BoardReaction reaction = BoardReaction.builder()
                .board(board)
                .user(user)
                .type(type)
                .build();
            reactionRepository.save(reaction);
            incrementReactionCount(board, type);
        }
    } catch (DataIntegrityViolationException e) {
        // Unique 제약 위반 → 중복 클릭 무시
        log.warn("Duplicate reaction attempt: board={}, user={}", boardId, userId);
    }
}
```

**장점:**
- DB 레벨에서 중복 방지
- 안전한 동시성 제어

**단점:**
- 예외 발생 오버헤드 (미미함)

---

## 3. 댓글 수 동기화 문제

### 문제 상황

**시나리오:**
1. 게시글 A의 댓글 수: 10
2. 사용자 A, B가 동시에 댓글 작성
3. 둘 다 commentCount = 10을 읽음
4. 둘 다 11로 증가시켜 저장
5. **결과: 댓글 수 11 (기대값: 12)**

### 현재 방식 (문제 있음)

```java
@Transactional
public CommentDTO createComment(CommentDTO dto) {
    Comment comment = commentRepository.save(...);
    
    Board board = comment.getBoard();
    
    // Lost Update 발생 가능!
    board.setCommentCount(board.getCommentCount() + 1);
    boardRepository.save(board);
    
    return converter.toDTO(comment);
}
```

### 해결책 1: UPDATE 쿼리로 직접 증가 (권장)

```java
// BoardRepository.java
@Modifying
@Query("UPDATE Board b SET b.commentCount = b.commentCount + 1 WHERE b.idx = :boardId")
void incrementCommentCount(@Param("boardId") Long boardId);

@Modifying
@Query("UPDATE Board b SET b.commentCount = b.commentCount - 1 WHERE b.idx = :boardId AND b.commentCount > 0")
void decrementCommentCount(@Param("boardId") Long boardId);
```

```java
// CommentService.java
@Transactional
public CommentDTO createComment(CommentDTO dto) {
    Comment comment = commentRepository.save(...);
    
    // 원자적 증가
    boardRepository.incrementCommentCount(comment.getBoard().getIdx());
    
    return converter.toDTO(comment);
}

@Transactional
public void deleteComment(long commentId) {
    Comment comment = commentRepository.findById(commentId).orElseThrow();
    
    comment.setIsDeleted(true);
    commentRepository.save(comment);
    
    // 원자적 감소
    boardRepository.decrementCommentCount(comment.getBoard().getIdx());
}
```

**장점:**
- 원자적 연산
- Lost Update 완전 방지
- 성능 좋음

### 해결책 2: 낙관적 락

```java
@Entity
public class Board {
    @Version
    private Long version;
    
    private Integer commentCount;
}
```

```java
@Transactional
public CommentDTO createComment(CommentDTO dto) {
    int maxRetries = 3;
    int attempt = 0;
    
    while (attempt < maxRetries) {
        try {
            Comment comment = commentRepository.save(...);
            
            Board board = boardRepository.findById(comment.getBoard().getIdx()).orElseThrow();
            board.setCommentCount(board.getCommentCount() + 1);
            boardRepository.save(board);
            
            return converter.toDTO(comment);
        } catch (OptimisticLockException e) {
            attempt++;
            if (attempt >= maxRetries) {
                throw new RuntimeException("Failed to create comment after retries");
            }
        }
    }
}
```

**단점:**
- 재시도 로직 복잡
- 충돌 빈번 시 성능 저하

### 해결책 3: 실시간 계산 (궁극적 해결)

```java
// 댓글 수를 저장하지 않고 매번 계산
public BoardDTO getBoard(long idx) {
    Board board = boardRepository.findById(idx).orElseThrow();
    
    // 실시간 계산
    int commentCount = commentRepository.countByBoardAndIsDeletedFalse(board);
    
    BoardDTO dto = converter.toDTO(board);
    dto.setCommentCount(commentCount);
    return dto;
}
```

**장점:**
- 항상 정확
- 동기화 문제 없음

**단점:**
- 추가 쿼리 (N+1 가능)
- 성능 저하 (캐싱으로 보완 가능)

---

## 4. 펫케어 지원 승인 동시성 문제

### 문제 상황

**시나리오:**
1. 펫케어 요청 A에 사용자 B, C가 지원
2. 요청자가 B와 C를 동시에 승인 시도
3. **결과: 2명 모두 승인 (기대값: 1명만 승인)**

### 비즈니스 규칙

- **1개 요청당 1명만 승인 가능**
- 승인 후 요청 상태는 IN_PROGRESS로 변경

### 해결책 1: 비관적 락 (Pessimistic Lock)

```java
// CareRequestRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT cr FROM CareRequest cr WHERE cr.idx = :id")
CareRequest findByIdWithLock(@Param("id") Long id);
```

```java
// CareRequestService.java
@Transactional
public void approveApplication(long requestId, long applicationId) {
    // 락 획득 (다른 트랜잭션은 대기)
    CareRequest request = careRequestRepository.findByIdWithLock(requestId);
    
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
    
    // 요청 상태 변경
    request.setStatus(CareRequestStatus.IN_PROGRESS);
    careRequestRepository.save(request);
}
```

**장점:**
- 확실한 동시성 제어
- 구현 간단

**단점:**
- 대기 시간 발생 (성능 저하)
- 데드락 가능성

### 해결책 2: 낙관적 락

```java
@Entity
public class CareRequest {
    @Version
    private Long version;
}
```

```java
@Transactional
public void approveApplication(long requestId, long applicationId) {
    try {
        CareRequest request = careRequestRepository.findById(requestId).orElseThrow();
        
        boolean hasApproved = applicationRepository.existsByRequestAndStatus(
            request, CareApplicationStatus.APPROVED
        );
        
        if (hasApproved) {
            throw new IllegalStateException("이미 승인된 지원이 있습니다.");
        }
        
        CareApplication application = applicationRepository.findById(applicationId).orElseThrow();
        application.setStatus(CareApplicationStatus.APPROVED);
        applicationRepository.save(application);
        
        request.setStatus(CareRequestStatus.IN_PROGRESS);
        careRequestRepository.save(request);  // 버전 체크
        
    } catch (OptimisticLockException e) {
        throw new IllegalStateException("다른 사용자가 먼저 승인했습니다.");
    }
}
```

**장점:**
- 성능 좋음 (락 대기 없음)

**단점:**
- 충돌 시 예외 처리 필요

### 해결책 3: Unique 제약 조건 (부분 인덱스)

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

**장점:**
- DB 레벨 보장
- 가장 안전

**단점:**
- DB 종속적 (MySQL, PostgreSQL)

---

## 5. 모임 참여 동시성 문제

### 문제 상황

**시나리오:**
1. 모임 최대 인원: 10명, 현재 인원: 9명
2. 사용자 A, B가 동시에 참여 시도
3. 둘 다 "9명" 확인
4. **결과: 11명 (기대값: 10명)**

### 해결책 1: 비관적 락

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m WHERE m.idx = :id")
Meetup findByIdWithLock(@Param("id") Long id);
```

```java
@Transactional
public void joinMeetup(long meetupId, long userId) {
    Meetup meetup = meetupRepository.findByIdWithLock(meetupId);
    Users user = usersRepository.findById(userId).orElseThrow();
    
    // 인원 체크
    if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
        throw new IllegalStateException("모임 인원이 가득 찼습니다.");
    }
    
    // 참여 추가
    MeetupParticipants participant = MeetupParticipants.builder()
        .meetup(meetup)
        .user(user)
        .build();
    participantRepository.save(participant);
    
    // 인원 증가
    meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
    meetupRepository.save(meetup);
}
```

### 해결책 2: 낙관적 락

```java
@Entity
public class Meetup {
    @Version
    private Long version;
    
    private Integer currentParticipants;
    private Integer maxParticipants;
}
```

```java
@Transactional
public void joinMeetup(long meetupId, long userId) {
    int maxRetries = 5;
    int attempt = 0;
    
    while (attempt < maxRetries) {
        try {
            Meetup meetup = meetupRepository.findById(meetupId).orElseThrow();
            
            if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
                throw new IllegalStateException("모임 인원이 가득 찼습니다.");
            }
            
            // 참여 추가
            MeetupParticipants participant = ...;
            participantRepository.save(participant);
            
            // 인원 증가
            meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
            meetupRepository.save(meetup);  // 버전 체크
            
            return;
            
        } catch (OptimisticLockException e) {
            attempt++;
            if (attempt >= maxRetries) {
                throw new RuntimeException("모임 참여에 실패했습니다. 다시 시도해주세요.");
            }
            
            // 짧은 대기 후 재시도
            Thread.sleep(100);
        }
    }
}
```

### 해결책 3: UPDATE 쿼리 + 조건

```java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupId AND m.currentParticipants < m.maxParticipants")
int incrementParticipants(@Param("meetupId") Long meetupId);
```

```java
@Transactional
public void joinMeetup(long meetupId, long userId) {
    // 참여 추가
    MeetupParticipants participant = ...;
    participantRepository.save(participant);
    
    // 인원 증가 (조건부)
    int updated = meetupRepository.incrementParticipants(meetupId);
    
    if (updated == 0) {
        // 인원 초과
        throw new IllegalStateException("모임 인원이 가득 찼습니다.");
    }
}
```

**장점:**
- 원자적 연산
- 조건 체크 포함

**단점:**
- 참여 기록이 남았지만 카운트가 증가하지 않은 경우 처리 필요

---

## 6. 스케줄러 중복 실행 방지

### 문제 상황

**시나리오:**
- 여러 서버 인스턴스에서 같은 스케줄러 동시 실행
- 중복 작업 수행

### 해결책 1: ShedLock

**의존성 추가:**
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.7.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>5.7.0</version>
</dependency>
```

**테이블 생성:**
```sql
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

**설정:**
```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {
    
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
```

**사용:**
```java
@Scheduled(cron = "0 0 * * * ?")
@SchedulerLock(
    name = "updateExpiredCareRequests",
    lockAtLeastFor = "PT5M",   // 최소 5분 락 유지
    lockAtMostFor = "PT10M"     // 최대 10분 (비정상 종료 대비)
)
public void updateExpiredCareRequests() {
    // 여러 인스턴스 중 1개만 실행
    log.info("펫케어 요청 상태 자동 업데이트 시작");
    // ...
}
```

**장점:**
- 분산 환경에서 안전
- 구현 간단

### 해결책 2: Redis 분산 락

```java
@Component
public class RedisLockScheduler {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledTask() {
        String lockKey = "scheduler:updateExpiredCareRequests";
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 락 획득 (TTL 10분)
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMinutes(10));
            
            if (Boolean.TRUE.equals(acquired)) {
                // 작업 수행
                updateExpiredCareRequests();
            } else {
                log.info("다른 인스턴스에서 이미 실행 중");
            }
            
        } finally {
            // 락 해제 (본인이 획득한 락만)
            String currentValue = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            }
        }
    }
}
```

---

## 트랜잭션 격리 수준

### MySQL 기본값: REPEATABLE READ

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)  // 기본값
```

**특징:**
- 같은 트랜잭션 내에서 같은 데이터를 여러 번 읽어도 같은 값
- Phantom Read 방지 (InnoDB)

### 격리 수준 변경

```java
// READ_COMMITTED: 커밋된 데이터만 읽음
@Transactional(isolation = Isolation.READ_COMMITTED)
public void someMethod() {
    // ...
}

// SERIALIZABLE: 가장 높은 격리 수준 (성능 저하)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalMethod() {
    // ...
}
```

---

## 동시성 제어 전략 선택 가이드

| 상황 | 추천 전략 | 이유 |
|------|----------|------|
| 조회수 증가 | Unique 제약 + ViewLog | 정확도 중요, 충돌 빈도 낮음 |
| 좋아요/싫어요 | Unique 제약 + 예외 처리 | DB 레벨 보장, 구현 간단 |
| 댓글 수 동기화 | UPDATE 쿼리 | 원자적 연산, 성능 좋음 |
| 펫케어 지원 승인 | 비관적 락 | 1명만 승인, 충돌 시 대기 필요 |
| 모임 인원 관리 | 낙관적 락 + 재시도 | 충돌 빈도 낮음, 성능 우선 |
| 스케줄러 중복 방지 | ShedLock 또는 Redis | 분산 환경 대응 |

---

## 성능 테스트

### JMeter를 이용한 동시성 테스트

**시나리오: 좋아요 동시 클릭**
```
1. 100명의 사용자가 동시에
2. 같은 게시글에 좋아요 클릭
3. 기대값: 좋아요 100개
```

**검증:**
```sql
SELECT COUNT(*) FROM board_reaction WHERE board_idx = 1;
-- 결과: 100 (성공)
```

### 부하 테스트

```bash
# Apache Bench 예시
ab -n 1000 -c 100 http://localhost:8080/api/boards/1
```

---

## 동시성 문제 디버깅

### 1. 로그 추가

```java
@Transactional
public void approveApplication(long requestId, long applicationId) {
    log.info("[LOCK] Acquiring lock for request {}", requestId);
    CareRequest request = careRequestRepository.findByIdWithLock(requestId);
    log.info("[LOCK] Lock acquired for request {}", requestId);
    
    // ...
    
    log.info("[LOCK] Releasing lock for request {}", requestId);
}
```

### 2. 트랜잭션 모니터링

```sql
-- MySQL: 현재 트랜잭션 목록
SELECT * FROM information_schema.innodb_trx;

-- 락 대기 상황
SELECT * FROM information_schema.innodb_locks;
```

### 3. 데드락 감지

```sql
-- 데드락 로그 확인
SHOW ENGINE INNODB STATUS;
```

---

## 체크리스트

- [ ] 조회수 증가: 중복 방지 메커니즘
- [ ] 반응 처리: Unique 제약 조건
- [ ] 댓글 수: UPDATE 쿼리로 원자적 증가
- [ ] 지원 승인: 1명만 승인 보장
- [ ] 모임 참여: 최대 인원 체크
- [ ] 스케줄러: 중복 실행 방지
- [ ] 격리 수준: 적절한 설정
- [ ] 성능 테스트: 동시성 시나리오 검증

