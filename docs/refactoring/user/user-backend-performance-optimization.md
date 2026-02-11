# User 백엔드 성능 최적화 리팩토링

## 개요
User 도메인의 백엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**문서 구조**:
- **리팩토링**: `auth-duplicate-query/`, `admin-delete-optimization/`
- **트러블슈팅**: `social-users-query/` - UsersConverter socialUsers N+1 쿼리 (런타임 발견 이슈)
- **기타**: `profile-with-pets`, `getAllUsers` 페이징 등

---

## 🔴 Critical (긴급) - 리팩토링

### 1. 전체 사용자 조회 - `getAllUsers()` 메모리 전체 로드

**파일**: `UsersService.java` (Lines 40-42), `AdminUserController.java` (Lines 33-37)

**현재 문제**:
- `findAll()`로 전체 사용자를 메모리에 로드
- 탈퇴한 사용자(isDeleted) 포함
- 사용자 수가 많아질수록 메모리/응답 시간 증가

```java
// 현재 코드 (비효율적)
public List<UsersDTO> getAllUsers() {
    return usersConverter.toDTOList(usersRepository.findAll());
}
```

**해결 방안**:
```java
// Repository에 탈퇴 제외 조회 메서드 추가
@Query("SELECT u FROM Users u WHERE u.isDeleted = false OR u.isDeleted IS NULL")
List<Users> findAllNotDeleted();

// 또는 페이징만 사용 (getAllUsersWithPaging 이미 존재)
// getAllUsers() deprecate 또는 페이징 강제
```

**예상 효과**: 탈퇴 사용자 제외로 불필요한 데이터 로드 감소, 대량 데이터 시 메모리 개선 
-- 이 부분은 탈퇴한사람까지 해야함 / 차라리 자료구조,알고리즘 이쪽을 좀 분석하는 방향으로 

---

### 2. Auth 로그인 시 중복 DB 조회 ✅ **해결 완료**

**파일**: `AuthService.java` (Lines 28-66)

**이전 문제**:
- `login()`: `findByIdString` 1회 → `save` 2회 → `getUserById` (내부에서 `findByIdString` 1회) = **동일 User 2번 조회**
- `refreshAccessToken()`: `findByRefreshToken` 1회 → `getUserById` (내부에서 `findByIdString` 1회) = **동일 User 2번 조회**

```java
// 현재 코드 (login)
Users user = usersRepository.findByIdString(id).orElseThrow(...);  // 1번
// ... save ...
UsersDTO userDTO = usersService.getUserById(id);  // 2번 - 내부에서 또 findByIdString
```

**해결 방안**:
```java
// 개선: 이미 로드한 User 엔티티를 DTO로 변환
UsersDTO userDTO = usersConverter.toDTO(user);
return new TokenResponse(accessToken, refreshToken, userDTO);
```

**리팩토링 결과**:
- ✅ `UsersService.getUserById()` 대신 `UsersConverter.toDTO(user)` 사용
- ✅ AuthService에서 UsersService 의존성 제거
- ✅ 로그인/Refresh 시 DB 쿼리 1회 감소

**시퀀스 다이어그램**: [auth-duplicate-query/sequence-diagram.md](./auth-duplicate-query/sequence-diagram.md)

---

### 3. Admin 삭제 시 불필요한 getUser 호출 ✅ **해결 완료**

**파일**: `AdminUserController.java` (Lines 92-102)

**이전 문제**:
- `deleteUser()` 호출 전 권한 검증을 위해 `getUser(id)` 호출
- `getUser()` → `getUserWithPets()` → User 조회 + Pet 조회 (2+ 쿼리)
- 삭제 API에서 삭제 대상의 전체 프로필이 꼭 필요하지 않음 (역할만 확인하면 됨)

**리팩토링 결과**:
- ✅ `findRoleByIdx()` 쿼리 추가 (role 프로젝션만 SELECT)
- ✅ `UsersService.getRoleById()` 경량 메서드 추가
- ✅ AdminUserController에서 `getUser()` → `getRoleById()` 변경
- ✅ 권한 검증 시 DB 쿼리 2+ → 1회로 감소

**시퀀스 다이어그램**: [admin-delete-optimization/sequence-diagram.md](./admin-delete-optimization/sequence-diagram.md)

---

## 🔴 트러블슈팅 (런타임 발견 이슈)

### 4. N+1 쿼리 - `UsersConverter.socialUsers` 접근

**파일**: `UsersConverter.java` (Lines 31-36)

**발견 경로**: `getAllUsers()`, `getAllUsersWithPaging()` 호출 시 사용자 수가 많을수록 쿼리 수 급증 → 프로파일링으로 N+1 발견

**문제 원인**:
- `toDTO()`에서 `user.getSocialUsers()` 접근 시 Lazy Loading 트리거
- Users 엔티티에 `@OneToMany(mappedBy = "user")` socialUsers (기본 LAZY)
- N명 사용자 조회 시: 1 (Users) + N (SocialUser) = **N+1 쿼리**

