# 성능 최적화 가이드

## 개요

Petory 백엔드에서 적용된 성능 최적화 기법과 추가 개선 방안을 정리한 문서입니다.

---

## 1. N+1 문제 해결

### 문제 상황

#### Case 1: 게시글 목록 조회 시 반응 정보

**문제 코드:**
```java
public List<BoardDTO> getAllBoards() {
    List<Board> boards = boardRepository.findAll();
    
    return boards.stream()
        .map(board -> {
            BoardDTO dto = converter.toDTO(board);
            
            // N+1 발생! 각 게시글마다 개별 쿼리
            long likeCount = reactionRepository.countByBoardAndType(board, LIKE);
            long dislikeCount = reactionRepository.countByBoardAndType(board, DISLIKE);
            
            dto.setLikes(likeCount);
            dto.setDislikes(dislikeCount);
            return dto;
        })
        .collect(Collectors.toList());
}
```

**쿼리 수:** 1 (게시글) + N×2 (좋아요/싫어요) = **2N+1개**

#### 해결책: 배치 조회 (IN 절)

**개선 코드:**
```java
// BoardService.java
private List<BoardDTO> mapBoardsWithReactionsBatch(List<Board> boards) {
    if (boards.isEmpty()) {
        return new ArrayList<>();
    }
    
    // 1. 게시글 ID 목록 추출
    List<Long> boardIds = boards.stream()
        .map(Board::getIdx)
        .collect(Collectors.toList());
    
    // 2. 좋아요/싫어요 카운트 배치 조회 (IN 절)
    Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(boardIds);
    
    // 3. 게시글 DTO 변환 및 반응 정보 매핑
    return boards.stream()
        .map(board -> {
            BoardDTO dto = converter.toDTO(board);
            Map<ReactionType, Long> counts = reactionCountsMap.getOrDefault(board.getIdx(), new HashMap<>());
            dto.setLikes(Math.toIntExact(counts.getOrDefault(LIKE, 0L)));
            dto.setDislikes(Math.toIntExact(counts.getOrDefault(DISLIKE, 0L)));
            return dto;
        })
        .collect(Collectors.toList());
}

private Map<Long, Map<ReactionType, Long>> getReactionCountsBatch(List<Long> boardIds) {
    final int BATCH_SIZE = 500;  // IN 절 크기 제한
    Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();
    
    // IN 절을 500개 단위로 나누어 조회
    for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, boardIds.size());
        List<Long> batch = boardIds.subList(i, end);
        
        List<Object[]> results = reactionRepository.countByBoardsGroupByReactionType(batch);
        
        for (Object[] result : results) {
            Long boardId = ((Number) result[0]).longValue();
            ReactionType type = (ReactionType) result[1];
            Long count = ((Number) result[2]).longValue();
            
            countsMap.computeIfAbsent(boardId, k -> new HashMap<>()).put(type, count);
        }
    }
    
    return countsMap;
}
```

**Repository 쿼리:**
```java
// BoardReactionRepository.java
@Query("SELECT br.board.idx, br.type, COUNT(br) " +
       "FROM BoardReaction br " +
       "WHERE br.board.idx IN :boardIds " +
       "GROUP BY br.board.idx, br.type")
List<Object[]> countByBoardsGroupByReactionType(@Param("boardIds") List<Long> boardIds);
```

**쿼리 수:** 1 (게시글) + ⌈N/500⌉ (반응) = **최대 3개** (1000개 게시글 기준)

**효과:**
- 1000개 게시글: 2001 쿼리 → 3 쿼리 (**99.8% 감소**)
- 조회 시간: ~30초 → ~0.3초

---

### Case 2: 게시글 상세 조회 시 작성자 정보

**문제 코드:**
```java
public BoardDTO getBoard(long id) {
    Board board = boardRepository.findById(id)
        .orElseThrow();
    
    // LAZY 로딩으로 인한 추가 쿼리
    String username = board.getUser().getUsername();  // 쿼리 발생
    
    return converter.toDTO(board);
}
```

**해결책 1: Fetch Join**
```java
// BoardRepository.java
@Query("SELECT b FROM Board b " +
       "JOIN FETCH b.user " +
       "WHERE b.idx = :id")
Optional<Board> findByIdWithUser(@Param("id") Long id);
```

**해결책 2: EntityGraph**
```java
@EntityGraph(attributePaths = {"user"})
@Query("SELECT b FROM Board b WHERE b.idx = :id")
Optional<Board> findByIdWithUser(@Param("id") Long id);
```

**해결책 3: Hibernate Batch Fetch Size (전역 설정)**
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

---

### Case 3: 댓글 목록 조회 시 작성자 정보

**문제:**
- 댓글 100개 조회 시 작성자 정보도 100번 쿼리

