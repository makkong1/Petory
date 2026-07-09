# UserDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `UserDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/user.md`, 사용자 인증/프로필 아키텍처, user 리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `UserDomainV2.jsx`는 큰 방향이 좋다. User 도메인은 단순 회원 CRUD가 아니라 Petory 전체 보호 API의 신원 기반이다. 인증, OAuth 계정 연결, 이메일 인증, Pet 소유권, 제재 상태, 관리자 사용자 관리까지 묶어서 설명하는 구성이 맞다.

그대로 진행해도 되는 부분:

- `JWT 인증`, `OAuth 계정 연결`, `Pet 소유 검증`, `제재 상태 동기화`, `보안 트레이드오프`를 핵심 축으로 잡은 구성
- Access Token은 JWT, Refresh Token은 DB 컬럼에 저장한다는 설명
- OAuth는 `provider + providerId`를 먼저 보고, 없으면 동일 이메일 계정에 연결한다는 설명
- Pet 상세/수정/삭제/복구에서 JWT subject와 Pet 소유자 `Users.id`를 비교한다는 설명
- 로그인/OAuth 진입 시 제재 상태를 확인하고, 만료 정지는 로그인 시점과 스케줄러에서 해제한다는 설명
- OAuth callback query token, SSE query token, Refresh rotation 미적용 같은 한계를 명시한 점

보완하면 좋은 부분:

- 성능 표의 `21개 -> 4개`, `305ms -> 55ms`, `0.58MB -> 0.13MB`는 일반 로그인 로직 전체 수치라기보다 로그인 응답에 엮였던 채팅방 목록 N+1 측정 시나리오다. 표 아래에 "채팅방 10개/참여자 30명/메시지 200개 기준" 조건을 붙이는 게 정확하다.
- `ReportActionType.SUSPEND_USER`가 `addBan()`으로 연결된다는 JSX 문구는 현재 코드와 다르다. 현재는 `addSuspension(..., AUTO_SUSPENSION_DAYS)`로 연결된다. 이 한계 문구는 제거해야 한다.
- GitHub 아키텍처 링크가 `docs/architecture/user/user-domain-architecture.md`로 되어 있는데, 현재 repo 파일명은 `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`다.
- OAuth2 로그인은 토큰 발급 후 `usersService.getUserById(user.getId())`로 DTO를 다시 조회한다. 일반 로그인/refresh는 이미 로드한 user를 DTO로 변환하도록 개선됐지만, OAuth는 아직 추가 조회가 남아 있다.
- `JwtAuthenticationFilter`는 토큰 검증 후 `UserDetailsService.loadUserByUsername()`을 호출하지만 `Users.status`를 매 요청 검사하지 않는다. 제재 즉시성은 Access TTL에 의존한다는 표현은 유지한다.
- `GET /api/pets/type/{petType}`는 사용자 소유 필터가 없는 타입 기준 전체 조회라 사용자용 API 의도가 맞는지 검토 여지가 있다.

---

## 1. 페이지 상단

### H1

유저 도메인

### 소개 문단

User 도메인은 Petory에서 로그인만 담당하는 얇은 계층이 아니라, 인증, 소셜 계정 연결, 이메일 인증, 반려동물 소유권, 제재 상태를 함께 관리하는 신원 기반 도메인이다. 거의 모든 보호 API의 입구 역할을 하기 때문에 보안 정책과 트레이드오프가 코드에 직접 드러난다. 현재 구조는 Access JWT와 DB 저장 Refresh Token을 조합하고, OAuth 계정은 provider 식별자와 동일 이메일 연결로 통합하며, Pet과 제재 흐름은 다른 도메인이 신뢰할 수 있는 사용자 상태를 제공한다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 그대로 사용 가능하다.

```javascript
const corePillars = [
  "JWT 인증",
  "OAuth 계정 연결",
  "Pet 소유 검증",
  "제재 상태 동기화",
  "보안 트레이드오프",
];
```

