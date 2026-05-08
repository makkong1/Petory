# DB 개념 어필 포인트 — User 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)
> 참고 파일: `domain/user/`, `filter/JwtAuthenticationFilter.java`, `docs/refactoring/user/`, `docs/troubleshooting/users/`

---

## 1. Refresh Token DB 저장 + 이중 검증 (JWT + DB)

### 어필 포인트
- Refresh Token을 `users` 테이블의 `refresh_token`, `refresh_expiration` 컬럼에 직접 저장
- 갱신 요청 시 JWT 서명 검증 후 DB에서 토큰 존재 여부와 만료 시각을 재확인하는 이중 검증
- 로그아웃 시 DB에서 토큰을 null로 삭제 → 서버 측 무효화(revocation) 가능

```java
// AuthService.java — 로그인 시
user.setRefreshToken(refreshToken);
user.setRefreshExpiration(LocalDateTime.now().plusDays(1));
usersRepository.save(user);

// AuthService.java — 갱신 시 이중 검증
if (!jwtUtil.validateToken(refreshToken)) throw InvalidRefreshTokenException.invalid();
Users user = usersRepository.findActiveByRefreshToken(refreshToken)
        .orElseThrow(() -> InvalidRefreshTokenException.notFound());
if (user.getRefreshExpiration().isBefore(LocalDateTime.now()))
    throw InvalidRefreshTokenException.expired();
```

### 말할 내용
> "Refresh Token을 stateless JWT로만 관리하면 탈취 시 만료 전까지 무효화가 불가능합니다. 그래서 users 테이블에 refresh_token, refresh_expiration 컬럼을 두고, 갱신 요청마다 JWT 서명 검증과 DB 조회를 순서대로 수행합니다. 로그아웃 시 DB 값을 null로 초기화해 즉시 무효화가 가능하고, 탈취된 토큰으로 갱신을 시도해도 DB에서 차단됩니다."

---

## 2. 동시성 제어 — 경고 횟수 원자적 증가 (DB 레벨 UPDATE)

### 어필 포인트
- `warningCount` 증가를 애플리케이션 레벨이 아닌 DB `UPDATE ... SET count = count + 1`로 처리
- Lost Update 방지: 여러 관리자가 동시에 경고 부여 시에도 카운터 정확 보장
- 경고 3회 도달 시 자동 이용제한 3일 로직이 이 원자적 증가에 의존

```java
// SpringDataJpaUsersRepository.java
@Modifying
@Query("UPDATE Users u SET u.warningCount = u.warningCount + 1 WHERE u.idx = :userId")
int incrementWarningCount(@Param("userId") Long userId);

// UserSanctionService.java
usersRepository.incrementWarningCount(userId);          // 원자적 증가
user = usersRepository.findById(userId).orElseThrow();  // 업데이트된 값 재조회
if (user.getWarningCount() >= WARNING_THRESHOLD) {       // >= 3
    addSuspension(userId, ..., AUTO_SUSPENSION_DAYS);    // 자동 이용제한 3일
}
```

### 말할 내용
> "경고 횟수를 user.getWarningCount() + 1 로 저장하면 두 스레드가 동시에 읽은 값이 같아 한 번이 누락됩니다. 이를 막기 위해 'UPDATE users SET warning_count = warning_count + 1'로 DB 원자적 증가 쿼리를 사용했습니다. 이 방식은 행 레벨에서 직렬화되어 동시 요청에도 카운터가 정확히 올라가고, 경고 3회 자동 이용제한 트리거가 항상 정확하게 동작합니다."

---

## 3. 비관적 락(PESSIMISTIC_WRITE) — PetCoin 차감

### 어필 포인트
- `pet_coin_balance` 컬럼을 가진 `users` 테이블에서 코인 차감 시 `SELECT ... FOR UPDATE` 적용
- Race Condition 방지: 동시 결제 요청이 들어와도 잔액 이중 차감 방지