```java
// UsersConverter.java - 트리거 위치
.socialUsers(user.getSocialUsers() != null ? user.getSocialUsers().stream()
        .map(socialUserConverter::toDTO)
        .collect(Collectors.toList())
        : null)
```

**해결 방안** (우선순위):
1. **@BatchSize**: Users 엔티티 `socialUsers`에 `@BatchSize(size = 50)` 추가 → 가장 간단
2. **JOIN FETCH**: `findAllWithSocialUsers()` 메서드 추가
3. **선택적 로딩**: socialUsers 불필요한 API는 `toDTOWithoutSocialUsers()` 사용

**상세**: [social-users-query/troubleshooting.md](./social-users-query/troubleshooting.md)

---

## 🟠 High Priority - 리팩토링

### 5. 프로필 조회 시 User + Pet 분리 쿼리 (2+ N)

**파일**: `UsersService.java` (Lines 287-328), `PetService.java` (Lines 36-47)

**현재 문제**:
- `getMyProfile()`, `getUserWithPets()`: User 1회 조회 → Pet 별도 1회 조회
- Pet 조회 시 PetVaccination Lazy Loading으로 N+1 가능성 (Pet에 @BatchSize 적용됨 ✅)
- User와 Pet이 1+N 형태로 분리 조회

**해결 방안**:
```java
// SpringDataJpaUsersRepository에 메서드 추가
@Query("SELECT u FROM Users u LEFT JOIN FETCH u.pets p WHERE u.id = :userId AND (p.isDeleted = false OR p IS NULL)")
Optional<Users> findByIdStringWithPets(@Param("userId") String userId);
```
- 단, Users.pets는 `@OneToMany`로 컬렉션이므로 JOIN FETCH 시 중복 row 발생 → `DISTINCT` 필요
- 또는 현재 구조 유지 (PetService가 이미 배치 File 조회 적용됨)

---

### 6. 프로필+리뷰 조회 시 중복 쿼리 - `getAverageRating` vs `getReviewsByReviewee`

**파일**: `UserProfileController.java` (Lines 52-69, 279-292), `CareReviewService.java` (Lines 33-69)

**현재 문제**:
- `getMyProfile()`, `getUserProfile()`: `getReviewsByReviewee` + `getAverageRating` 2번 호출
- **동일 쿼리 2번 실행**: `findByRevieweeIdxOrderByCreatedAtDesc`가 두 메서드에서 각각 호출됨

```java
// CareReviewService
public List<CareReviewDTO> getReviewsByReviewee(Long revieweeIdx) {
    List<CareReview> reviews = reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
    // ...
}

public Double getAverageRating(Long revieweeIdx) {
    List<CareReview> reviews = reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);  // 동일 쿼리!
    // ...
}
```

**해결 방안**:
```java
// 통합 메서드 추가
public ReviewSummaryDTO getReviewsWithAverage(Long revieweeIdx) {
    List<CareReview> reviews = reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
    Double avg = reviews.isEmpty() ? null : reviews.stream().mapToInt(CareReview::getRating).average().orElse(0);
    return new ReviewSummaryDTO(reviewConverter.toDTOList(reviews), avg, reviews.size());
}
```

---

### 7. OAuth2 고유 ID/Username 생성 - while 루프 DB 조회

**파일**: `OAuth2Service.java` (Lines 230-257)

**현재 문제**:
- `generateUniqueId()`, `generateUniqueUsername()`: while 루프에서 매번 DB 조회
- 중복 가능성이 낮을 때도 최소 1회, 충돌 시 N회 쿼리 발생

```java
while (usersRepository.findByIdString(uniqueId).isPresent()) {
    uniqueId = baseId + "_" + suffix;
    suffix++;
}
```

**해결 방안**:
1. **UUID 활용**: `baseId + "_" + UUID.randomUUID().toString().substring(0, 8)` - 충돌 확률 극히 낮음
2. **DB Unique 제약 + 재시도**: save 시 `DataIntegrityViolationException` catch 후 suffix 증가하여 재시도 (현재 createUser에 이미 적용됨)
3. **Redis/분산 ID**: 고유 ID 생성器 사용 (규모 큰 경우)

---

### 8. 회원가입 시 중복 검사 3회 개별 쿼리

**파일**: `UsersService.java` (Lines 110-126)

**현재 문제**:
- `findByNickname`, `findByUsername`, `findByEmail` 각각 1회씩 = 3회 DB 조회
- 순차 실행으로 총 3번의 round-trip

**해결 방안**:
- 단일 쿼리로 통합 (존재 여부만 반환하는 메서드)
```java
@Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Users u " +
       "WHERE u.nickname = :nickname OR u.username = :username OR u.email = :email " +
       "AND (u.isDeleted = false OR u.isDeleted IS NULL)")
boolean existsByNicknameOrUsernameOrEmail(@Param("nickname") String nickname, 
                                          @Param("username") String username, 
                                          @Param("email") String email);
```
- 단, 어느 필드가 중복인지 구분하려면 개별 쿼리 유지 필요 → 사용자 경험 위해 현재 구조 유지 가능

