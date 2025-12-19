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

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java:57-58`

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
- ✅ 게시글 생성 시: `boardList` 캐시 전체 무효화
- ✅ 게시글 수정 시: 해당 게시글 캐시 무효화 + `boardList` 캐시 전체 무효화
- ✅ 게시글 삭제 시: 해당 게시글 캐시 무효화 + `boardList` 캐시 전체 무효화
- ✅ 게시글 상태 변경 시: 해당 게시글 캐시 무효화 + `boardList` 캐시 전체 무효화
- ✅ 게시글 복구 시: 해당 게시글 캐시 무효화 + `boardList` 캐시 전체 무효화
- ✅ 댓글 추가 시: 해당 게시글 캐시 무효화 (댓글 수 포함)
- ✅ 댓글 수정 시: 해당 게시글 캐시 무효화
- ✅ 댓글 삭제 시: 해당 게시글 캐시 무효화
- ✅ 댓글 상태 변경 시: 해당 게시글 캐시 무효화
- ✅ 댓글 복구 시: 해당 게시글 캐시 무효화
- ✅ 좋아요/싫어요 반응 시: 해당 게시글 캐시 무효화 (좋아요 수 포함)

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`

```java
@Cacheable(value = "boardDetail", key = "#idx")
@Transactional
public BoardDTO getBoard(long idx, Long viewerId) { ... }

@CacheEvict(value = "boardList", allEntries = true)
@Transactional
public BoardDTO createBoard(BoardDTO dto) { ... }

@Caching(evict = {
    @CacheEvict(value = "boardDetail", key = "#idx"),
    @CacheEvict(value = "boardList", allEntries = true)
})
@Transactional
public BoardDTO updateBoard(long idx, BoardDTO dto) { ... }
```

---

### 3. 좋아요/싫어요 반응 캐싱

**전략**: Write-Through 방식 (즉시 캐시 무효화)

**적용 메서드**:
- `ReactionService.reactToBoard()` - `@CacheEvict` 적용

**동작 방식**:
- 좋아요/싫어요 반응 시 DB에 즉시 반영
- 게시글 상세 캐시를 무효화하여 다음 조회 시 최신 데이터 반영

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java:36`

```java
@CacheEvict(value = "boardDetail", key = "#boardId")
public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) { ... }
```

**참고**: 댓글 반응(`reactToComment`)에는 캐시 무효화가 적용되지 않습니다.

---

### 4. 인기 위치 서비스 캐싱 (`popularLocationServices`)

**캐시 키**: `popularLocationServices:{category}`

**적용 메서드**:
- `LocationServiceService.getPopularLocationServices(String category)` - `@Cacheable` 적용

**TTL**: 기본값 30분 (RedisConfig에서 설정)

**용도**: 카테고리별 인기 위치 서비스 상위 10개 조회 결과 캐싱

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java:28`

```java
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) {
    return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
        .stream()
        .map(locationServiceConverter::toDTO)
        .collect(Collectors.toList());
}
```

**참고**: 현재 캐시 무효화 로직이 없어 TTL에만 의존합니다. 위치 서비스 평점 변경 시 캐시 무효화가 필요할 수 있습니다.

---

### 5. 알림 시스템 캐싱 (직접 RedisTemplate 사용)

**캐시 키**: `notification:{userId}`

**적용 방식**: Spring Cache가 아닌 **직접 RedisTemplate 사용**

**사용하는 RedisTemplate**: `notificationRedisTemplate`

**TTL**: 24시간

**용도**: 사용자별 최신 알림 50개를 Redis에 캐싱하여 실시간 조회 성능 향상