```java
// SpringDataJpaUsersRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM Users u WHERE u.idx = :idx")
Optional<Users> findByIdForUpdate(@Param("idx") Long idx);
```

### 말할 내용
> "펫코인 차감은 잔액을 읽고 확인 후 저장하는 read-modify-write 패턴이라 동시 요청 시 이중 차감이 발생할 수 있습니다. JPA의 PESSIMISTIC_WRITE를 사용해 'SELECT ... FOR UPDATE'를 실행하면, 락을 획득한 트랜잭션이 완료될 때까지 다른 요청이 해당 행을 수정할 수 없어 정확한 잔액 관리가 가능합니다."

---

## 4. N+1 해결 — socialUsers @BatchSize + 회원가입 중복 검사 쿼리 통합

### 어필 포인트
**socialUsers N+1 (@BatchSize)**
- `Users.socialUsers`가 기본 LAZY 로딩이라 사용자 목록 조회 시 N+1 발생 (100명 → 101 쿼리)
- `@BatchSize(size = 50)` 추가로 Hibernate가 `WHERE user_idx IN (...)` 배치 조회 → 3 쿼리로 감소

```java
// Users.java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
@BatchSize(size = 50)  // [리팩토링] 101 쿼리 → 3 쿼리 (100명 기준)
private List<SocialUser> socialUsers;
```

**회원가입 중복 검사 3회 → 1회**
- 기존: `findByNickname`, `findByUsername`, `findByEmail` 각각 1회 = 3 round-trip
- 개선: `findByNicknameOrUsernameOrEmail()` 단일 쿼리로 통합

```java
// SpringDataJpaUsersRepository.java
@Query("SELECT u FROM Users u WHERE (u.nickname = :nickname OR u.username = :username OR u.email = :email) AND (u.isDeleted = false OR u.isDeleted IS NULL)")
List<Users> findByNicknameOrUsernameOrEmail(...);
```

### 말할 내용
> "사용자 목록 페이지에서 소셜 로그인 정보를 함께 표시할 때, 각 사용자마다 SocialUser 쿼리가 별도로 실행되어 N+1 문제가 발생했습니다. @BatchSize(size=50)를 적용해 Hibernate가 IN 절 배치 조회를 하도록 했고, 100명 기준 101 쿼리에서 3 쿼리로 줄었습니다. 같은 맥락에서 회원가입 중복 검사도 3개 개별 쿼리를 OR 조건 하나로 통합해 round-trip을 3배 줄였습니다."

---

## 5. 로그인 시 N+1 해결 — 채팅방 배치 조회 (실측 측정 완료)

### 어필 포인트
- 로그인 후 채팅방 목록 조회 시 채팅방마다 참여자·메시지 쿼리 개별 실행 → N+1
- IN 절 배치 조회 + Fetch Join + 최신 메시지만 조회로 해결
- 실측: 채팅방 10개 기준 21 쿼리 → 4 쿼리, 305ms → 55ms, 메모리 0.58MB → 0.13MB

```
Before: SELECT * FROM conversation ...          (1)
        SELECT * FROM conversationparticipant WHERE conversation_idx = 1 AND user_idx = ?
        SELECT * FROM conversationparticipant WHERE conversation_idx = 1 AND status = 'ACTIVE'
        SELECT * FROM chatmessage WHERE conversation_idx = 1  ← 전체 메시지 로드
        ... (채팅방마다 반복) = 21 쿼리

After:  SELECT * FROM conversation ...                               (1)
        SELECT * FROM conversationparticipant WHERE conversation_idx IN (...)  (1)
        SELECT * FROM conversationparticipant WHERE conversation_idx IN (...)  (1)
        SELECT * FROM chatmessage WHERE idx IN (SELECT MAX(idx) ...) (1) ← 최신만
        = 4 쿼리 (채팅방 수와 무관하게 일정)
```

**실측 성능 개선**:
| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| 쿼리 수 | 21개 | 4개 | 80.95% |
| 실행 시간 | 305ms | 55ms | 81.97% |
| 메모리 사용량 | 607,968 bytes | 138,384 bytes | 77.24% |