---

## 🟡 Medium Priority

### 9. UserSanctionService addWarning - 중복 findById

**파일**: `UserSanctionService.java` (Lines 36-62)

**현재 문제**:
- `findById` 1회 → `incrementWarningCount` 후 → `findById` 1회 (업데이트된 warningCount 조회)
- `refresh()` 또는 `incrementWarningCount` 반환값으로 대체 가능

**해결 방안**:
```java
// incrementWarningCount가 업데이트된 행 수를 반환
// 경고 횟수는 application에서 +1로 계산 가능
int updated = usersRepository.incrementWarningCount(userId);
user = usersRepository.findById(userId).orElseThrow(...);
// 또는 entityManager.refresh(user) 사용
```

---

### 10. UserProfileController updateMyProfile - 불필요한 getMyProfile

**파일**: `UserProfileController.java` (Lines 78-89)

**현재 문제**:
- `updateMyProfile` 호출 시 `getMyProfile(userId)`로 currentUser 조회 후 idx 비교
- 이미 `getCurrentUserId()`로 userId 보유 중인데, idx 비교를 위해 전체 프로필(Pet 포함) 조회

**해결 방안**:
- userId로 이미 본인 확인 가능하므로, idx 비교가 필요한 경우 `usersService.getUserIdx(userId)` 같은 경량 메서드 추가
- 또는 클라이언트에서 dto.idx를 보내지 않도록 협의

---

### 11. UserProfileController updateMyProfile - getMyProfile 2번 가능성

**파일**: `UserProfileController.java` (Lines 78-89)

**현재 문제**:
- `updateMyProfile`: `getMyProfile` 1회 (idx 확인용) → `updateMyProfile` 내부에서 `findByIdString` 1회
- 같은 트랜잭션 내에서 User를 2번 조회

**해결 방안**: `updateMyProfile`에 idx 검증 로직 통합 또는 `getCurrentUserIdx()` 경량 조회

---

## 🟢 Low Priority

### 12. 데이터베이스 인덱스 추가

**Entity 클래스에 추가 필요**:
```java
@Table(name = "users", indexes = {
    @Index(name = "idx_users_id", columnList = "id"),
    @Index(name = "idx_users_username", columnList = "username"),
    @Index(name = "idx_users_nickname", columnList = "nickname"),
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_deleted", columnList = "is_deleted"),
    @Index(name = "idx_users_refresh_token", columnList = "refresh_token"),
    @Index(name = "idx_users_status", columnList = "status")
})
public class Users { ... }
```

---

### 13. 캐싱 적용

```java
@Cacheable(value = "users", key = "#userId")
public UsersDTO getMyProfile(String userId) { ... }

@Cacheable(value = "users", key = "#idx")
public UsersDTO getUser(Long idx) { ... }

@CacheEvict(value = "users", key = "#userId")
public UsersDTO updateMyProfile(String userId, UsersDTO dto) { ... }
```

---

### 14. 불필요한 save 제거 검토

**파일**: `UsersService.java` (Lines 219-222, 277-278)

- `updateUser`, `updateUserStatus` 등에서 `usersRepository.save(user)` 호출
- JPA Dirty Checking으로 트랜잭션 종료 시 자동 flush되므로 명시적 save 불필요할 수 있음
- 다만 `@Transactional` 적용 범위에 따라 의도적인 flush가 필요할 수 있음 → 검토 필요

---

## 체크리스트

- [ ] `getAllUsers()` 탈퇴 사용자 제외 또는 페이징 강제
- [ ] UsersConverter socialUsers N+1 해결 (트러블슈팅) [상세](./social-users-query/troubleshooting.md)
- [x] AuthService login/refresh 중복 조회 제거 ✅ [시퀀스 다이어그램](./auth-duplicate-query/sequence-diagram.md)
- [x] AdminUserController deleteUser 불필요한 getUser 제거 ✅ [시퀀스 다이어그램](./admin-delete-optimization/sequence-diagram.md)
- [ ] CareReviewService getReviewsByReviewee + getAverageRating 통합
- [ ] OAuth2Service generateUniqueId/Username 최적화
- [ ] UserSanctionService addWarning 중복 findById 제거
- [ ] 인덱스 추가
- [ ] 캐싱 적용 (선택)

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| getAllUsers | 전체 로드 + socialUsers N+1 | 탈퇴 제외 + JOIN FETCH |
| 로그인/Refresh | User 2회 조회 | User 1회 조회 |
| 프로필+리뷰 | 리뷰 쿼리 2회 | 리뷰 쿼리 1회 |
| Admin 삭제 | User+Pet 전체 조회 | 역할만 조회 |
