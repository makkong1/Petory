# Redis 캐싱 전략 정리

## 📋 개요

Petory 프로젝트에서 Redis를 활용한 캐싱 전략을 적용하여 성능을 최적화하고 있습니다.

## 🎯 적용된 캐싱 전략

### 1. 게시글 목록 캐싱 (`boardList`)

**캐시 키**: `boardList:{category}` 또는 `boardList:ALL`

**적용 메서드**:
- `BoardService.getAllBoards(String category)` - `@Cacheable` 적용

**TTL**: 10분 (RedisConfig에서 설정)

**캐시 무효화 시점**:
- ✅ 게시글 생성 시: 해당 카테고리 캐시 무효화
- ✅ 게시글 수정 시: 전체 목록 캐시 무효화 (카테고리 변경 가능하므로 안전하게)
- ✅ 게시글 삭제 시: 전체 목록 캐시 무효화
- ✅ 게시글 상태 변경 시: 전체 목록 캐시 무효화
- ✅ 게시글 복구 시: 전체 목록 캐시 무효화

**코드 예시**:
```java
@Cacheable(value = "boardList", key = "#category != null ? #category : 'ALL'")
public List<BoardDTO> getAllBoards(String category) { ... }

@CacheEvict(value = "boardList", key = "#dto.category != null ? #dto.category : 'ALL'")
public BoardDTO createBoard(BoardDTO dto) { ... }
```

---

### 2. 게시글 상세 캐싱 (`boardDetail`)

**캐시 키**: `boardDetail:{boardId}`

**적용 메서드**:
- `BoardService.getBoard(Long idx, Long viewerId)` - `@Cacheable` 적용

**TTL**: 1시간 (RedisConfig에서 설정)

**캐시 무효화 시점**:
- ✅ 게시글 수정 시: 해당 게시글 캐시 무효화
- ✅ 게시글 삭제 시: 해당 게시글 캐시 무효화
- ✅ 게시글 상태 변경 시: 해당 게시글 캐시 무효화
- ✅ 게시글 복구 시: 해당 게시글 캐시 무효화
- ✅ 댓글 추가 시: 해당 게시글 캐시 무효화 (댓글 수 포함)
- ✅ 댓글 삭제 시: 해당 게시글 캐시 무효화
- ✅ 댓글 상태 변경 시: 해당 게시글 캐시 무효화
- ✅ 좋아요/싫어요 반응 시: 해당 게시글 캐시 무효화 (좋아요 수 포함)

**코드 예시**:
```java
@Cacheable(value = "boardDetail", key = "#idx")
public BoardDTO getBoard(Long idx, Long viewerId) { ... }

@Caching(evict = {
    @CacheEvict(value = "boardDetail", key = "#idx"),
    @CacheEvict(value = "boardList", allEntries = true)
})
public BoardDTO updateBoard(Long idx, BoardDTO dto) { ... }
```

---

### 3. 좋아요/싫어요 반응 캐싱

**전략**: Write-Through 방식 (즉시 캐시 무효화)

**적용 메서드**:
- `ReactionService.reactToBoard()` - `@CacheEvict` 적용

**동작 방식**:
- 좋아요/싫어요 반응 시 DB에 즉시 반영
- 게시글 상세 캐시를 무효화하여 다음 조회 시 최신 데이터 반영

**코드 예시**:
```java
@CacheEvict(value = "boardDetail", key = "#boardId")
public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) { ... }
```

---

## 🔄 캐시 무효화 흐름도

```
게시글 생성/수정/삭제
    ↓
@CacheEvict 실행
    ↓
boardList 캐시 무효화 (해당 카테고리 또는 전체)
boardDetail 캐시 무효화 (해당 게시글)
    ↓
다음 조회 시 DB에서 최신 데이터 조회 후 캐시 저장
```

```
댓글 추가/삭제
    ↓
@CacheEvict 실행
    ↓
boardDetail 캐시 무효화 (해당 게시글)
    ↓
다음 조회 시 DB에서 최신 데이터 조회 후 캐시 저장
```