### 말할 내용
> "로그인 시 채팅방 목록을 조회할 때 N+1 문제가 발생해 채팅방 10개 기준 21개 쿼리가 실행되고 응답 시간이 305ms나 걸렸습니다. 채팅방 ID 목록을 먼저 추출하고 IN 절로 배치 조회하도록 변경했고, 최신 메시지 1건만 서브쿼리로 가져오도록 바꿨습니다. 결과적으로 쿼리는 4개로 고정되고 응답 시간은 55ms로 줄었습니다. 채팅방이 100개로 늘어도 쿼리는 4개로 유지됩니다."

---

## 6. 소프트 삭제(Soft Delete) + 탈퇴 사용자 닉네임 재사용 트러블슈팅

### 어필 포인트
- 회원 탈퇴 시 `is_deleted = true`, `deleted_at` 설정으로 데이터 보존
- 탈퇴한 사용자의 닉네임/username/email을 다른 사용자가 재사용할 수 없는 버그 발견 → Repository JPQL에 `isDeleted = false OR isDeleted IS NULL` 조건 추가로 해결
- Race Condition 대비: `DataIntegrityViolationException` catch 후 필드별 명확한 에러 메시지 반환

```java
// SpringDataJpaUsersRepository.java — 수정 후
@Query("SELECT u FROM Users u WHERE u.nickname = :nickname AND (u.isDeleted = false OR u.isDeleted IS NULL)")
Optional<Users> findByNickname(@Param("nickname") String nickname);
```

```java
// UsersService.java — Race Condition 처리
try {
    saved = usersRepository.save(user);
} catch (DataIntegrityViolationException e) {
    if (errorMessage.contains("nickname")) throw new RuntimeException("이미 사용 중인 닉네임입니다.");
    // email, username 등 필드별 처리
}
```

### 말할 내용
> "소프트 삭제를 적용하면 DB에 데이터가 남아있어, 탈퇴한 사용자의 닉네임으로 신규 가입 시도 시 중복 체크가 탈퇴 사용자를 잡아 가입이 실패하는 버그가 있었습니다. Repository의 JPQL 쿼리에 is_deleted = false 조건을 추가해 활성 사용자만 체크하도록 수정했습니다. 또한 체크와 저장 사이에 발생할 수 있는 Race Condition에 대비해 DB Unique 제약조건 위반 예외를 잡아 사용자 친화적 에러 메시지로 변환했습니다."

---

## 7. OAuth2 소셜 로그인 동시성 — DB UNIQUE 제약조건으로 Race Condition 방지

### 어필 포인트
- 같은 소셜 계정 동시 로그인 시도 시 `findByProviderAndProviderId()` 조회가 모두 null을 반환해 중복 계정 생성 가능성
- `users.email` UNIQUE 제약조건 + `socialuser.(provider, provider_id)` UNIQUE 제약조건으로 DB 레벨에서 차단
- 트랜잭션 롤백으로 한 계정만 생성 보장

```java
// OAuth2Service.java — 사용자 생성 시도
// DB UNIQUE 제약조건이 최후 방어선 역할
// email UNIQUE: 같은 이메일로 중복 계정 방지
// socialuser (provider + providerId) UNIQUE: 같은 소셜 계정 중복 SocialUser 방지
```

### 말할 내용
> "소셜 로그인에서 같은 계정으로 동시에 두 번 로그인 시도가 들어오면 두 스레드 모두 신규 사용자로 판단해 계정을 생성할 수 있습니다. 낙관적 락보다 단순하게, DB의 UNIQUE 제약조건을 최후 방어선으로 사용했습니다. 이메일 컬럼과 socialuser 테이블의 (provider, provider_id) 복합 유니크 제약조건이 위반되면 예외가 발생하고 트랜잭션이 롤백되어 중복 계정 생성을 막습니다."

---

