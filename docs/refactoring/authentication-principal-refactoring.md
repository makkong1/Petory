# 인증 주체 조회 리팩터링 점검

## 목적

현재 인증 구조는 JWT 필터에서 인증을 만들고도, 컨트롤러와 서비스에서 현재 사용자 정보를 다시 조회하거나 직접 `SecurityContextHolder`를 읽는 코드가 섞여 있다. 이 문서는 특정 도메인 리팩터링이 아니라, **인증 사용자 식별 방식과 인증 관련 공통 코드 정리**를 위한 점검 기록이다.

---

## 현재 적용 상태

2026-06-21 기준으로 1차 리팩터링 일부가 적용됐다.

완료:

- `CustomUserDetails` 추가
- `UsersDetailsServiceImpl`이 Spring Security 기본 `User` 대신 `CustomUserDetails` 반환
- `AuthenticatedUserIdResolver`가 `CustomUserDetails` principal이면 DB 재조회 없이 `idx` 반환
- `UserProfileController`, `PetController`의 주요 사용자 API가 `@AuthenticationPrincipal CustomUserDetails` 사용
- WebSocket 인증도 `UserDetailsService`를 거치므로 인증 객체의 principal은 `CustomUserDetails`

남은 과제:

- `JwtAuthenticationFilter`는 아직 `validateToken`, `isTokenExpired`, `getIdFromToken`으로 JWT를 여러 번 파싱한다.
- `AuthController.validate`, `AuthController.logout`은 아직 Authorization header를 직접 파싱한다.
- 여러 도메인 컨트롤러는 `@AuthenticationPrincipal`로 직접 전환하지 않고 `AuthenticatedUserIdResolver` 호환 경로를 유지한다.
- 일부 서비스 레이어에는 아직 `SecurityContextHolder` 직접 접근이 남아 있다.
- 이메일 인증 검사는 도메인별 서비스에 흩어져 있다.

---

## 리팩터링 전 문제 흐름

```text
HTTP 요청
  -> JwtAuthenticationFilter
  -> JWT subject에서 로그인 ID 추출
  -> UsersDetailsServiceImpl.loadUserByUsername(loginId)
  -> DB에서 Users 조회
  -> Spring Security 기본 User principal 생성
  -> SecurityContext에 Authentication 저장
  -> 컨트롤러/서비스에서 현재 사용자 정보 필요
  -> 다시 SecurityContextHolder 또는 AuthenticatedUserIdResolver 사용
```

Access Token의 subject는 숫자 PK가 아니라 로그인용 문자열 ID다.

```java
// JwtUtil#createAccessToken
Jwts.builder()
        .subject(id)
```

리팩터링 전 `UsersDetailsServiceImpl`은 사용자 조회 후 Spring Security 기본 `User`를 반환했다.

```java
return new org.springframework.security.core.userdetails.User(
        user.getId(),
        user.getPassword(),
        getAuthorities(user));
```

이 구조에서는 principal 안에 `Users.idx`, `emailVerified`, `status`, `role` 같은 Petory 사용자 정보가 없었다. 그래서 숫자 PK가 필요할 때 다시 DB를 조회했다.

현재는 `UsersDetailsServiceImpl`이 `CustomUserDetails.from(user)`를 반환하므로 JWT 보호 API의 principal에 `idx`, `loginId`, `role`, `emailVerified`, `status`가 들어간다.

---

## 발견된 문제

### 1. 매 요청 인증 단계에서 이미 DB 조회를 한다

`JwtAuthenticationFilter`는 토큰 검증 후 `UserDetailsService`를 호출한다.

```java
UserDetails userDetails = userDetailsService.loadUserByUsername(id);
```

이 호출은 `UsersDetailsServiceImpl -> usersRepository.findActiveByIdString(id)`로 이어진다. 즉 인증 필터 단계에서 이미 사용자 row를 한 번 조회한다.

### 2. `JwtAuthenticationFilter`가 JWT를 여러 번 파싱·검증한다