```
좋아요/싫어요 반응
    ↓
@CacheEvict 실행
    ↓
boardDetail 캐시 무효화 (해당 게시글)
    ↓
다음 조회 시 DB에서 최신 데이터 조회 후 캐시 저장
```

---

## 📝 적용된 파일 목록

### BoardService.java
- ✅ `getAllBoards()` - `@Cacheable` 적용
- ✅ `getBoard()` - `@Cacheable` 적용
- ✅ `createBoard()` - `@CacheEvict` 적용
- ✅ `updateBoard()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `deleteBoard()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `updateBoardStatus()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `restoreBoard()` - `@CacheEvict` 적용 (Caching 사용)

### CommentService.java
- ✅ `addComment()` - `@CacheEvict` 적용
- ✅ `deleteComment()` - `@CacheEvict` 적용
- ✅ `updateCommentStatus()` - `@CacheEvict` 적용
- ✅ `restoreComment()` - `@CacheEvict` 적용

### ReactionService.java
- ✅ `reactToBoard()` - `@CacheEvict` 적용

---

## ⚙️ Redis 설정 (RedisConfig.java)

### 캐시 TTL 설정
- **boardList**: 10분
- **boardDetail**: 1시간
- **user**: 1시간
- **기본**: 30분

### RedisTemplate 설정
- `customStringRedisTemplate`: Refresh Token, 블랙리스트용
- `objectRedisTemplate`: 게시글 캐싱, 사용자 정보 캐싱용
- `notificationRedisTemplate`: 알림 리스트용
- `reactionCountRedisTemplate`: 좋아요/싫어요 배치 동기화용

---

## 🎯 캐시 무효화 전략 요약

### 1. 게시글 목록 캐싱
- **생성**: 해당 카테고리 캐시만 무효화
- **수정/삭제**: 전체 목록 캐시 무효화 (카테고리 변경 가능성 고려)

### 2. 게시글 상세 캐싱
- **게시글 변경**: 해당 게시글 캐시만 무효화
- **댓글 변경**: 해당 게시글 캐시 무효화 (댓글 수 포함)
- **반응 변경**: 해당 게시글 캐시 무효화 (좋아요 수 포함)

### 3. 트랜잭션 고려사항
- `@CacheEvict`는 기본적으로 트랜잭션 커밋 후 실행 (`beforeInvocation = false`)
- 트랜잭션 롤백 시 캐시 무효화도 롤백됨

---

## 🚀 성능 개선 효과

### Before (캐싱 없음)
- 게시글 목록 조회: 매번 DB 쿼리 실행
- 게시글 상세 조회: 매번 DB 쿼리 + 조인 쿼리 실행
- 좋아요/싫어요 카운트: 매번 집계 쿼리 실행

### After (캐싱 적용)
- 게시글 목록 조회: Redis에서 즉시 반환 (10분간)
- 게시글 상세 조회: Redis에서 즉시 반환 (1시간간)
- 데이터 변경 시에만 캐시 무효화 후 재조회

---

## ⚠️ 주의사항

1. **카테고리 변경 시**: 게시글 수정 시 카테고리가 변경될 수 있으므로 안전하게 전체 목록 캐시를 무효화합니다.

2. **댓글 수 포함**: 게시글 상세에 댓글 수가 포함되므로 댓글 추가/삭제 시 게시글 상세 캐시를 무효화합니다.

3. **좋아요 수 포함**: 게시글 상세에 좋아요/싫어요 수가 포함되므로 반응 변경 시 게시글 상세 캐시를 무효화합니다.

4. **TTL 안전망**: 캐시 무효화가 실패하더라도 TTL로 인해 일정 시간 후 자동으로 만료됩니다.

---

## 📚 참고 자료

- Spring Cache Abstraction: https://docs.spring.io/spring-framework/reference/integration/cache.html
- Redis Cache Configuration: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

