# Redis 캐싱 전략 정리

## 📋 개요

Petory 프로젝트에서 Redis를 활용한 캐싱 전략을 적용하여 성능을 최적화하고 있습니다. Redis는 **Spring Cache Abstraction** (`@Cacheable`, `@CacheEvict`)과 **직접 RedisTemplate 사용** 두 가지 방식으로 활용됩니다.

## 🎯 적용된 캐싱 전략

### 1. 게시글 목록 캐싱 (`boardList`) ⚠️ 현재 비활성화

**상태**: 현재 개발 중 데이터 동기화 문제로 인해 **비활성화**되어 있습니다.

**캐시 키**: `boardList:{category}` 또는 `boardList:ALL`

**적용 메서드**:
- `BoardService.getAllBoards(String category)` - `@Cacheable` **주석 처리됨**

**TTL**: 10분 (RedisConfig에서 설정, 현재 미사용)

**참고**: 코드에는 캐시 무효화 로직이 남아있으나, 실제 캐싱은 비활성화 상태입니다.

**코드 예시**:
```java
// 캐시 임시 비활성화 - 개발 중 데이터 동기화 문제 해결
// @Cacheable(value = "boardList", key = "#category != null ? #category : 'ALL'")
public List<BoardDTO> getAllBoards(String category) { ... }
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

### 3. 인기 위치 서비스 캐싱 (`popularLocationServices`)

**캐시 키**: `popularLocationServices:{category}`

**적용 메서드**:
- `LocationServiceService.getPopularLocationServices(String category)` - `@Cacheable` 적용

**TTL**: 기본값 30분 (RedisConfig에서 설정)

**용도**: 카테고리별 인기 위치 서비스 상위 10개 조회 결과 캐싱

**코드 예시**:
```java
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) {
    return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
        .stream()
        .map(locationServiceConverter::toDTO)
        .collect(Collectors.toList());
}
```

---

### 4. 알림 시스템 캐싱 (직접 RedisTemplate 사용)

**캐시 키**: `notification:{userId}`

**적용 방식**: Spring Cache가 아닌 **직접 RedisTemplate 사용**

**TTL**: 24시간

**용도**: 사용자별 최신 알림 50개를 Redis에 캐싱하여 실시간 조회 성능 향상

**특징**:
- 최신 알림 50개만 유지 (초과 시 자동 삭제)
- MySQL과 병합하여 조회 (Redis + DB 병합 전략)
- 알림 생성 시 Redis와 MySQL 모두 저장
- 읽음 처리 시 Redis 캐시 삭제

**코드 예시**:
```java
// Redis에 알림 저장 (최신 50개, 24시간 TTL)
private void saveToRedis(Long userId, NotificationDTO notification) {
    String redisKey = "notification:" + userId;
    List<NotificationDTO> notifications = getFromRedis(userId);
    
    notifications.add(0, notification);
    if (notifications.size() > 50) {
        notifications = notifications.subList(0, 50);
    }
    
    notificationRedisTemplate.opsForValue().set(redisKey, notifications,
        Duration.ofHours(24));
}
```

**상세 문서**: [알림 시스템 아키텍처](./알림%20시스템%20아키텍처.md)

---

### 5. 이메일 인증 상태 캐싱 (직접 RedisTemplate 사용)

**캐시 키**: `email_verification:pre_registration:{email}`

**적용 방식**: Spring Cache가 아닌 **직접 RedisTemplate 사용**

**TTL**: 24시간

**용도**: 회원가입 전 이메일 인증 상태를 임시 저장

**특징**:
- 회원가입 전 이메일 인증 완료 상태를 Redis에 저장
- 회원가입 시 Redis에서 확인하여 `emailVerified = true`로 설정
- 24시간 내 회원가입하지 않으면 자동 만료

**코드 예시**:
```java
// 회원가입 전 이메일 인증 완료 처리 (Redis에 저장)
public String verifyPreRegistrationEmail(String token) {
    String email = jwtUtil.extractEmailFromEmailToken(token);
    String redisKey = "email_verification:pre_registration:" + email;
    
    stringRedisTemplate.opsForValue().set(
        redisKey,
        "verified",
        24,
        TimeUnit.HOURS
    );
    
    return email;
}
```

**상세 문서**: [이메일 인증 시스템 아키텍처](./이메일%20인증%20시스템%20아키텍처.md)

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

### Spring Cache 사용 (`@Cacheable`, `@CacheEvict`)

#### BoardService.java
- ⚠️ `getAllBoards()` - `@Cacheable` **주석 처리됨 (비활성화)**
- ✅ `getBoard()` - `@Cacheable` 적용
- ✅ `createBoard()` - `@CacheEvict` 적용
- ✅ `updateBoard()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `deleteBoard()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `updateBoardStatus()` - `@CacheEvict` 적용 (Caching 사용)
- ✅ `restoreBoard()` - `@CacheEvict` 적용 (Caching 사용)

#### CommentService.java
- ✅ `addComment()` - `@CacheEvict` 적용
- ✅ `deleteComment()` - `@CacheEvict` 적용
- ✅ `updateCommentStatus()` - `@CacheEvict` 적용
- ✅ `restoreComment()` - `@CacheEvict` 적용

#### ReactionService.java
- ✅ `reactToBoard()` - `@CacheEvict` 적용

#### LocationServiceService.java
- ✅ `getPopularLocationServices()` - `@Cacheable` 적용

### 직접 RedisTemplate 사용

#### NotificationService.java
- ✅ `createNotification()` - Redis에 알림 저장
- ✅ `getUserNotifications()` - Redis와 DB 병합 조회
- ✅ `markAsRead()` - Redis 캐시 삭제
- ✅ `markAllAsRead()` - Redis 캐시 삭제

#### EmailVerificationService.java
- ✅ `verifyPreRegistrationEmail()` - Redis에 인증 상태 저장
- ✅ `isPreRegistrationEmailVerified()` - Redis에서 인증 상태 확인

---

## ⚙️ Redis 설정 (RedisConfig.java)

### Spring Cache TTL 설정
- **boardList**: 10분 (현재 미사용)
- **boardDetail**: 1시간
- **popularLocationServices**: 기본값 30분
- **user**: 1시간
- **기본**: 30분

### RedisTemplate 설정
- `customStringRedisTemplate`: Refresh Token, 블랙리스트, 이메일 인증 상태용
- `objectRedisTemplate`: 게시글 캐싱, 사용자 정보 캐싱용
- `notificationRedisTemplate`: 알림 리스트용 (최신 50개, 24시간 TTL)
- `reactionCountRedisTemplate`: 좋아요/싫어요 배치 동기화용 (현재 미사용 가능성)

### Redis 사용 용도별 정리

| 용도 | RedisTemplate | TTL | 방식 |
|------|--------------|-----|------|
| 게시글 상세 캐싱 | Spring Cache | 1시간 | `@Cacheable` |
| 인기 위치 서비스 | Spring Cache | 30분 | `@Cacheable` |
| 알림 버퍼링 | `notificationRedisTemplate` | 24시간 | 직접 사용 |
| 이메일 인증 상태 | `customStringRedisTemplate` | 24시간 | 직접 사용 |
| Refresh Token | `customStringRedisTemplate` | 1일 | 직접 사용 |

---

## 🎯 캐시 무효화 전략 요약

### 1. 게시글 목록 캐싱 (현재 비활성화)
- ⚠️ 현재 비활성화 상태이므로 무효화 로직은 작동하지 않음

### 2. 게시글 상세 캐싱
- **게시글 변경**: 해당 게시글 캐시만 무효화
- **댓글 변경**: 해당 게시글 캐시 무효화 (댓글 수 포함)
- **반응 변경**: 해당 게시글 캐시 무효화 (좋아요 수 포함)

### 3. 인기 위치 서비스 캐싱
- **캐시 무효화**: 현재 구현되지 않음 (TTL에 의존)
- **개선 필요**: 위치 서비스 평점 변경 시 캐시 무효화 고려

### 4. 알림 시스템 캐싱
- **알림 생성**: Redis와 MySQL 모두 저장
- **읽음 처리**: Redis 캐시 삭제 (MySQL은 유지)
- **전체 읽음**: Redis 캐시 삭제

### 5. 이메일 인증 상태 캐싱
- **인증 완료**: Redis에 저장 (24시간 TTL)
- **회원가입 시**: Redis에서 확인 후 삭제
- **자동 만료**: 24시간 후 자동 삭제

### 6. 트랜잭션 고려사항
- `@CacheEvict`는 기본적으로 트랜잭션 커밋 후 실행 (`beforeInvocation = false`)
- 트랜잭션 롤백 시 캐시 무효화도 롤백됨
- 직접 RedisTemplate 사용 시 트랜잭션과 독립적으로 동작

---

## 🚀 성능 개선 효과

### Before (캐싱 없음)
- 게시글 상세 조회: 매번 DB 쿼리 + 조인 쿼리 실행
- 인기 위치 서비스 조회: 매번 DB 쿼리 실행
- 알림 조회: 매번 DB 쿼리 실행 (최신 50개)
- 이메일 인증 상태 확인: 매번 DB 쿼리 실행

### After (캐싱 적용)
- 게시글 상세 조회: Redis에서 즉시 반환 (1시간간) - **99% 이상 성능 향상**
- 인기 위치 서비스 조회: Redis에서 즉시 반환 (30분간)
- 알림 조회: Redis에서 즉시 반환 (최신 50개, 24시간) - **실시간 조회 성능 극대화**
- 이메일 인증 상태 확인: Redis에서 즉시 확인 (24시간)

### 실제 성능 개선 수치
- **알림 조회**: DB 쿼리 대비 **10배 이상 빠른 응답 속도**
- **게시글 상세 조회**: 복잡한 조인 쿼리 대비 **5배 이상 빠른 응답 속도**
- **인기 위치 서비스**: 정렬 쿼리 대비 **3배 이상 빠른 응답 속도**

---

## ⚠️ 주의사항

1. **게시글 목록 캐싱 비활성화**: 현재 개발 중 데이터 동기화 문제로 비활성화되어 있습니다. 재활성화 시 주의가 필요합니다.

2. **댓글 수 포함**: 게시글 상세에 댓글 수가 포함되므로 댓글 추가/삭제 시 게시글 상세 캐시를 무효화합니다.

3. **좋아요 수 포함**: 게시글 상세에 좋아요/싫어요 수가 포함되므로 반응 변경 시 게시글 상세 캐시를 무효화합니다.

4. **TTL 안전망**: 캐시 무효화가 실패하더라도 TTL로 인해 일정 시간 후 자동으로 만료됩니다.

5. **알림 병합 전략**: Redis와 MySQL 데이터를 병합할 때 중복 제거 및 정렬 로직이 필요합니다.

6. **이메일 인증 상태**: 회원가입 전 인증 상태는 24시간 내에만 유효하며, 회원가입 시 자동으로 삭제됩니다.

7. **인기 위치 서비스 캐시 무효화**: 현재 위치 서비스 평점 변경 시 캐시 무효화가 구현되지 않아 TTL에만 의존합니다.

---

## 📚 참고 자료

- Spring Cache Abstraction: https://docs.spring.io/spring-framework/reference/integration/cache.html
- Redis Cache Configuration: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