현재 필터는 같은 토큰에 대해 검증과 파싱을 반복한다.

```java
if (token != null && jwtUtil.validateToken(token)) {
    String id = jwtUtil.getIdFromToken(token);
    ...
    if (jwtUtil.validateToken(token) && !jwtUtil.isTokenExpired(token)) {
        ...
    }
}
```

`JwtUtil.validateToken()`은 내부에서 `parseSignedClaims(token)`을 호출한다. JJWT는 이 과정에서 서명뿐 아니라 만료도 함께 검증하며, 만료 토큰이면 예외가 발생한다.

현재 정상 토큰 하나를 처리할 때의 파싱 흐름은 대략 다음과 같다.

```text
validateToken(token)   -> parseSignedClaims
getIdFromToken(token)  -> parseSignedClaims
validateToken(token)   -> parseSignedClaims
isTokenExpired(token)  -> parseSignedClaims
```

즉 인증 필터 한 번에 최대 4회 파싱한다. 기능상 치명적인 버그는 아니지만, 모든 요청에서 실행되는 필터라 불필요한 비용과 로그 노이즈를 만든다.

개선 방향은 유효한 Claims를 한 번만 파싱하고, 그 Claims에서 subject를 읽는 구조다.

```text
Before:
  validate -> getId -> validate -> isExpired

After:
  parseValidClaims once -> subject 추출 -> UserDetails 로드
```

### 3. `AuthenticatedUserIdResolver`가 같은 요청에서 다시 DB 조회했다

`AuthenticatedUserIdResolver.requireCurrentUserIdx()`는 `authentication.getName()`으로 로그인 ID를 얻고 다시 사용자를 조회한다.

```java
String loginId = authentication.getName();
Users user = usersRepository.findActiveByIdString(loginId)
        .orElseThrow(...);
return user.getIdx();
```

리팩터링 전에는 `JwtAuthenticationFilter`를 거친 요청에서 컨트롤러가 `requireCurrentUserIdx()`를 쓰면, 같은 사용자에 대해 최소 2회 조회가 발생했다.

현재 resolver는 principal이 `CustomUserDetails`면 `userDetails.getIdx()`를 바로 반환한다. principal이 다른 타입일 때만 이전 방식으로 loginId를 다시 조회한다.

### 4. 현재 사용자 조회 방식이 여러 개로 갈라져 있다

| 방식 | 사용 예 | 문제 |
| --- | --- | --- |
| `@AuthenticationPrincipal CustomUserDetails` | `UserProfileController`, `PetController`, `PetCoinController`, Board 일부, Location review 일부 | 권장 경로. 다만 전 도메인에 일괄 적용되지는 않음 |
| `AuthenticatedUserIdResolver.requireCurrentUserIdx()` | Care, Chat, Notification, Admin, Recommendation 계열 컨트롤러 | 호환 브리지. `CustomUserDetails`면 DB 재조회는 없지만 접근 방식은 아직 분산됨 |
| `SecurityContextHolder.getContext().getAuthentication()` 직접 사용 | Board/Comment/MissingPet/Care/Location 서비스 일부, WebSocket 인터셉터 | 인증 주체 접근 방식이 각 클래스에 중복 |
| Authorization 헤더 직접 파싱 | `AuthController.validate`, `AuthController.logout` | 이미 필터가 처리하는 JWT 파싱 로직과 중복 |
| WebSocket 인터셉터 자체 인증 처리 | `WebSocketAuthenticationInterceptor`, `WebSocketAuthChannelInterceptor` | HTTP 인증과 유사 로직이 따로 존재 |

### 5. 서비스 레이어에 SecurityContext 접근이 섞여 있다

일부 서비스는 관리자 여부 확인이나 현재 사용자 조회를 위해 직접 `SecurityContextHolder`를 읽는다.

```text
BoardService
CommentService
MissingPetBoardService
MissingPetCommentService
CareRequestService
LocationServiceService
```