**해결책: Fetch Join**
```java
@Query("SELECT c FROM Comment c " +
       "JOIN FETCH c.user " +
       "WHERE c.board.idx = :boardId AND c.isDeleted = false " +
       "ORDER BY c.createdAt ASC")
List<Comment> findByBoardWithUser(@Param("boardId") Long boardId);
```

---

## 2. 인덱싱 전략

### 단일 컬럼 인덱스

```sql
-- 사용자 조회
CREATE UNIQUE INDEX idx_users_id ON users(id);
CREATE UNIQUE INDEX idx_users_email ON users(email);

-- 게시글 작성일 정렬
CREATE INDEX idx_board_created_at ON board(created_at DESC);

-- 외래 키
CREATE INDEX idx_board_user ON board(user_idx);
CREATE INDEX idx_comment_board ON comment(board_idx);
```

### 복합 인덱스

#### 1. 게시글 카테고리별 조회
```sql
CREATE INDEX idx_board_category_deleted_created 
ON board(category, is_deleted, created_at DESC);
```

**쿼리:**
```sql
SELECT * FROM board 
WHERE category = '자유' 
  AND is_deleted = false 
ORDER BY created_at DESC;
```

**인덱스 활용:**
- `category` → 필터
- `is_deleted` → 필터
- `created_at DESC` → 정렬 (인덱스만으로 처리, FileSort 없음)

#### 2. 사용자별 게시글 조회
```sql
CREATE INDEX idx_board_user_deleted_created 
ON board(user_idx, is_deleted, created_at DESC);
```

#### 3. 펫케어 요청 상태별 조회
```sql
CREATE INDEX idx_care_request_status_deleted_date 
ON carerequest(status, is_deleted, date DESC);
```

#### 4. 반응 집계
```sql
CREATE INDEX idx_board_reaction_board_type 
ON board_reaction(board_idx, reaction_type);
```

### 커버링 인덱스

**개념:** 쿼리에 필요한 모든 컬럼을 인덱스에 포함

**예시:**
```sql
-- 게시글 목록 조회 시 필요한 컬럼만
CREATE INDEX idx_board_list_covering 
ON board(category, is_deleted, created_at DESC, idx, title, user_idx, view_count, like_count);
```

**장점:**
- 테이블 접근 없이 인덱스만으로 쿼리 완성
- I/O 대폭 감소

**단점:**
- 인덱스 크기 증가
- 쓰기 성능 저하

**사용 시기:**
- 조회 빈도가 매우 높은 쿼리
- 쓰기 대비 읽기 비율이 높을 때 (10:1 이상)

### Full-Text 인덱스 (검색)

```sql
CREATE FULLTEXT INDEX idx_board_fulltext 
ON board(title, content);
```

**쿼리:**
```sql
SELECT * FROM board 
WHERE MATCH(title, content) AGAINST('반려동물' IN NATURAL LANGUAGE MODE);
```

**장점:**
- 빠른 텍스트 검색
- 형태소 분석 지원 (한글 제한적)

**한계:**
- 복잡한 검색은 ElasticSearch 권장

---

## 3. 쿼리 최적화

### EXPLAIN 분석

```sql
EXPLAIN 
SELECT b.*, u.username 
FROM board b 
JOIN users u ON b.user_idx = u.idx 
WHERE b.category = '자유' 
  AND b.is_deleted = false 
ORDER BY b.created_at DESC 
LIMIT 20;
```

**확인 사항:**
- `type`: ALL(테이블 스캔) → ref, range, index (인덱스 사용)
- `key`: 사용된 인덱스
- `rows`: 검사한 행 수 (적을수록 좋음)
- `Extra`: Using filesort, Using temporary (피해야 함)

### SELECT 최적화

**나쁜 예:**
```java
@Query("SELECT b FROM Board b WHERE b.category = :category")
List<Board> findByCategory(@Param("category") String category);
```
→ 모든 컬럼 조회 (불필요한 데이터)

**좋은 예: DTO Projection**
```java
@Query("SELECT new com.linkup.Petory.dto.BoardSummaryDTO(" +
       "b.idx, b.title, b.user.username, b.createdAt) " +
       "FROM Board b WHERE b.category = :category")
List<BoardSummaryDTO> findSummaryByCategory(@Param("category") String category);
```
→ 필요한 컬럼만 조회

### JOIN 최적화

**나쁜 예: N+1 발생**
```java
List<Board> boards = boardRepository.findAll();
boards.forEach(b -> b.getUser().getUsername());  // N번 쿼리
```

**좋은 예: Fetch Join**
```java
@Query("SELECT b FROM Board b JOIN FETCH b.user")
List<Board> findAllWithUser();
```

