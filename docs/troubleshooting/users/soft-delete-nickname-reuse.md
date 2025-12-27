# 탈퇴한 사용자(Soft Delete)의 닉네임/username/email 재사용 불가 문제

## 📋 요약

**문제**: 탈퇴한 사용자(Soft Delete)의 닉네임, username, email을 다른 사용자가 재사용할 수 없음

**해결 방안**: Repository 메서드에 `isDeleted = false` 조건 추가

**상태**: ✅ 해결 완료

---

## 1. 문제 상황

### 1.1 발생 시나리오

1. 사용자 A가 "nickname1"로 가입 → 성공
2. 사용자 A가 탈퇴 (`isDeleted = true`로 변경)
3. 사용자 B가 "nickname1"로 가입 시도
4. `findByNickname("nickname1")`이 탈퇴한 사용자 A를 반환
5. 중복 체크에서 예외 발생 → 가입 실패 ❌

**결과**: 탈퇴한 사용자의 닉네임을 재사용할 수 없음!

### 1.2 문제 코드

**위치**: `UsersRepository.findByNickname()`, `findByUsername()`, `findByEmail()`

**Before (문제 코드)**:
```java
// UsersRepository.java
Optional<Users> findByUsername(String username);  // ⚠️ isDeleted 필터링 없음!
Optional<Users> findByNickname(String nickname);  // ⚠️ isDeleted 필터링 없음!
Optional<Users> findByEmail(String email);        // ⚠️ isDeleted 필터링 없음!

// UsersService.java:107-110
usersRepository.findByNickname(nickname)
    .ifPresent(existingUser -> {
        throw new RuntimeException("이미 사용 중인 닉네임입니다.");
    });
// ⚠️ 탈퇴한 사용자도 조회됨!
```

### 1.3 영향

- **탈퇴한 사용자의 닉네임/username/email이 영구히 사용 불가능**
- 사용자가 탈퇴 후 재가입 시 기존 닉네임 사용 불가
- 닉네임 고갈 가능성 (특히 짧은/인기 있는 닉네임)
- 사용자 경험 저하

---

## 2. 원인 분석

### 2.1 Soft Delete 방식의 특성

- 물리적 삭제가 아닌 논리적 삭제 (`isDeleted = true`)
- DB에 데이터는 남아있지만 비활성화 상태
- 일반적으로 활성 사용자만 조회해야 함

### 2.2 현재 구현의 문제점

1. **Repository 메서드에 isDeleted 필터링 없음**
   - `findByNickname()` 등이 모든 사용자를 조회 (탈퇴한 사용자 포함)
   
2. **서비스 레이어에서 필터링 없음**
   - 중복 체크 시 탈퇴한 사용자도 고려됨

3. **일관성 없는 조회 로직**
   - 다른 조회 메서드는 활성 사용자만 조회해야 하는데, 중복 체크만 탈퇴한 사용자도 포함

---

## 3. 해결 방안

### 3.1 Repository 메서드에 isDeleted 필터링 추가 (구현 완료)

**위치**: `UsersRepository.java`

**After (해결 코드)**:
```java
// 탈퇴하지 않은 사용자만 조회 (Soft Delete 필터링)
@Query("SELECT u FROM Users u WHERE u.username = :username AND (u.isDeleted = false OR u.isDeleted IS NULL)")
Optional<Users> findByUsername(@Param("username") String username);

@Query("SELECT u FROM Users u WHERE u.nickname = :nickname AND (u.isDeleted = false OR u.isDeleted IS NULL)")
Optional<Users> findByNickname(@Param("nickname") String nickname);

@Query("SELECT u FROM Users u WHERE u.email = :email AND (u.isDeleted = false OR u.isDeleted IS NULL)")
Optional<Users> findByEmail(@Param("email") String email);
```

**장점**:
- ✅ 코드 일관성 유지
- ✅ 실수로 탈퇴한 사용자를 조회하는 것을 방지
- ✅ 모든 중복 체크 로직에서 자동으로 적용
- ✅ 명시적이고 이해하기 쉬움

### 3.2 대안: 서비스에서 필터링

```java
// 서비스에서 필터링 (비추천)
usersRepository.findByNickname(nickname)
    .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
    .ifPresent(existingUser -> {
        throw new RuntimeException("이미 사용 중인 닉네임입니다.");
    });
```