서비스가 SecurityContext에 직접 의존하면 테스트가 어려워지고, “현재 사용자 정보는 컨트롤러에서 받아 서비스로 전달한다”는 경계가 흐려진다.

### 6. 이메일 인증 검사가 도메인별로 반복된다

`emailVerified` 검사는 여러 서비스에 직접 들어가 있다.

```text
CareRequestService
MeetupService
BoardService
CommentService
MissingPetBoardService
LocationServiceReviewService
UsersService
EmailVerificationService
```

이메일 인증은 로그인 인증과는 별개인 “기능 사용 자격”에 가깝다. 지금처럼 도메인 서비스마다 직접 예외를 던지는 방식은 동작은 명확하지만, 인증 정책이 흩어진다.

### 7. OAuth2 로그인 흐름은 별도 principal 타입을 거친다

OAuth2 로그인 성공 시점의 principal은 `CustomUserDetails`가 아니라 `OAuth2User`다.

```text
OAuth2 provider callback
  -> OAuth2UserProviderRouter
  -> GoogleOAuth2UserService 또는 NaverOAuth2UserService
  -> DefaultOAuth2User 반환
  -> OAuth2SuccessHandler
  -> OAuth2Service.processOAuth2Login()
  -> Petory JWT 발급
  -> 프론트 redirect
```

즉 `CustomUserDetails` 도입은 OAuth2 성공 핸들러의 `Authentication` principal을 대체하는 작업이 아니다. OAuth2 성공 후 프론트가 받은 JWT로 다시 API를 호출할 때, 그 JWT 요청이 `JwtAuthenticationFilter -> UsersDetailsServiceImpl -> CustomUserDetails` 흐름을 타게 만드는 것이 1차 목표다.

| 구간 | principal 타입 | 정리 방향 |
| --- | --- | --- |
| OAuth2 provider 콜백 처리 중 | `OAuth2User` | provider attributes 처리 유지 |
| OAuth2 성공 후 Petory JWT 발급 | OAuth2 인증 객체 기반 | `OAuth2Service`가 기존처럼 `user.getId()` subject로 JWT 발급 |
| JWT로 API 호출 | `CustomUserDetails` | 일반 로그인과 OAuth2 로그인 모두 동일 principal 사용 |

컨트롤러에서 `@AuthenticationPrincipal CustomUserDetails`를 쓰는 것은 JWT API 요청 기준이다. OAuth2 success handler 내부에서 같은 타입을 기대하면 안 된다.

---

## 개선 방향

### 목표 구조

```text
JWT 요청
  -> JwtAuthenticationFilter
  -> UsersDetailsServiceImpl.loadUserByUsername(loginId)
  -> DB에서 Users 조회
  -> CustomUserDetails 생성
       - idx
       - loginId
       - role
       - emailVerified
       - status
  -> SecurityContext에 저장
  -> 컨트롤러에서 @AuthenticationPrincipal CustomUserDetails 사용
```

핵심은 **인증 필터에서 이미 조회한 사용자 정보를 SecurityContext principal에 보관**하는 것이다. 그러면 컨트롤러는 `@AuthenticationPrincipal`로 바로 `idx`를 꺼낼 수 있다.

```java
@GetMapping("/me")
public ResponseEntity<?> getMe(@AuthenticationPrincipal CustomUserDetails user) {
    Long userIdx = user.getIdx();
    ...
}
```

---

## 1차 리팩터링 계획

### 1. `CustomUserDetails` 추가

위치 후보:

```text
backend/main/java/com/linkup/Petory/global/security/CustomUserDetails.java
```

필드 후보:

```java
private final Long idx;
private final String loginId;
private final String password;
private final Role role;
private final Boolean emailVerified;
private final UserStatus status;
private final Collection<? extends GrantedAuthority> authorities;
```

권장 메서드:

```java
public Long getIdx();
public String getLoginId();
public Role getRole();
public boolean isEmailVerified();
public boolean isAdmin();
public boolean isMaster();
```