**특징**:
- 최신 알림 50개만 유지 (초과 시 자동 삭제)
- MySQL과 병합하여 조회 (Redis + DB 병합 전략)
- 알림 생성 시 Redis와 MySQL 모두 저장
- 읽음 처리 시 Redis에서 해당 알림 제거
- 전체 읽음 처리 시 Redis 캐시 전체 삭제

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/notification/service/NotificationService.java`

```java
// Redis에 알림 저장 (최신 50개, 24시간 TTL)
private void saveToRedis(Long userId, NotificationDTO notification) {
    String redisKey = REDIS_KEY_PREFIX + userId; // "notification:" + userId
    List<NotificationDTO> existingNotifications = getFromRedis(userId);
    
    List<NotificationDTO> notifications = new ArrayList<>(existingNotifications);
    notifications.add(0, notification); // 최신 알림을 맨 앞에 추가
    if (notifications.size() > 50) {
        notifications = notifications.subList(0, 50);
    }
    
    notificationRedisTemplate.opsForValue().set(redisKey, notifications,
        Duration.ofHours(24));
}
```

**주요 메서드**:
- `createNotification()`: 알림 생성 시 Redis에 저장
- `getUserNotifications()`: Redis와 DB 병합 조회
- `markAsRead()`: 개별 알림 읽음 처리 시 Redis에서 제거
- `markAllAsRead()`: 전체 읽음 처리 시 Redis 캐시 전체 삭제

---

### 6. 이메일 인증 상태 캐싱 (직접 RedisTemplate 사용)

**캐시 키**: `email_verification:pre_registration:{email}`

**적용 방식**: Spring Cache가 아닌 **직접 RedisTemplate 사용**

**사용하는 RedisTemplate**: `customStringRedisTemplate`

**TTL**: 24시간

**용도**: 회원가입 전 이메일 인증 상태를 임시 저장

**특징**:
- 회원가입 전 이메일 인증 완료 상태를 Redis에 저장
- 회원가입 시 Redis에서 확인하여 `emailVerified = true`로 설정
- 24시간 내 회원가입하지 않으면 자동 만료
- 회원가입 완료 후 Redis에서 삭제

**코드 위치**: `backend/main/java/com/linkup/Petory/domain/user/service/EmailVerificationService.java`

```java
// 회원가입 전 이메일 인증 완료 처리 (Redis에 저장)
public String verifyPreRegistrationEmail(String token) {
    String email = jwtUtil.extractEmailFromEmailToken(token);
    String redisKey = PRE_REGISTRATION_VERIFICATION_KEY_PREFIX + email;
    
    stringRedisTemplate.opsForValue().set(
        redisKey,
        "verified",
        PRE_REGISTRATION_VERIFICATION_EXPIRE_HOURS, // 24시간
        TimeUnit.HOURS
    );
    
    return email;
}

// 회원가입 전 이메일 인증 완료 여부 확인
public boolean isPreRegistrationEmailVerified(String email) {
    String redisKey = PRE_REGISTRATION_VERIFICATION_KEY_PREFIX + email;
    String value = stringRedisTemplate.opsForValue().get(redisKey);
    return "verified".equals(value);
}
```

---

## 🔄 캐시 무효화 흐름도

### 게시글 생성/수정/삭제/상태변경/복구
```
게시글 생성/수정/삭제/상태변경/복구
    ↓
@CacheEvict 실행
    ↓
boardList 캐시 전체 무효화 (allEntries = true)
boardDetail 캐시 무효화 (해당 게시글)
    ↓
다음 조회 시 DB에서 최신 데이터 조회 후 캐시 저장
```

### 댓글 추가/수정/삭제/상태변경/복구
```
댓글 추가/수정/삭제/상태변경/복구
    ↓
@CacheEvict 실행
    ↓
boardDetail 캐시 무효화 (해당 게시글)
    ↓
다음 조회 시 DB에서 최신 데이터 조회 후 캐시 저장
```

### 좋아요/싫어요 반응
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
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`

- ⚠️ `getAllBoards()` - `@Cacheable` **주석 처리됨 (비활성화)** (line 57-58)
- ✅ `getBoard()` - `@Cacheable` 적용 (line 207)
- ✅ `createBoard()` - `@CacheEvict` 적용 (line 221)
- ✅ `updateBoard()` - `@CacheEvict` 적용 (Caching 사용) (line 246-249)
- ✅ `deleteBoard()` - `@CacheEvict` 적용 (Caching 사용) (line 276-279)
- ✅ `updateBoardStatus()` - `@CacheEvict` 적용 (Caching 사용) (line 555-558)
- ✅ `restoreBoard()` - `@CacheEvict` 적용 (Caching 사용) (line 568-571)

#### CommentService.java
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/CommentService.java`

- ✅ `addComment()` - `@CacheEvict` 적용 (line 65)
- ✅ `updateComment()` - `@CacheEvict` 적용 (line 107)
- ✅ `deleteComment()` - `@CacheEvict` 적용 (line 141)
- ✅ `updateCommentStatus()` - `@CacheEvict` 적용 (line 197)
- ✅ `restoreComment()` - `@CacheEvict` 적용 (line 215)

#### ReactionService.java
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java`