**주의:** OneToMany + Fetch Join + 페이징 = 메모리 페이징
```java
// 경고 발생: HHH000104: firstResult/maxResults specified with collection fetch; applying in memory
@Query("SELECT b FROM Board b JOIN FETCH b.comments")
Page<Board> findAllWithComments(Pageable pageable);
```

**해결:** 
1. Batch Fetch Size 사용
2. 별도 쿼리로 댓글 조회

---

## 4. 캐싱 전략

### Spring Cache 적용

#### 게시글 상세 캐싱

```java
@Service
@RequiredArgsConstructor
public class BoardService {
    
    @Cacheable(value = "boardDetail", key = "#idx")
    public BoardDTO getBoard(long idx, Long viewerId) {
        Board board = boardRepository.findById(idx).orElseThrow();
        // ...
        return mapWithReactions(board);
    }
    
    @CacheEvict(value = "boardDetail", key = "#idx")
    public BoardDTO updateBoard(long idx, BoardDTO dto) {
        // ...
    }
    
    @CacheEvict(value = "boardDetail", key = "#idx")
    public void deleteBoard(long idx) {
        // ...
    }
}
```

#### 인기글 목록 캐싱

```java
@Cacheable(value = "popularBoards", key = "#periodType + '_' + #limit")
public List<BoardDTO> getPopularBoards(PopularityPeriodType periodType, int limit) {
    // 복잡한 계산 또는 스냅샷 조회
    return boardPopularityRepository.findTopByPeriodType(periodType, limit);
}
```

### 캐시 무효화 전략

#### 1. 특정 키 무효화
```java
@CacheEvict(value = "boardDetail", key = "#idx")
```

#### 2. 전체 캐시 무효화
```java
@CacheEvict(value = "boardList", allEntries = true)
```

#### 3. 여러 캐시 무효화
```java
@Caching(evict = {
    @CacheEvict(value = "boardDetail", key = "#idx"),
    @CacheEvict(value = "boardList", allEntries = true)
})
```

### Redis 캐시 (개선안)

**현재:** Spring Cache (로컬 메모리)
**문제:** 
- 여러 서버 인스턴스 시 캐시 불일치
- 서버 재시작 시 캐시 소실

**개선:**
```yaml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
```

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))  // TTL 30분
            .disableCachingNullValues();
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

---

## 5. 페이징 최적화

### Offset 페이징 문제

**문제:**
```sql
SELECT * FROM board 
WHERE is_deleted = false 
ORDER BY created_at DESC 
LIMIT 1000 OFFSET 10000;  -- 10001~11000번째 조회
```

→ 10000개 행을 스캔 후 버림 (비효율)

### Cursor 페이징 (개선안)

**개념:** 마지막 조회 위치를 기억

```java
public Page<BoardDTO> getBoardsWithCursor(Long lastId, int size) {
    List<Board> boards;
    
    if (lastId == null) {
        // 첫 페이지
        boards = boardRepository.findTop(size);
    } else {
        // 다음 페이지: lastId보다 작은 ID
        boards = boardRepository.findByIdLessThan(lastId, size);
    }
    
    return new PageImpl<>(boards);
}
```

**Repository:**
```java
@Query("SELECT b FROM Board b " +
       "WHERE b.idx < :lastId AND b.isDeleted = false " +
       "ORDER BY b.idx DESC")
List<Board> findByIdLessThan(@Param("lastId") Long lastId, Pageable pageable);
```

**장점:**
- 일정한 성능 (offset 크기 무관)
- 실시간 데이터 반영

**단점:**
- 특정 페이지로 이동 불가 (1, 2, 3... 페이지 번호 없음)
- 정렬 기준이 고유 키여야 함

---

## 6. 조회수 최적화

### 현재 방식: BoardViewLog

**장점:**
- 정확한 조회 수 추적
- 중복 조회 방지

**단점:**
- ViewLog 테이블 크기 증가
- 조회 시 추가 쿼리 발생

```java
private boolean shouldIncrementView(Board board, Long viewerId) {
    boolean alreadyViewed = viewLogRepository.existsByBoardAndUser(board, viewer);
    if (alreadyViewed) {
        return false;
    }
    
    BoardViewLog log = new BoardViewLog(board, viewer);
    viewLogRepository.save(log);
    return true;
}
```

### Redis 방식 (개선안)

```java
@Service
public class ViewCountService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean shouldIncrementView(Long boardId, Long userId) {
        String key = "board:" + boardId + ":viewers";
        
        // Set에 사용자 ID 추가 (중복 자동 제거)
        Boolean added = redisTemplate.opsForSet().add(key, userId.toString());
        
        // TTL 24시간 설정
        redisTemplate.expire(key, Duration.ofDays(1));
        
        return Boolean.TRUE.equals(added);
    }
}
```