`UserDetails#getUsername()`은 Spring Security 계약상 로그인 ID를 반환하면 된다.

### 2. `UsersDetailsServiceImpl` 수정

현재:

```java
return new org.springframework.security.core.userdetails.User(...);
```

변경:

```java
return CustomUserDetails.from(user);
```

이렇게 하면 JWT 필터, 로그인 인증, WebSocket 인증이 같은 `UserDetailsService`를 통해 동일한 principal 모델을 쓴다.

### 3. `JwtAuthenticationFilter`의 토큰 검증을 1회 파싱 구조로 정리

`CustomUserDetails`를 넣는 김에 필터의 중복 검증도 줄인다. 목표는 토큰을 한 번만 파싱해 Claims를 얻고, 같은 Claims에서 subject를 꺼내는 것이다.

후보 API:

```java
Claims claims = jwtUtil.parseValidClaims(token);
String loginId = claims.getSubject();
```

또는 `JwtUtil`이 subject만 반환하되 내부 파싱을 한 번만 수행하게 해도 된다.

```java
String loginId = jwtUtil.getValidSubject(token);
```

주의할 점:

- `validateToken()`이 이미 만료 검사를 포함하므로 `validateToken() && !isTokenExpired()` 조합은 중복이다.
- 검증 실패 로그 레벨 정리는 `docs/refactoring/JWT-토큰-리팩토링-백로그.md`의 “검증 실패 로깅” 항목과 같이 봐야 한다.
- 쿼리 파라미터 `token` 지원은 SSE/WebSocket 영향이 있으므로 제거는 별도 과제로 분리한다.

### 4. `AuthenticatedUserIdResolver`를 호환 브리지로 변경

컨트롤러를 한 번에 전부 수정하면 변경 범위가 커진다. 먼저 resolver 내부만 바꿔도 중복 조회를 줄일 수 있다.

```java
Object principal = authentication.getPrincipal();
if (principal instanceof CustomUserDetails userDetails) {
    return userDetails.getIdx();
}

// 하위 호환 fallback
String loginId = authentication.getName();
return usersRepository.findActiveByIdString(loginId)
        .orElseThrow(...)
        .getIdx();
```

이 단계만 해도 기존 `AuthenticatedUserIdResolver` 사용처는 유지하면서 DB 재조회 대부분을 제거할 수 있다.

### 5. OAuth2 발급 토큰도 같은 JWT principal 경로를 타는지 확인

OAuth2 성공 핸들러는 `OAuth2User`를 받아 `OAuth2Service.processOAuth2Login()`으로 Petory JWT를 발급한다.

```java
TokenResponse tokenResponse = oAuth2Service.processOAuth2Login(oAuth2User, provider);
```

이후부터는 일반 JWT 요청과 동일해야 한다.

```text
OAuth2 로그인 성공
  -> accessToken 발급(subject = user.getId())
  -> 프론트 저장
  -> API 요청 Authorization: Bearer ...
  -> JwtAuthenticationFilter
  -> UsersDetailsServiceImpl
  -> CustomUserDetails
```

검증 포인트:

- 일반 로그인과 OAuth2 로그인으로 발급된 Access Token의 subject 의미가 같아야 한다. 현재 둘 다 `user.getId()`다.
- OAuth2 전용 `DefaultOAuth2User`를 `CustomUserDetails`로 억지 변환하지 않는다.
- OAuth2 성공 후 JWT로 호출한 API에서 `@AuthenticationPrincipal CustomUserDetails`가 정상 주입되는지 통합 테스트로 확인한다.

### 6. 컨트롤러를 점진적으로 `@AuthenticationPrincipal`로 교체

우선순위가 높은 곳:

| 우선순위 | 대상 | 상태/이유 |
| --- | --- | --- |
| 1 | `UserProfileController`, `PetController`, `PetCoinController` | 적용됨. `@AuthenticationPrincipal CustomUserDetails`로 현재 사용자 식별 |
| 2 | `NotificationController`, `FcmTokenController` | resolver 사용 중. 요청마다 단순 userIdx만 필요 |
| 3 | `CareRequestController`, `ConversationController`, `ChatMessageController` | resolver 사용 중. 점진 전환 후보 |
| 4 | Admin 컨트롤러 | resolver 사용 중. admin/master idx 기록 용도 |

예시:

```java
public ResponseEntity<?> getMyBalance(@AuthenticationPrincipal CustomUserDetails user) {
    Long userIdx = user.getIdx();
}
```

### 7. 서비스 레이어의 `SecurityContextHolder` 접근 제거

서비스는 인증 컨텍스트를 직접 읽기보다, 컨트롤러에서 `currentUserIdx`, `role`, `isAdmin` 등을 인자로 받는 방식이 낫다.

```text
Before:
  service 내부에서 SecurityContextHolder 접근

After:
  controller에서 인증 주체 추출
  service.method(..., currentUserIdx, currentUserRole)
```

---

## 2차 검토 과제

### AuthController의 직접 JWT 파싱 정리

현재 `/api/auth/validate`, `/api/auth/logout`은 Authorization 헤더를 직접 파싱한다.

```java
String token = jwtUtil.extractTokenFromHeader(authHeader);
String id = jwtUtil.getIdFromToken(token);
```

개선 후보:

- `logout`은 `@AuthenticationPrincipal CustomUserDetails user`를 받게 변경
- `validate`는 인증 필터를 통과한 principal을 반환하거나, 공개 검증 API로 유지할지 결정

### 이메일 인증 정책 중앙화

이메일 인증은 `isAuthenticated()`와 별개의 기능 권한이다. 반복 검사를 줄이려면 아래 중 하나를 검토할 수 있다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| 서비스 공통 헬퍼 `EmailVerificationGuard.requireVerified(user, purpose)` | 적용이 단순하고 명시적 | 호출 누락 가능 |
| 커스텀 애노테이션 `@RequireEmailVerified(purpose = ...)` + AOP | 정책이 한 곳에 모임 | AOP 복잡도 증가 |
| Method Security 표현식 확장 | `@PreAuthorize`와 통합 가능 | 구현 난도와 디버깅 비용 증가 |

현재 단계에서는 `CustomUserDetails`에 `emailVerified`를 담고, 반복되는 검사를 공통 헬퍼로 먼저 빼는 정도가 안전하다.

### JWT claim 확장 여부

`idx`, `role`을 JWT claim에 넣으면 `JwtAuthenticationFilter`의 DB 조회까지 줄일 수 있다.

다만 이 경우 권한 변경, 정지, 탈퇴 상태가 Access Token 만료 전까지 늦게 반영될 수 있다. Petory는 제재/차단/관리자 권한이 있으므로, 당장 claim-only 인증으로 가기보다는 **매 요청 DB 조회는 유지하고 중복 조회만 제거**하는 편이 안전하다.

---

## 기대 효과

| 항목 | 현재 | 개선 후 |
| --- | --- | --- |
| 현재 사용자 PK 조회 | loginId로 DB 재조회 | principal에서 바로 조회 |
| 컨트롤러 인증 코드 | resolver, SecurityContextHolder, 직접 JWT 파싱 혼재 | `@AuthenticationPrincipal` 중심 |
| principal 정보 | Spring 기본 `User`: username/password/authorities | Petory 전용 `CustomUserDetails`: idx/role/status/emailVerified 포함 |
| 서비스 테스트 | SecurityContext 의존 코드 존재 | 인증 정보를 인자로 받아 테스트 쉬움 |
| 이메일 인증 검사 | 도메인별 반복 | 공통 guard 또는 애노테이션으로 이동 가능 |

---

## 기존 방식 대비 장단점

### 기존 방식: Spring 기본 `User` + resolver/직접 조회

장점:

- 구현이 단순하다. Spring Security 기본 `User`를 그대로 쓰기 때문에 커스텀 principal 클래스를 관리하지 않아도 된다.
- principal에 도메인 필드를 많이 싣지 않으므로 인증 객체가 가볍다.
- `AuthenticatedUserIdResolver`가 DB를 다시 조회하기 때문에, 사용자 삭제·비활성화·role 변경 같은 최신 DB 상태를 그 시점에 다시 확인할 수 있다.
- 각 도메인 서비스가 필요한 시점에 `Users` 엔티티를 직접 조회하므로, 영속 엔티티가 필요한 로직에서는 흐름이 직관적이다.

단점:

- JWT 필터에서 이미 사용자 조회를 했는데, `idx`가 필요할 때 다시 조회한다. 단순한 “현재 사용자 PK” 조회에도 중복 DB 쿼리가 발생한다.
- 현재 사용자 조회 방식이 `AuthenticatedUserIdResolver`, 직접 `SecurityContextHolder`, 직접 JWT 파싱으로 흩어져 있다.
- Spring 기본 `User`는 Petory의 `idx`, `emailVerified`, `status`, `role`을 직접 제공하지 않아 컨트롤러에서 `@AuthenticationPrincipal`을 써도 얻을 수 있는 정보가 제한적이다.
- 컨트롤러와 서비스마다 인증 보일러플레이트가 반복된다.
- 테스트에서 SecurityContext를 직접 세팅하거나 repository mock을 추가해야 하는 경우가 많다.

### 개선 방식: `CustomUserDetails` + `@AuthenticationPrincipal`

장점:

- JWT 필터에서 조회한 사용자 정보를 principal에 담아 재사용하므로, 현재 사용자 `idx` 조회를 위한 추가 DB 쿼리를 줄일 수 있다.
- 컨트롤러 메서드에서 `@AuthenticationPrincipal CustomUserDetails user`로 현재 사용자 정보를 명시적으로 받을 수 있어 코드 의도가 분명해진다.
- `idx`, `role`, `emailVerified`, `status` 같은 인증 주체 정보를 한 타입으로 통일할 수 있다.
- `AuthenticatedUserIdResolver`를 호환 브리지로 유지하면 기존 코드를 한 번에 갈아엎지 않고 점진적으로 전환할 수 있다.
- 이메일 인증 guard, 관리자 여부 확인, 현재 사용자 식별 같은 공통 정책을 한 타입 중심으로 정리하기 쉽다.
- 컨트롤러 단위 테스트에서 principal 객체를 직접 주입해 검증하기 쉬워진다.

단점:

- `CustomUserDetails`라는 보안 전용 모델을 새로 관리해야 한다. `Users` 필드가 바뀌면 principal 생성 코드도 같이 점검해야 한다.
- principal에 담긴 `role`, `emailVerified`, `status`는 인증 필터에서 조회한 시점의 스냅샷이다. 한 요청 안에서는 최신 DB 변경을 자동으로 다시 반영하지 않는다.
- 너무 많은 도메인 정보를 principal에 넣으면 SecurityContext가 비대해지고, 인증 객체가 사실상 사용자 DTO처럼 변질될 수 있다.
- `@AuthenticationPrincipal`을 컨트롤러에 도입하면 메서드 시그니처 변경이 많이 발생한다. 한 번에 전환하면 변경 범위가 크다.
- OAuth2 성공 핸들러의 principal은 여전히 `OAuth2User`이므로, OAuth2 콜백 구간과 JWT API 구간을 구분해서 설계해야 한다.
- WebSocket 인증도 같은 `UserDetailsService`를 쓰지만, HTTP 요청과 생명주기가 다르므로 별도 회귀 테스트가 필요하다.

### 판단 기준

현재 Petory에서는 개선 방식이 더 적합하다. 이유는 단순하다.

```text
현재 병목:
  인증 필터에서 이미 사용자 조회
  -> 컨트롤러에서 현재 사용자 idx 필요
  -> 같은 사용자 다시 조회

개선 목표:
  인증 시 만든 principal에 idx를 보관
  -> 컨트롤러에서 바로 사용
```