- ✅ `reactToBoard()` - `@CacheEvict` 적용 (line 36)
- ⚠️ `reactToComment()` - 캐시 무효화 없음 (댓글 반응은 캐시되지 않음)

#### LocationServiceService.java
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java`

- ✅ `getPopularLocationServices()` - `@Cacheable` 적용 (line 28)

### 직접 RedisTemplate 사용

#### NotificationService.java
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/notification/service/NotificationService.java`

**사용하는 RedisTemplate**: `notificationRedisTemplate` (주입: line 28)

- ✅ `createNotification()` - Redis에 알림 저장 (line 64)
- ✅ `getUserNotifications()` - Redis와 DB 병합 조회 (line 75-97)
- ✅ `markAsRead()` - Redis에서 개별 알림 제거 (line 139)
- ✅ `markAllAsRead()` - Redis 캐시 전체 삭제 (line 158)
- `saveToRedis()` - Redis에 알림 저장 (private, line 164)
- `getFromRedis()` - Redis에서 알림 조회 (private, line 193)
- `removeFromRedis()` - Redis에서 알림 제거 (private, line 206)
- `mergeNotifications()` - Redis와 DB 데이터 병합 (private, line 222)

#### EmailVerificationService.java
**파일 위치**: `backend/main/java/com/linkup/Petory/domain/user/service/EmailVerificationService.java`

**사용하는 RedisTemplate**: `customStringRedisTemplate` (주입: line 29)

- ✅ `verifyPreRegistrationEmail()` - Redis에 인증 상태 저장 (line 124-128)
- ✅ `isPreRegistrationEmailVerified()` - Redis에서 인증 상태 확인 (line 142-143)
- ✅ `removePreRegistrationVerification()` - Redis에서 인증 상태 삭제 (line 153)

---

## ⚙️ Redis 설정 (RedisConfig.java)