---

## 2. `section#intro` - 도메인 개요

### 2-1. 구조 테이블

첨부 JSX의 구조 테이블은 사용 가능하다. 아래처럼 현재 코드 기준으로 조금 더 구체화하면 좋다.

| 항목               | 현재 코드 기준                                                              |
| ------------------ | --------------------------------------------------------------------------- |
| Access 인증        | JWT Bearer, subject는 `Users.id`                                            |
| Access TTL         | `jwt.access-token-expiration-ms`, 기본 15분                                 |
| Refresh 저장       | `Users.refreshToken`, `refreshExpiration`                                   |
| Refresh TTL        | 1일, refresh 성공 시 기존 refresh 유지                                      |
| OAuth 연결         | `provider + providerId` 우선, 없으면 동일 email 계정 연결                   |
| OAuth 신규 ID      | provider/providerId + UUID 8자리 suffix, 충돌 시 최대 3회 재시도            |
| OAuth callback     | access/refresh token을 query parameter로 redirect                           |
| Email 인증         | JWT token + Redis pre-registration 상태 24시간                              |
| Pet 소유 검증      | JWT subject `Users.id`와 `pet.user.id` 비교                                 |
| 제재 상태          | 로그인/OAuth 진입 시 검사, 만료 정지는 로그인 시점 + 자정 스케줄러에서 해제 |
| 관리자 사용자 목록 | paging API, role/status/q 필터                                              |
| soft delete 중복   | 탈퇴 사용자 nickname/username/email은 중복 검사에서 제외                    |

### 2-2. 성능 테이블

첨부 JSX의 성능 수치는 사용 가능하다. 단, 측정 조건을 반드시 함께 둔다.

| 지표                     | Before | After  |
| ------------------------ | ------ | ------ |
| 채팅방 목록 관련 쿼리 수 | 21개   | 4개    |
| 실행 시간                | 305ms  | 55ms   |
| 메모리 사용량            | 0.58MB | 0.13MB |

보조 설명:

- 이 수치는 `docs/troubleshooting/users/login-n-plus-one-issue.md`의 측정값이다.
- 측정 조건은 채팅방 10개, 참여자 30명, 메시지 200개, `entityManager.clear()` 후 조회다.
- 로그인 화면 응답에서 채팅방 목록이 같이 엮였던 맥락의 N+1 개선 수치다.
- User 도메인 자체의 다른 최적화로는 로그인/refresh의 동일 User 중복 조회 제거, 회원가입 중복 검사 3회 -> 1회, socialUsers `@BatchSize(size=50)`, 관리자 삭제 role projection이 있다.

### 2-3. 데이터 흐름 카드

문구:

User 시퀀스는 통합 흐름 페이지에 모은다. 일반 로그인, OAuth 계정 연결, 이메일 인증, Pet 소유 검증, 제재 상태 변경과 만료 해제 흐름을 분리해 볼 수 있게 한다.

내부 링크:

- `/domains/flows?tab=user`
- `/domains/flows?tab=notification`
- `/domains/flows?tab=report`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 아래 내용으로 현재 코드 기준 문구와 스니펫만 다듬으면 된다.

### A. JWT + Refresh

핵심 문구:

일반 로그인은 `AuthenticationManager`로 ID/비밀번호를 인증한 뒤, `AuthService.login()`에서 active user를 다시 조회해 제재 상태를 확인한다. `BANNED`는 즉시 차단하고, `SUSPENDED`는 만료 전이면 차단한다. 만료된 정지는 로그인 시점에 `ACTIVE`로 되돌릴 수 있다. 토큰은 Access JWT와 DB 저장 Refresh Token을 함께 발급한다.

코드 스니펫 후보:

```java
Users user = usersRepository.findActiveByIdString(id)
        .orElseThrow(UserNotFoundException::new);

if (user.getStatus() == UserStatus.BANNED) {
    throw new UserBannedException();
}

if (user.getStatus() == UserStatus.SUSPENDED) {
    if (user.getSuspendedUntil() != null
            && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
        throw new UserSuspendedException(user.getSuspendedUntil());
    }
    user.setStatus(UserStatus.ACTIVE);
    user.setSuspendedUntil(null);
}

String accessToken = jwtUtil.createAccessToken(user.getId());
String refreshToken = jwtUtil.createRefreshToken();

user.setRefreshToken(refreshToken);
user.setRefreshExpiration(LocalDateTime.now().plusDays(1));
user.setLastLoginAt(LocalDateTime.now());
usersRepository.save(user);
```

Refresh:

```java
Users user = usersRepository.findActiveByRefreshToken(refreshToken)
        .orElseThrow(InvalidRefreshTokenException::notFound);

String newAccessToken = jwtUtil.createAccessToken(user.getId());
return new TokenResponse(newAccessToken, refreshToken, usersConverter.toDTO(user));
```

주의 문구:

- 사용자당 refresh token은 최근 1개 문자열만 유지한다.
- refresh 성공 시 refresh token 회전은 하지 않고 Access만 재발급한다.
- 일반 로그인/refresh는 이미 로드한 user를 DTO로 변환해 동일 User 재조회가 제거됐다.

근거:

- `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java`
- `backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java`
- `docs/refactoring/user/user-backend-performance-optimization.md`

### B. OAuth 계정 연결

핵심 문구:

OAuth 로그인은 제공자 계정과 Petory 계정을 분리해서 본다. 먼저 `provider + providerId`로 `SocialUser`를 찾고, 없으면 email로 기존 사용자를 찾아 소셜 계정을 연결한다. 기존 사용자도 없으면 provider/providerId 기반 id와 username에 UUID suffix를 붙여 신규 사용자를 만들고, unique 충돌 시 최대 3회 재시도한다.

코드 스니펫 후보:

```java
Optional<SocialUser> socialUserOpt =
        socialUserRepository.findByProviderAndProviderId(provider, providerId);

if (socialUserOpt.isPresent()) {
    user = socialUserOpt.get().getUser();
} else {
    user = createOrLinkUser(oauth2User, provider, providerId, email, name);
}
```

연결/생성:

```java
Optional<Users> existingUserOpt = usersRepository.findByEmail(email);

if (existingUserOpt.isPresent()) {
    user = existingUserOpt.get();
    updateUserWithSocialData(user, attributes, provider);
} else {
    user = createNewUserWithRetry(provider, providerId, email, name, attributes);
}

socialUserRepository.save(socialUser);
```

주의 문구:

- 신규 소셜 사용자는 `nickname = null`일 수 있고, callback에 `needsNickname=true`가 붙는다.
- 성공 redirect는 access/refresh token을 query parameter로 전달한다.
- 현재 OAuth2Service는 토큰 발급 후 `usersService.getUserById(user.getId())`로 DTO를 다시 조회하므로 일반 로그인보다 추가 조회가 남아 있다.
- Google/Naver 중심으로 설명하고, Kakao 분기는 코드에 있지만 설정/UX 지원 범위는 별도 확인이 필요하다고 적는다.

근거:

- `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java`
- `backend/main/java/com/linkup/Petory/domain/user/handler/OAuth2SuccessHandler.java`
- `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`

### C. Pet 소유 검증

핵심 문구:

Pet은 단순 프로필 하위 데이터가 아니라 Care, MissingPet, Recommendation 등 여러 도메인이 참조하는 사용자 자산이다. 따라서 상세, 수정, 삭제, 복구는 모두 현재 JWT subject와 Pet 소유자의 로그인 ID가 같아야 한다. 이미지 URL은 File 도메인의 `AttachmentFileService`로 `FileTargetType.PET` 첨부와 동기화한다.

코드 스니펫 후보:

```java
private static void assertPetOwnedBy(Pet pet, String ownerUserId) {
    Users owner = pet.getUser();
    String ownerLoginId = owner != null ? owner.getId() : null;
    if (ownerLoginId == null || !ownerLoginId.equals(ownerUserId)) {
        throw UserForbiddenException.ownPetOnly();
    }
}
```

이미지 동기화:

```java
if (dto.getProfileImageUrl() != null) {
    String imageUrl = dto.getProfileImageUrl().trim();
    if (imageUrl.isEmpty()) {
        attachmentFileService.deleteAll(FileTargetType.PET, updated.getIdx());
    } else {
        attachmentFileService.syncSingleAttachment(
                FileTargetType.PET,
                updated.getIdx(),
                imageUrl,
                null);
    }
}
```

주의 문구:

- `PetType.ETC`는 breed가 필수다.
- `GET /api/pets/type/{petType}`는 현재 사용자 소유 필터가 아니라 타입 기준 전체 조회라 사용자용 API 의도와 권한 모델을 재검토할 수 있다.

근거:

- `backend/main/java/com/linkup/Petory/domain/user/service/PetService.java`
- `backend/main/java/com/linkup/Petory/domain/user/controller/PetController.java`

### D. 제재 상태 동기화

핵심 문구:

제재는 `Users.status`, `warningCount`, `suspendedUntil`, `UserSanction` 이력으로 나뉜다. 경고는 DB 원자적 update로 증가하고, 경고 3회 이상이면 자동으로 3일 정지가 적용된다. 정지 만료 해제는 로그인/OAuth 진입 시점과 매일 자정 스케줄러가 함께 처리한다.

코드 스니펫 후보:

```java
private static final int WARNING_THRESHOLD = 3;
private static final int AUTO_SUSPENSION_DAYS = 3;

usersRepository.incrementWarningCount(userId);

user = usersRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);

if (user.getWarningCount() >= WARNING_THRESHOLD) {
    addSuspension(userId,
            String.format("경고 %d회 누적으로 인한 자동 이용제한", user.getWarningCount()),
            adminId,
            reportId,
            AUTO_SUSPENSION_DAYS);
}
```

Report 연결:

```java
switch (actionType) {
    case WARN_USER -> addWarning(userId, reason, adminId, reportId);
    case SUSPEND_USER -> addSuspension(userId, reason, adminId, reportId, AUTO_SUSPENSION_DAYS);
    default -> { }
}
```

스케줄러:

```java
@Scheduled(cron = "0 0 0 * * *")
public void releaseExpiredSuspensions() {
    userSanctionService.releaseExpiredSuspensions();
}
```

주의 문구:

- JSX의 `SUSPEND_USER -> addBan()` 문구는 현재 코드 기준으로 제거한다.
- `addWarning()`은 경고 증가 후 최신 warning count 확인을 위해 user를 다시 조회한다. 이건 남은 최적화 포인트다.

근거:

- `backend/main/java/com/linkup/Petory/domain/user/service/UserSanctionService.java`
- `backend/main/java/com/linkup/Petory/domain/user/scheduler/UserSanctionScheduler.java`
- `docs/architecture/user/신고 및 제재 시스템 아키텍처.md`

### E. 보안 트레이드오프

핵심 문구:

현재 인증 구조는 사이드 프로젝트 수준에서 단순성과 운영 가능성을 우선한 선택이 섞여 있다. Access Token은 짧은 TTL의 JWT로 무상태 검증하지만, 토큰 유효 기간 중 `BANNED`나 `SUSPENDED`를 매 요청 재평가하지 않는다. Refresh Token은 DB에 저장하지만 회전이나 재사용 탐지는 없다. OAuth와 SSE 일부 경로는 query parameter token을 허용한다.

코드 근거:

```java
// JwtAuthenticationFilter
String authorizationHeader = request.getHeader("Authorization");
if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
    token = jwtUtil.extractTokenFromHeader(authorizationHeader);
}

if (token == null) {
    token = request.getParameter("token");
}
```

한계 문구:

- Access JWT 유효 기간 중 제재 상태 즉시 반영은 Access TTL에 의존한다.
- Refresh Token 회전 미적용으로 탈취 시 TTL 내 재사용 가능성이 있다.
- OAuth callback query parameter로 토큰이 전달되어 브라우저 history, 로그, referer 노출 표면이 있다.
- SSE 등 헤더 사용이 어려운 경로를 위해 query token을 허용한다.
- JWT 검증 실패 로그 레벨 정리도 백로그에 남아 있다.

근거:

- `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java`
- `docs/refactoring/JWT-토큰-리팩토링-백로그.md`

### F. 휴면 계정

핵심 문구:

탈퇴(`isDeleted`)와 별개로, 1년간 로그인하지 않은 계정은 매일 자정 배치가 `isDormant`/`dormantAt`으로 휴면 전환한다. 제재 상태(`UserStatus`)와는 독립적인 필드라 "정지 중이면서 동시에 휴면"도 표현 가능하다. 휴면 계정은 일반 로그인에서만 차단하며(OAuth2 제외), 본인이 로그인을 재시도해 확인하면 즉시 재활성화된다 — 관리자가 대신 풀어줄 수 없다.

`lastLoginAt`은 개인로그인/OAuth2 로그인이 같은 `Users` row를 공유해서 갱신하므로, 계정을 연동해 둔 사용자가 한쪽 채널만 꾸준히 써도 휴면 전환 자체가 발생하지 않는다. 이미 휴면인 상태에서 OAuth2로 로그인하면 차단 없이 플래그만 조용히 해제되는데, 이는 OAuth2가 이미 제3자 인증을 거쳐 로그인 성공 자체를 본인 확인으로 볼 수 있기 때문이다 (비밀번호 탈취 위험이 있는 개인로그인과는 신뢰 수준이 다름). OAuth2 자체의 차단/재활성화 확인 플로우는 작업량 대비 이득이 크지 않아 의도적으로 범위에서 제외했다.

코드 스니펫 후보:

```java
@Modifying
@Query("UPDATE Users u SET u.isDormant = true, u.dormantAt = :now " +
       "WHERE u.isDormant = false AND u.isDeleted = false AND (" +
       "  (u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :cutoff) OR " +
       "  (u.lastLoginAt IS NULL AND u.createdAt < :cutoff)" +
       ")")
int markDormantUsers(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
```

로그인 차단 + 재활성화:

```java
if (Boolean.TRUE.equals(user.getIsDormant())) {
    if (!confirmReactivate) {
        throw new UserDormantException();
    }
    user.setIsDormant(false);
    user.setDormantAt(null);
}
```

스케줄러:

```java
@Scheduled(cron = "0 0 0 * * *")
public void markDormantUsers() {
    userDormantService.markDormantUsers();
}
```

주의 문구:

- 새 엔드포인트를 만들지 않고 기존 `POST /api/auth/login`에 `confirmReactivate` 필드만 추가했다. 비밀번호는 컨트롤러의 `authenticationManager.authenticate()`에서 이미 검증되므로 재활성화 시 추가 인증 절차가 없다.
- 가입 후 한 번도 로그인 안 한 계정은 `lastLoginAt`이 null이라 `createdAt` 기준으로 판정한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/user/service/UserDormantService.java`
- `backend/main/java/com/linkup/Petory/domain/user/scheduler/UserDormantScheduler.java`
- `backend/main/java/com/linkup/Petory/domain/user/exception/UserDormantException.java`
- `docs/superpowers/specs/2026-07-09-dormant-account-design.md`

---

## 4. `section#limits` - 한계와 운영 메모

첨부 JSX의 한계 섹션은 유지하되, 아래처럼 현재 코드 기준으로 보정한다.