다만 Access Token claim에 `idx`, `role`을 넣어 DB 조회 자체를 없애는 단계까지 바로 가는 것은 별도 판단이 필요하다. 제재, 탈퇴, 권한 변경을 빠르게 반영해야 하므로 1차 개선은 **매 요청 DB 조회는 유지하되, 같은 요청 안의 중복 조회를 줄이는 것**이 안전하다.

---

## 적용 순서 체크리스트

- [x] `CustomUserDetails` 생성
- [x] `UsersDetailsServiceImpl`이 `CustomUserDetails` 반환하도록 변경
- [ ] `JwtAuthenticationFilter`의 `validateToken -> getIdFromToken -> validateToken -> isTokenExpired` 중복 파싱 제거
- [x] `AuthenticatedUserIdResolver`가 `CustomUserDetails`면 DB 조회 없이 `idx` 반환하도록 변경
- [ ] `JwtAuthenticationFilter` 동작 확인: principal 타입이 `CustomUserDetails`인지 테스트
- [x] `WebSocketAuthenticationInterceptor`, `WebSocketAuthChannelInterceptor`도 동일 principal을 쓰는지 확인
- [x] OAuth2 성공 후 발급된 JWT도 `CustomUserDetails` principal로 복원되는지 확인
- [ ] 직접 `SecurityContextHolder`를 쓰는 컨트롤러부터 `@AuthenticationPrincipal`로 교체 (부분 적용: `UserProfileController`, `PetController`. 다른 도메인은 점진 전환)
- [ ] `AuthController.logout`의 직접 토큰 파싱 제거 검토
- [ ] 이메일 인증 반복 검사 공통화 검토
- [ ] `./gradlew compileJava`
- [ ] 단위 테스트 추가: `UsersDetailsServiceImpl`이 `CustomUserDetails`를 반환하고 `idx/role/emailVerified/status`를 보존
- [ ] 단위 테스트 추가: `AuthenticatedUserIdResolver`가 `CustomUserDetails` principal이면 repository를 호출하지 않음
- [ ] 통합 테스트 추가: Bearer JWT 요청에서 컨트롤러의 `@AuthenticationPrincipal CustomUserDetails` 주입 확인
- [ ] 통합 테스트 추가: OAuth2Service가 발급한 Access Token으로 API 호출 시 일반 로그인과 같은 principal 경로를 타는지 확인
- [ ] 통합 테스트 추가: 만료/잘못된 JWT가 SecurityContext를 만들지 않고 401로 이어지는지 확인

---

## 관련 코드

| 구분 | 파일 |
| --- | --- |
| JWT 인증 필터 | `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java` |
| Petory principal | `backend/main/java/com/linkup/Petory/global/security/CustomUserDetails.java` |
| UserDetails 생성 | `backend/main/java/com/linkup/Petory/domain/user/service/UsersDetailsServiceImpl.java` |
| 현재 사용자 idx resolver | `backend/main/java/com/linkup/Petory/global/security/AuthenticatedUserIdResolver.java` |
| Security 설정 | `backend/main/java/com/linkup/Petory/global/security/SecurityConfig.java` |
| 로그인/Refresh | `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java` |
| Auth API | `backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java` |
| OAuth JWT 발급 | `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java` |
| WebSocket 인증 | `backend/main/java/com/linkup/Petory/global/websocket/security/*Authentication*.java` |
| JWT 백로그 | `docs/refactoring/JWT-토큰-리팩토링-백로그.md` |

---

## 한 줄 결론

초기 문제였던 “principal이 Petory 사용자 모델을 담지 못하는 구조”는 `CustomUserDetails` 도입으로 1차 해소됐다. 남은 과제는 JWT claims 중복 파싱 제거, `AuthController` 직접 토큰 파싱 정리, 그리고 resolver/직접 `SecurityContextHolder` 사용처를 `@AuthenticationPrincipal` 중심으로 점진 통일하는 것이다.