**파일 위치**: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`

### Spring Cache TTL 설정
- **boardList**: 10분 (현재 미사용 - `getAllBoards()` 주석 처리됨)
- **boardDetail**: 1시간
- **popularLocationServices**: 기본값 30분 (명시적 설정 없음)
- **user**: 1시간 (설정되어 있으나 실제 사용 안 함)
- **기본**: 30분

### RedisTemplate 설정

#### 1. `customStringRedisTemplate` (line 87-96)
- **용도**: 문자열 기반 데이터 저장
- **실제 사용**: 이메일 인증 상태 (`EmailVerificationService`)
- **설정**: Key/Value 모두 String 직렬화
- **참고**: 주석에는 "Refresh Token, 블랙리스트 등에 사용"이라고 되어 있으나, 현재 코드에서는 이메일 인증 상태에만 사용됨

#### 2. `objectRedisTemplate` (line 103-112)
- **용도**: 객체 저장용 (JSON 직렬화)
- **실제 사용**: 현재 사용 안 함
- **설정**: Key는 String, Value는 GenericJackson2JsonRedisSerializer
- **참고**: 주석에는 "게시글 캐싱, 사용자 정보 캐싱 등에 사용"이라고 되어 있으나, 실제로는 Spring Cache가 자동으로 사용

#### 3. `notificationRedisTemplate` (line 120-129)
- **용도**: 알림 리스트 저장
- **실제 사용**: `NotificationService`에서 사용
- **설정**: Key는 String, Value는 GenericJackson2JsonRedisSerializer
- **TTL**: 24시간 (서비스 코드에서 설정)

#### 4. `reactionCountRedisTemplate` (line 137-144)
- **용도**: 좋아요/싫어요 배치 동기화용
- **실제 사용**: 현재 사용 안 함
- **설정**: Key는 String, Value는 Long (GenericJackson2JsonRedisSerializer 사용)
- **참고**: 주석에는 "좋아요/싫어요 배치 동기화용"이라고 되어 있으나, 현재 코드에서는 사용되지 않음

### Redis 사용 용도별 정리

| 용도 | RedisTemplate | TTL | 방식 | 상태 |
|------|--------------|-----|------|------|
| 게시글 상세 캐싱 | Spring Cache | 1시간 | `@Cacheable` | ✅ 활성화 |
| 인기 위치 서비스 | Spring Cache | 30분 | `@Cacheable` | ✅ 활성화 |
| 알림 버퍼링 | `notificationRedisTemplate` | 24시간 | 직접 사용 | ✅ 활성화 |
| 이메일 인증 상태 | `customStringRedisTemplate` | 24시간 | 직접 사용 | ✅ 활성화 |
| 게시글 목록 캐싱 | Spring Cache | 10분 | `@Cacheable` | ⚠️ 비활성화 |
| 사용자 정보 캐싱 | Spring Cache | 1시간 | `@Cacheable` | ❌ 미사용 |
| 객체 캐싱 | `objectRedisTemplate` | - | 직접 사용 | ❌ 미사용 |
| 반응 카운트 배치 | `reactionCountRedisTemplate` | - | 직접 사용 | ❌ 미사용 |

---

## 🎯 캐시 무효화 전략 요약

### 1. 게시글 목록 캐싱 (현재 비활성화)
- ⚠️ 현재 비활성화 상태이므로 무효화 로직은 작동하지 않음
- 게시글 생성/수정/삭제 시 `boardList` 캐시 전체 무효화 로직은 남아있으나 실제 캐싱이 안 되므로 의미 없음

### 2. 게시글 상세 캐싱
- **게시글 변경**: 해당 게시글 캐시만 무효화 + `boardList` 캐시 전체 무효화
- **댓글 변경**: 해당 게시글 캐시 무효화 (댓글 수 포함)
- **반응 변경**: 해당 게시글 캐시 무효화 (좋아요 수 포함)

### 3. 인기 위치 서비스 캐싱
- **캐시 무효화**: 현재 구현되지 않음 (TTL에 의존)
- **개선 필요**: 위치 서비스 평점 변경 시 캐시 무효화 고려

### 4. 알림 시스템 캐싱
- **알림 생성**: Redis와 MySQL 모두 저장
- **읽음 처리**: Redis에서 해당 알림 제거 (MySQL은 유지)
- **전체 읽음**: Redis 캐시 전체 삭제

### 5. 이메일 인증 상태 캐싱
- **인증 완료**: Redis에 저장 (24시간 TTL)
- **회원가입 시**: Redis에서 확인 후 삭제 (`removePreRegistrationVerification()`)
- **자동 만료**: 24시간 후 자동 삭제

### 6. 트랜잭션 고려사항
- `@CacheEvict`는 기본적으로 트랜잭션 커밋 후 실행 (`beforeInvocation = false`)
- 트랜잭션 롤백 시 캐시 무효화도 롤백됨
- 직접 RedisTemplate 사용 시 트랜잭션과 독립적으로 동작

---

## ⚠️ 주의사항

1. **게시글 목록 캐싱 비활성화**: 현재 개발 중 데이터 동기화 문제로 비활성화되어 있습니다. 재활성화 시 주의가 필요합니다.

2. **댓글 수 포함**: 게시글 상세에 댓글 수가 포함되므로 댓글 추가/삭제 시 게시글 상세 캐시를 무효화합니다.

3. **좋아요 수 포함**: 게시글 상세에 좋아요/싫어요 수가 포함되므로 반응 변경 시 게시글 상세 캐시를 무효화합니다.

4. **TTL 안전망**: 캐시 무효화가 실패하더라도 TTL로 인해 일정 시간 후 자동으로 만료됩니다.

5. **알림 병합 전략**: Redis와 MySQL 데이터를 병합할 때 중복 제거 및 정렬 로직이 필요합니다 (`mergeNotifications()` 메서드).

6. **이메일 인증 상태**: 회원가입 전 인증 상태는 24시간 내에만 유효하며, 회원가입 시 자동으로 삭제됩니다.

7. **인기 위치 서비스 캐시 무효화**: 현재 위치 서비스 평점 변경 시 캐시 무효화가 구현되지 않아 TTL에만 의존합니다.

8. **미사용 RedisTemplate**: `objectRedisTemplate`, `reactionCountRedisTemplate`는 설정되어 있으나 현재 사용되지 않습니다. 향후 사용 계획이 있다면 유지하고, 없다면 제거를 고려할 수 있습니다.

9. **댓글 반응 캐싱 없음**: 댓글에 대한 좋아요/싫어요 반응은 캐시되지 않습니다. 필요시 추가 고려가 필요합니다.

---

## 📚 참고 자료

- Spring Cache Abstraction: https://docs.spring.io/spring-framework/reference/integration/cache.html
- Redis Cache Configuration: `backend/main/java/com/linkup/Petory/global/security/RedisConfig.java`
- Spring Data Redis: https://docs.spring.io/spring-data/redis/docs/current/reference/html/