**단점**:
- ❌ 모든 서비스 메서드에서 반복적으로 필터링 필요
- ❌ 실수로 필터링을 빼먹을 수 있음
- ❌ 코드 중복

---

## 4. 해결 완료

### 4.1 구현 내역

**수정 파일**: 
- `backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java`

**수정 내용**:
1. ✅ `findByUsername()` 메서드에 `isDeleted = false` 조건 추가
2. ✅ `findByNickname()` 메서드에 `isDeleted = false` 조건 추가
3. ✅ `findByEmail()` 메서드에 `isDeleted = false` 조건 추가

**주의사항**:
- `(u.isDeleted = false OR u.isDeleted IS NULL)` 조건 사용
  - `isDeleted`가 null일 수도 있어서 null 체크 포함
  - 기존 데이터와의 호환성 고려

### 4.2 Before/After 비교

| 항목 | Before | After |
|------|--------|-------|
| **findByNickname()** | 탈퇴한 사용자 포함 조회 | ✅ 탈퇴하지 않은 사용자만 조회 |
| **findByUsername()** | 탈퇴한 사용자 포함 조회 | ✅ 탈퇴하지 않은 사용자만 조회 |
| **findByEmail()** | 탈퇴한 사용자 포함 조회 | ✅ 탈퇴하지 않은 사용자만 조회 |
| **중복 체크** | 탈퇴한 사용자도 고려 | ✅ 활성 사용자만 고려 |
| **닉네임 재사용** | 불가능 | ✅ 가능 |

### 4.3 테스트 결과

**테스트 파일**: `UsersServiceConcurrencyTest.testDeletedUserNicknameReuseIssue()`

**Before (문제 상황)**:
```
❌ 사용자 B 가입 실패: 이미 사용 중인 닉네임입니다.
⚠️ 문제 확인: 탈퇴한 사용자의 닉네임을 재사용할 수 없음!
```

**After (해결 후)**:
```
✅ 사용자 A 가입 성공
✅ 사용자 A 탈퇴 완료
✅ 사용자 B 가입 성공: 탈퇴한 사용자의 닉네임 재사용 가능
```

---

## 5. 관련 문제와 함께 해결한 사항

### 5.1 닉네임 중복 체크 Race Condition 예외 처리

**위치**: `UsersService.createUser()`

**추가 내용**:
```java
try {
    saved = usersRepository.save(user);
} catch (DataIntegrityViolationException e) {
    // DB Unique 제약조건 위반 (Race Condition 발생 시)
    String errorMessage = e.getMessage();
    if (errorMessage != null) {
        if (errorMessage.contains("nickname") || errorMessage.contains("nick_name")) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        } else if (errorMessage.contains("username") || errorMessage.contains("user_name")) {
            throw new RuntimeException("이미 사용 중인 사용자명입니다.");
        } else if (errorMessage.contains("email")) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        } else if (errorMessage.contains("id")) {
            throw new RuntimeException("이미 사용 중인 아이디입니다.");
        }
    }
    throw new RuntimeException("이미 사용 중인 정보가 있습니다. 다른 값을 사용해주세요.", e);
}
```

**효과**:
- ✅ 동시 요청 시 DB Unique 제약조건 위반 예외를 적절히 처리
- ✅ 사용자에게 명확한 에러 메시지 제공

---

## 6. 핵심 포인트

### 개선 효과

1. **닉네임 재사용 가능**: 탈퇴한 사용자의 닉네임을 다른 사용자가 재사용할 수 있음
2. **코드 일관성**: Repository 레벨에서 필터링하여 일관성 유지
3. **유지보수성**: 서비스 레이어에서 별도 필터링 불필요

### 주의사항

- `isDeleted IS NULL` 조건 포함: 기존 데이터 호환성 고려
- 다른 Repository 메서드도 확인 필요: 탈퇴한 사용자를 조회해야 하는 경우 예외 처리
- 로그인/인증 관련 조회: 탈퇴한 사용자는 로그인 불가해야 함 (별도 확인 필요)

---

## 7. 참고 자료

- 테스트 코드: `backend/test/java/com/linkup/Petory/domain/user/service/UsersServiceConcurrencyTest.java`
- 트러블슈팅 체크리스트: `docs/troubleshooting/도메인별_트러블슈팅_체크리스트.md` (3. User 도메인)

