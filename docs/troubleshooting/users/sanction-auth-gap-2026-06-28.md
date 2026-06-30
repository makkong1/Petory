# 제재 상태 인증 우회 문제 정리 (2026-06-28)

## 1. 목적

이 문서는 현재 코드에서 확인한 사용자 제재(`SUSPENDED`, `BANNED`) 관련 인증/인가 문제를 수정 전에 고정하기 위한 문제상황 문서다.

다음 단계는 이 문서의 재현 항목을 테스트로 만들고, 현재 코드에서 실패 또는 취약 동작을 확인한 뒤 수정하는 것이다.

## 2. 현재 코드 사실

| 영역                 | 현재 동작                                                                                                                                                     |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 일반 로그인          | `AuthController.login()`이 `AuthenticationManager.authenticate()`를 먼저 호출하고, 이후 `AuthService.login()`에서 `BANNED` / `SUSPENDED`를 검사한다.          |
| OAuth 로그인         | `OAuth2Service.processOAuth2Login()`에서 토큰 발급 전 `BANNED` / `SUSPENDED`를 검사한다.                                                                      |
| Access JWT 인증      | `JwtAuthenticationFilter`가 token 검증 후 `UserDetailsService.loadUserByUsername()`로 사용자 정보를 읽고 직접 `UsernamePasswordAuthenticationToken`을 만든다. |
| Refresh token 재발급 | `AuthService.refreshAccessToken()`이 refresh token 유효성, DB 저장 여부, 만료만 확인하고 사용자 제재 상태는 확인하지 않는다.                                  |
| WebSocket 인증       | `WebSocketAuthenticationInterceptor`, `WebSocketAuthChannelInterceptor`도 JWT 검증 후 직접 인증 객체를 만든다.                                                |
| 사용자 상태 변경     | 관리자 상태 변경 API는 `Users.status`, `warningCount`, `suspendedUntil`을 직접 변경한다.                                                                      |
| 신고 제재            | `ReportService.handleReport()`는 `report.targetIdx`를 그대로 `UserSanctionService.applySanctionFromReport(userId, ...)`에 넘긴다.                             |

관련 파일:

- `backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java`
- `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java`
- `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java`
- `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java`
- `backend/main/java/com/linkup/Petory/global/security/CustomUserDetails.java`
- `backend/main/java/com/linkup/Petory/global/websocket/security/WebSocketAuthenticationInterceptor.java`
- `backend/main/java/com/linkup/Petory/global/websocket/security/WebSocketAuthChannelInterceptor.java`
- `backend/main/java/com/linkup/Petory/domain/user/service/UsersService.java`
- `backend/main/java/com/linkup/Petory/domain/report/service/ReportService.java`

## 3. 발견된 문제

### A1. 정지/차단 사용자가 refresh token으로 access token을 재발급받을 수 있음

`AuthService.refreshAccessToken()`은 refresh token 자체만 검증한다. `Users.status == SUSPENDED` 또는 `BANNED`인지 확인하지 않는다.

영향:

- 관리자가 사용자를 정지/차단해도 기존 refresh token이 살아 있으면 새 access token을 계속 받을 수 있다.
- access token TTL보다 긴 refresh token TTL 동안 제재가 우회될 수 있다.

재현 테스트 후보:

1. ACTIVE 사용자로 로그인해 refresh token을 확보한다.
2. DB 또는 서비스로 사용자를 `SUSPENDED` 또는 `BANNED`로 변경한다.
3. `/api/auth/refresh`를 호출한다.
4. 기대 정책은 403이지만, 현재 코드는 access token을 발급할 가능성이 있다.

### A2. 기존 access token은 제재 직후에도 보호 API 접근에 사용될 수 있음

`CustomUserDetails.isEnabled()`와 `isAccountNonLocked()`는 상태를 반영하지만, `JwtAuthenticationFilter`는 이 메서드를 검사하지 않고 직접 인증 객체를 만든다.

영향:

- 정지/차단 직전에 발급된 access token은 만료 전까지 계속 보호 API 접근에 사용될 수 있다.
- 현재 기본 access token TTL은 15분이다.

재현 테스트 후보:

1. ACTIVE 사용자로 로그인해 access token을 확보한다.
2. 사용자를 `BANNED` 또는 `SUSPENDED`로 변경한다.
3. 해당 access token으로 `/api/**` 보호 API를 호출한다.
4. 기대 정책은 403 또는 401이지만, 현재 코드는 인증 사용자로 처리할 가능성이 있다.

### A3. WebSocket도 제재 상태를 매 연결/메시지에서 강제하지 않음

WebSocket handshake와 STOMP channel interceptor도 JWT 검증 후 직접 `UsernamePasswordAuthenticationToken`을 만든다.

영향:

- 제재 전에 연결된 세션 또는 제재 후에도 유효한 JWT가 있으면 채팅 구독/전송 경로에서 제재 상태 반영이 늦을 수 있다.
- 채팅은 케어/모임 진행과 직접 연결되므로 도메인 영향이 크다.

재현 테스트 후보:

1. ACTIVE 사용자로 WebSocket 토큰을 확보한다.
2. 사용자를 `SUSPENDED` 또는 `BANNED`로 변경한다.
3. 같은 token으로 WebSocket handshake 또는 STOMP `SEND`를 시도한다.
4. 기대 정책은 거부지만, 현재 구조는 인증 통과 가능성이 있다.

### A4. 만료된 정지의 로그인 시 자동 해제 흐름이 인증 단계와 충돌할 수 있음

`AuthService.login()`에는 만료된 `SUSPENDED` 사용자를 `ACTIVE`로 돌리는 코드가 있다. 하지만 `AuthController.login()`은 그 전에 `AuthenticationManager.authenticate()`를 호출한다.

`CustomUserDetails.isEnabled()`는 `status == ACTIVE`만 true이므로, 만료된 정지 사용자도 Spring Security 인증 단계에서 먼저 막힐 수 있다.

영향:

- 문서상 “정지 기간이 만료된 사용자는 로그인 시 자동 해제” 정책이 실제 로그인 경로에서 보장되지 않을 수 있다.
- 자정 스케줄러가 돌기 전까지 사용자가 로그인하지 못할 가능성이 있다.

재현 테스트 후보:

1. 사용자를 `SUSPENDED`, `suspendedUntil = now - 1분`으로 만든다.
2. `/api/auth/login`을 호출한다.
3. 기대 정책은 자동 `ACTIVE` 전환 후 로그인 성공이다.
4. 현재 코드는 `AuthenticationManager` 단계에서 실패할 가능성이 있다.

### A5. 관리자 상태 변경은 제재 이력과 refresh token 무효화를 보장하지 않음

`UsersService.updateUserStatus()`는 상태 필드만 직접 바꾼다.

영향:

- `UserSanction` 이력이 남지 않을 수 있다.
- `BANNED` / `SUSPENDED`로 바꿔도 기존 refresh token이 제거되지 않는다.
- 운영 감사는 `AdminAuditLog`와 `UserSanction`으로 나뉘며, 제재 도메인 관점에서 이력이 불완전할 수 있다.

재현 테스트 후보:

1. 관리자 API로 사용자를 `BANNED`로 변경한다.
2. `user_sanctions` row 생성 여부를 확인한다.
3. 해당 사용자의 refresh token 유지 여부를 확인한다.

### A6. 신고 처리 제재 대상 매핑이 target type에 따라 틀릴 수 있음

`ReportService.handleReport()`는 `report.targetIdx`를 사용자 ID처럼 넘긴다.

안전한 경우:

- `PET_CARE_PROVIDER`: 신고 대상이 사용자라면 targetIdx가 user idx와 일치한다.

위험한 경우:

- `BOARD`
- `COMMENT`
- `MISSING_PET`
- `CARE_REVIEW`

이 경우 targetIdx는 콘텐츠 ID이므로, 작성자 user idx로 변환한 뒤 제재해야 한다.

영향:

- 엉뚱한 사용자에게 경고/정지가 적용될 수 있다.
- user idx가 없으면 신고 처리 자체가 실패할 수 있다.

재현 테스트 후보:

1. 게시글 신고를 생성한다.
2. 관리자 처리에서 `WARN_USER` 또는 `SUSPEND_USER`를 선택한다.
3. 제재 대상이 게시글 작성자인지, 게시글 ID와 같은 user idx인지 확인한다.

## 4. 우선 수정 순서