## 8. 이메일 인증 — Redis 임시 저장 (TTL 활용)

### 어필 포인트
- 회원가입 전 이메일 인증 상태를 Redis에 저장, TTL 24시간 자동 만료
- 키 형식: `email_verification:pre_registration:{email}`
- 가입 완료 후 명시적 삭제 (`stringRedisTemplate.delete(redisKey)`)
- 기존 사용자 인증은 `users.email_verified` 컬럼 직접 업데이트로 처리 (단일 통합 상태)

```java
// EmailVerificationService.java
private static final String PRE_REGISTRATION_VERIFICATION_KEY_PREFIX = "email_verification:pre_registration:";
private static final long PRE_REGISTRATION_VERIFICATION_EXPIRE_HOURS = 24;

// 인증 완료 시 Redis에 저장
stringRedisTemplate.opsForValue().set(
    redisKey, "verified",
    PRE_REGISTRATION_VERIFICATION_EXPIRE_HOURS, TimeUnit.HOURS);

// 가입 완료 후 명시적 삭제
stringRedisTemplate.delete(redisKey);
```

### 말할 내용
> "회원가입 전 이메일 인증 상태는 아직 DB 사용자 레코드가 없어 users 테이블에 저장할 수 없습니다. Redis의 TTL 기능을 활용해 24시간 동안만 인증 상태를 유지하고 자동 만료되도록 설계했습니다. 가입이 완료되면 users.email_verified 컬럼을 true로 업데이트하고 Redis 키는 명시적으로 삭제합니다. 이 방식은 미인증 이메일 데이터가 DB를 오염시키지 않고 자동 정리됩니다."

---

## 9. 역할 계층 기반 권한 + 경량 쿼리 최적화

### 어필 포인트
- `Role` enum: `USER < SERVICE_PROVIDER < ADMIN < MASTER` 4단계 계층
- 삭제 권한 검증 시 `Users` 전체 + `Pet` JOIN 조회 대신 `role` 스칼라 프로젝션만 조회하는 경량 메서드 도입

```java
// SpringDataJpaUsersRepository.java
@Query("SELECT u.role FROM Users u WHERE u.idx = :idx")
Optional<Role> findRoleByIdx(@Param("idx") Long idx);
// [리팩토링] Users+Pet 전체 조회 → role 1개 컬럼만 SELECT
```

```java
// Role.java
public enum Role { USER, SERVICE_PROVIDER, ADMIN, MASTER }
```

### 말할 내용
> "권한 검증을 위해 매번 Users 전체와 연관 Pet까지 조회하는 것은 낭비입니다. role 컬럼 하나만 필요한 경우 프로젝션 쿼리로 해당 컬럼만 가져오도록 리팩토링했습니다. 이처럼 실제로 필요한 데이터만 SELECT하는 습관이 불필요한 네트워크 I/O와 메모리 할당을 줄여줍니다."

---

## 10. 트랜잭션 관리 + 로그인 중복 쿼리 제거 리팩토링

### 어필 포인트
- 로그인 시 `getUserById()` 호출이 내부에서 `findByIdString`을 한 번 더 실행 → 동일 User 2회 조회
- `usersConverter.toDTO(user)`로 이미 로드된 엔티티를 직접 변환 → DB 조회 1회 감소

```java
// AuthService.java — 리팩토링 후
Users user = usersRepository.findActiveByIdString(id).orElseThrow(...);
// ... save ...
// [리팩토링] usersService.getUserById() → usersConverter.toDTO(user) (User 1회 조회)
UsersDTO userDTO = usersConverter.toDTO(user);
return new TokenResponse(accessToken, refreshToken, userDTO);
```

- 쓰기 메서드 (`login`, `logout`, `refreshAccessToken`): `@Transactional`
- 읽기 메서드: `@Transactional(readOnly = true)` (read-only 최적화)
- 기본 격리 수준: READ_COMMITTED