**장점:**
- 빠른 속도 (인메모리)
- DB 부담 감소
- 자동 만료 (TTL)

**단점:**
- 영구 데이터 아님 (통계용으로는 충분)

---

## 7. 인기글 스냅샷

### 문제: 실시간 인기글 계산

```java
public List<BoardDTO> getPopularBoards() {
    // 복잡한 계산: 조회수*0.1 + 좋아요*2 + 댓글*1.5
    List<Board> boards = boardRepository.findAll();
    
    return boards.stream()
        .sorted((b1, b2) -> {
            double score1 = calculatePopularityScore(b1);
            double score2 = calculatePopularityScore(b2);
            return Double.compare(score2, score1);
        })
        .limit(10)
        .map(converter::toDTO)
        .collect(Collectors.toList());
}
```

**문제:**
- 매번 전체 게시글 조회
- 복잡한 정렬
- 느린 응답 속도

### 해결: 스냅샷 미리 생성

```java
@Scheduled(cron = "0 30 18 * * ?")  // 매일 18:30
public void generateWeeklyPopularitySnapshots() {
    LocalDate today = LocalDate.now();
    LocalDate weekAgo = today.minusDays(7);
    
    // 최근 7일 게시글 조회
    List<Board> boards = boardRepository.findByCreatedAtBetween(weekAgo, today);
    
    // 인기도 점수 계산 후 스냅샷 저장
    List<BoardPopularitySnapshot> snapshots = boards.stream()
        .sorted((b1, b2) -> Double.compare(
            calculatePopularityScore(b2), 
            calculatePopularityScore(b1)
        ))
        .limit(100)
        .map(board -> BoardPopularitySnapshot.builder()
            .board(board)
            .periodType(PopularityPeriodType.WEEKLY)
            .snapshotDate(today)
            .viewCount(board.getViewCount())
            .likeCount(board.getLikeCount())
            .commentCount(board.getCommentCount())
            .popularityScore(calculatePopularityScore(board))
            .build())
        .collect(Collectors.toList());
    
    snapshotRepository.saveAll(snapshots);
}
```

**조회:**
```java
@Cacheable(value = "popularBoards", key = "#periodType")
public List<BoardDTO> getPopularBoards(PopularityPeriodType periodType) {
    LocalDate today = LocalDate.now();
    
    // 스냅샷에서 조회 (빠름!)
    List<BoardPopularitySnapshot> snapshots = snapshotRepository
        .findByPeriodTypeAndSnapshotDate(periodType, today);
    
    return snapshots.stream()
        .map(snapshot -> converter.toDTO(snapshot.getBoard()))
        .collect(Collectors.toList());
}
```

**효과:**
- 실시간 계산: ~5초 → 스냅샷 조회: ~0.01초
- DB 부담 감소
- 캐싱과 결합 시 더욱 빠름

---

## 8. 비동기 처리

### @Async 설정

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### 알림 비동기 처리

```java
@Service
public class NotificationService {
    
    @Async
    public void notifyCommentCreated(Comment comment) {
        Notification notification = Notification.builder()
            .user(comment.getBoard().getUser())
            .type(NotificationType.COMMENT)
            .title("새 댓글이 달렸습니다")
            .content(comment.getContent())
            .relatedId(comment.getIdx())
            .relatedType("COMMENT")
            .build();
        
        notificationRepository.save(notification);
    }
}
```

**효과:**
- 댓글 작성 응답 시간 감소
- 사용자 경험 향상

---

## 성능 측정 도구

### 1. Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

### 2. Hibernate Statistics

```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
```

```java
@Component
public class HibernateStatisticsLogger {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public void logStatistics() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        
        log.info("Query Count: {}", stats.getQueryExecutionCount());
        log.info("Cache Hit Ratio: {}%", stats.getSecondLevelCacheHitCount() * 100.0 / stats.getSecondLevelCachePutCount());
    }
}
```

### 3. 쿼리 로깅

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

---

## 성능 개선 체크리스트

- [ ] N+1 문제 해결 (Fetch Join, Batch Size)
- [ ] 인덱스 최적화 (복합 인덱스, 커버링 인덱스)
- [ ] 쿼리 최적화 (DTO Projection, 불필요한 JOIN 제거)
- [ ] 캐싱 적용 (Spring Cache, Redis)
- [ ] 페이징 최적화 (Cursor 페이징)
- [ ] 비동기 처리 (@Async)
- [ ] 스냅샷 생성 (복잡한 계산 미리 수행)
- [ ] Connection Pool 튜닝 (HikariCP)
- [ ] JVM 튜닝 (Heap Size, GC)
- [ ] 모니터링 (Actuator, Prometheus, Grafana)