| 순서 | 문제                                       | 이유                                            |
| ---- | ------------------------------------------ | ----------------------------------------------- |
| 1    | refresh token 제재 검사                    | access token 재발급 우회가 가장 큼              |
| 2    | JWT 필터 제재 상태 검사                    | 기존 access token의 남은 TTL 동안 API 접근 가능 |
| 3    | WebSocket 인증 제재 상태 검사              | 케어/모임 채팅 흐름과 직접 연결                 |
| 4    | 정지/차단 시 refresh token 제거            | 제재 즉시성 보강                                |
| 5    | 만료 정지 로그인 자동 해제 흐름 정리       | 문서 정책과 실제 로그인 흐름 일치 필요          |
| 6    | 신고 target -> 제재 대상 user resolve      | 콘텐츠 신고에서 잘못된 사용자 제재 방지         |
| 7    | 관리자 상태 변경을 제재 서비스 경유로 통일 | 제재 이력과 감사 일관성 확보                    |

## 5. 다음 작업

1. 이 문서의 A1~A6을 현재 상태 재현 테스트로 만든다.
2. 테스트가 현재 코드의 문제를 드러내는지 확인한다.
3. 인증/refresh/WebSocket/신고 대상 매핑 순서로 수정한다.
4. 수정 후 테스트를 통과시킨다.
5. 해결 내용은 이 문서에 “처리 결과” 섹션을 추가하거나 별도 해결 문서로 분리한다.

## 6. 처리 결과

2026-06-28 1차 수정에서 인증/refresh/신고 매핑 계층을 먼저 보정했다.

| 항목                               | 상태      | 처리                                                                                                                   |
| ---------------------------------- | --------- | ---------------------------------------------------------------------------------------------------------------------- |
| A1 refresh token 제재 우회         | 수정됨    | `refreshAccessToken()`에서 `BANNED` / 만료 전 `SUSPENDED`를 차단하고 refresh token을 제거한다.                         |
| A2 기존 access token 제재 미반영   | 수정됨    | `JwtAuthenticationFilter`가 `CustomUserDetails.isEnabled()` / `isAccountNonLocked()`를 검사한 뒤 인증 객체를 등록한다. |
| A3 WebSocket 제재 미반영           | 수정됨    | WebSocket handshake와 STOMP channel interceptor에서 제재/비활성 계정을 거부한다.                                       |
| A4 만료 정지 로그인 자동 해제 충돌 | 수정됨    | `UsersDetailsServiceImpl`이 만료된 `SUSPENDED` 사용자를 `ACTIVE`로 전환한 뒤 인증 주체를 만든다.                       |
| A5 관리자 상태 변경 token 잔존     | 부분 수정 | 관리자 상태 변경으로 `SUSPENDED` / `BANNED`가 되면 refresh token을 제거한다. `UserSanction` 이력 통일은 남은 작업이다. |
| A6 신고 제재 대상 매핑             | 수정됨    | `ReportService`가 target type별 콘텐츠 작성자 user idx를 resolve한 뒤 제재 서비스에 넘긴다.                            |

추가/수정 테스트:

- `AuthServiceTest`: 제재 계정 refresh 차단, 만료 정지 refresh 자동 해제, 제재 계정 refresh 검증 false
- `UsersDetailsServiceImplTest`: 만료 정지 사용자 로드 시 `ACTIVE` 전환
- `JwtAuthenticationFilterTest`: 차단 사용자 access token 인증 거부
- `ReportServiceTest`: 게시글/댓글 신고 제재가 콘텐츠 ID가 아니라 작성자 user idx에 적용됨

검증:

```bash
./gradlew compileJava
./gradlew test --tests "com.linkup.Petory.domain.user.service.AuthServiceTest" --tests "com.linkup.Petory.domain.user.service.UsersDetailsServiceImplTest" --tests "com.linkup.Petory.filter.JwtAuthenticationFilterTest" --tests "com.linkup.Petory.domain.report.service.ReportServiceTest"
```

남은 작업:

- 관리자 상태 변경 API를 `UserSanctionService` 경유로 통일해 `UserSanction` 이력을 남긴다.
- Care/Meetup/Chat 도메인별 제재 영향 처리는 [제재 상태 도메인 영향 작업 목록](../../architecture/user/제재 상태 도메인 영향 작업 목록 2026-06-28.md)을 기준으로 별도 작업한다.