### 말할 내용
> "로그인 로직을 프로파일링하다 이미 조회한 User 객체를 두 번째로 조회하는 불필요한 쿼리를 발견했습니다. 서비스 메서드 경계를 재정리하고 컨버터를 직접 호출하도록 바꿔 로그인과 토큰 갱신 각각 1회씩 DB 호출을 줄였습니다. 작은 수치지만 MAU가 높은 서비스에서 로그인은 빈도가 높은 API라 누적 효과가 큽니다."

---

## 핵심 키워드

- Refresh Token DB 저장 + 이중 검증 (JWT + DB)
- 비관적 락 (`PESSIMISTIC_WRITE`) — PetCoin 차감
- DB 레벨 원자적 UPDATE — 경고 횟수 동시성 제어
- `@BatchSize` — socialUsers N+1 해결
- IN 절 배치 조회 + Fetch Join — 채팅방 목록 N+1 해결 (실측 80.95%)
- Soft Delete + isDeleted 필터링 — 탈퇴 사용자 닉네임 재사용 버그 해결
- Redis TTL — 이메일 인증 임시 저장 (24h)
- DB UNIQUE 제약조건 — OAuth2 동시 로그인 Race Condition 방어
- 프로젝션 쿼리 (`findRoleByIdx`) — 불필요한 컬럼 제거
- `findByNicknameOrUsernameOrEmail` — 중복 검사 3 쿼리 → 1 쿼리

---

## 관련 문서

- `docs/domains/user.md` — 전체 도메인 상세 (엔티티, API, 서비스 메서드)
- `docs/refactoring/user/user-backend-performance-optimization.md` — 리팩토링 항목 체크리스트
- `docs/troubleshooting/users/login-n-plus-one-issue.md` — 채팅방 N+1 실측 데이터
- `docs/troubleshooting/users/soft-delete-nickname-reuse.md` — 소프트 삭제 닉네임 재사용 버그

---

## 면접 대답 구성

### 질문: "DB 동시성 이슈를 경험한 적이 있나요?"

1. **상황** (20초)
   - "경고 누적 시 자동 이용제한을 적용하는 로직에서 동시성 문제를 발견했습니다."

2. **문제 원인** (40초)
   - "여러 관리자가 동시에 같은 사용자에게 경고를 부여할 때, 각 스레드가 동일한 warningCount를 읽고 +1해서 저장하면 Lost Update가 발생합니다. 경고 2회 상태에서 2명이 동시에 경고를 부여하면 결과가 3이 아닌 2가 될 수 있습니다."

3. **해결** (40초)
   - "'UPDATE users SET warning_count = warning_count + 1'을 JPA @Modifying 쿼리로 실행해 DB 레벨에서 원자적으로 증가시켰습니다. DB가 행 레벨에서 직렬화하므로 동시 요청에도 카운터가 정확합니다."

4. **결과** (20초)
   - "자동 이용제한이 정확히 한 번만 적용되고, 경고 누락이나 중복 적용 없이 안정적으로 동작합니다."

---

### 질문: "N+1 문제를 해결한 경험이 있나요?"

1. **문제 발견** (20초)
   - "로그인 시 채팅방 목록 API에서 채팅방 10개 기준 21개 쿼리와 305ms 응답 시간을 측정했습니다."

2. **원인** (40초)
   - "채팅방마다 참여자 정보와 메시지를 개별 쿼리로 조회했고, LAZY 로딩으로 최신 메시지 1건을 위해 모든 메시지를 메모리에 로드하고 있었습니다."

3. **해결** (40초)
   - "채팅방 ID 목록을 먼저 추출하고, 참여자와 메시지를 IN 절 배치 조회로 변경했습니다. 메시지는 서브쿼리로 각 채팅방의 MAX(idx)만 가져왔습니다."

4. **결과** (20초)
   - "쿼리 4개로 고정, 응답 시간 55ms(81.97% 개선), 메모리 77.24% 감소. 채팅방이 100개로 늘어도 쿼리 수는 동일합니다."