- Access JWT 유효 동안 `BANNED`/`SUSPENDED` 상태를 매 요청 재평가하지 않는다. 제재 즉시성은 Access TTL에 의존한다.
- Refresh Token 회전은 없다. 사용자당 최근 1개 문자열만 DB에 저장하고, refresh 성공 시 기존 refresh를 유지한다.
- OAuth 성공 후 access/refresh token을 query parameter로 redirect한다.
- `JwtAuthenticationFilter`가 SSE 등을 위해 `token` query parameter도 허용한다.
- OAuth2Service는 토큰 발급 후 DTO 생성을 위해 `usersService.getUserById()`를 다시 호출한다. 일반 로그인/refresh와 달리 추가 조회가 남아 있다.
- OAuth2 경로의 제재 예외는 일반 로그인처럼 도메인 예외로 내려가지 않고 redirect `error` query로 전달된다.
- `GET /api/pets/type/{petType}`는 사용자 소유 필터가 없는 타입 기준 전체 조회다.
- `UserSanctionService.addWarning()`은 경고 증가 후 최신 count 확인을 위해 user를 다시 조회한다.
- JSX의 `SUSPEND_USER -> addBan()` 한계 문구는 제거한다. 현재 코드는 `SUSPEND_USER -> addSuspension(..., 3일)`이다.

---

## 5. `section#docs` - 연결 문서와 소스

### 내부 페이지 링크

- `/domains/user/optimization`
- `/domains/user/refactoring`
- `/domains/flows?tab=user`
- `/domains/report`
- `/domains/file`

### GitHub 소스 링크 후보

첨부 JSX의 아키텍처 문서 링크는 현재 파일명 기준으로 수정하는 게 좋다.

```javascript
const PETORY_USER_ARCH_DOC =
  "https://github.com/makkong1/Petory/blob/dev/docs/architecture/user/%EC%82%AC%EC%9A%A9%EC%9E%90%20%EC%9D%B8%EC%A6%9D%20%EB%B0%8F%20%ED%94%84%EB%A1%9C%ED%95%84%20%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98.md";
const PETORY_AUTH_SERVICE =
  "https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java";
const PETORY_OAUTH2_SERVICE =
  "https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java";
const PETORY_PET_SERVICE =
  "https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/user/service/PetService.java";
const PETORY_USER_SANCTION_SERVICE =
  "https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/user/service/UserSanctionService.java";
const PETORY_JWT_FILTER =
  "https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java";
```

### 문서 근거

- `docs/domains/user.md`
- `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`
- `docs/architecture/user/이메일 인증 시스템 아키텍처.md`
- `docs/architecture/user/신고 및 제재 시스템 아키텍처.md`
- `docs/refactoring/user/user-backend-performance-optimization.md`
- `docs/troubleshooting/users/login-n-plus-one-issue.md`
- `docs/troubleshooting/users/soft-delete-nickname-reuse.md`
- `docs/refactoring/JWT-토큰-리팩토링-백로그.md`

---

## 6. JSX 반영 체크리스트

`UserDomainV2.jsx`를 고칠 때 우선순위:

1. 전체 구조와 corePillars는 유지한다.
2. 성능 표 아래에 채팅방 목록 N+1 측정 조건을 추가한다.
3. `ReportActionType.SUSPEND_USER -> addBan()` 문구는 제거하고, 현재 `addSuspension(..., 3일)`으로 수정한다.
4. GitHub 아키텍처 문서 링크를 실제 파일명인 `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`로 바꾼다.
5. OAuth2Service의 DTO 추가 조회는 한계 또는 리팩토링 포인트로 추가한다.
6. `GET /api/pets/type/{petType}` 전체 타입 조회 한계를 추가한다.
7. Access JWT 상태 재평가 없음, Refresh 회전 없음, OAuth/SSE query token 노출은 한계 섹션에 유지한다.
