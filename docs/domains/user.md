# User 도메인

> 기준: 현재 코드를 단일 진실로 본다. 리팩토링/트러블슈팅 문서는 배경과 히스토리로만 참고한다.

## 1. 범위

User 도메인은 인증, 사용자 프로필, 소셜 계정 연결, 이메일 인증, 반려동물 관리, 제재 상태를 담당한다. 다른 도메인이 “현재 사용자”, “사용자 권한”, “사용자 신뢰 상태”, “펫 소유권”을 판단할 때 참조하는 기반 도메인이다.

포함 범위:

- 일반 회원가입/로그인/로그아웃
- Access Token 검증과 Refresh Token 기반 재발급
- Google/Naver 중심 OAuth2 로그인 처리. Kakao 분기는 코드에 있으나 실제 설정/UX 지원은 별도 확인 필요
- 내 프로필 조회/수정
- 다른 사용자 프로필 조회
- 닉네임/아이디 중복 확인
- 이메일 인증 및 비밀번호 재설정 메일 발송
- 반려동물 CRUD와 소유권 검증
- 사용자 제재 상태 확인, 경고, 정지, 차단
- 관리자용 사용자 조회/상태 변경/삭제/복구
- MASTER 전용 관리자 계정 관리

비범위:

- 신고 생성/처리 자체는 Report/Admin 도메인
- 알림 발송 자체는 Notification 도메인
- 결제 잔액 차감과 에스크로는 Payment/Care 도메인
- 채팅 목록 최적화는 Chat 도메인

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 인증 API | `backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java` |
| 인증 서비스 | `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java` |
| 프로필 API | `backend/main/java/com/linkup/Petory/domain/user/controller/UserProfileController.java` |
| 사용자 서비스 | `backend/main/java/com/linkup/Petory/domain/user/service/UsersService.java` |
| OAuth2 서비스 | `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java` |
| OAuth2 성공 핸들러 | `backend/main/java/com/linkup/Petory/domain/user/handler/OAuth2SuccessHandler.java` |
| 이메일 인증 | `backend/main/java/com/linkup/Petory/domain/user/service/EmailVerificationService.java` |
| 펫 API | `backend/main/java/com/linkup/Petory/domain/user/controller/PetController.java` |
| 펫 서비스 | `backend/main/java/com/linkup/Petory/domain/user/service/PetService.java` |
| 제재 서비스 | `backend/main/java/com/linkup/Petory/domain/user/service/UserSanctionService.java` |
| 제재 스케줄러 | `backend/main/java/com/linkup/Petory/domain/user/scheduler/UserSanctionScheduler.java` |
| 사용자 엔티티 | `backend/main/java/com/linkup/Petory/domain/user/entity/Users.java` |
| 사용자 repository | `backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java` |
| 프론트 인증 API | `frontend/src/api/authApi.js` |
| 프론트 사용자/펫 API | `frontend/src/api/userApi.js` |

## 3. 핵심 엔티티

### Users

주요 필드:

| 필드 | 의미 |
|---|---|
| `idx` | 내부 PK |
| `id` | 로그인 ID, JWT subject |
| `username` | 사용자명 |
| `nickname` | 닉네임. 소셜 로그인 신규 사용자는 비어 있을 수 있음 |
| `email` | 이메일 |
| `password` | 암호화된 비밀번호 |
| `profileImage`, `birthDate`, `gender` | OAuth2 제공자에서 수집 가능한 프로필 데이터 |
| `emailVerified` | 단일 이메일 인증 완료 여부 |
| `role` | `USER`, `SERVICE_PROVIDER`, `ADMIN`, `MASTER` |
| `status` | `ACTIVE`, `SUSPENDED`, `BANNED` |
| `warningCount` | 누적 경고 수 |
| `petCoinBalance` | 펫코인 잔액 |
| `suspendedUntil` | 정지 만료 시각 |
| `isDeleted`, `deletedAt` | soft delete 상태 |
| `refreshToken`, `refreshExpiration` | DB에 저장되는 refresh token 상태 |
| `lastLoginAt` | 통계용 마지막 로그인 시각 |

관계:

- `Users` 1:N `SocialUser`
- `Users` 1:N `Pet`
- `Users` 1:N `UserSanction`

성능 메모:

- `Users`에는 `@BatchSize(size = 50)`이 적용되어 ManyToOne proxy 배치 로딩에 사용된다.
- `socialUsers`에도 `@BatchSize(size = 50)`이 적용되어 사용자 목록 DTO 변환 시 N+1을 줄인다.

### SocialUser

소셜 로그인 제공자와 Petory 사용자를 연결한다.

핵심 키:

- `provider`
- `providerId`
- `user`

기존 소셜 계정은 `provider + providerId`로 찾고, 신규 소셜 로그인은 이메일로 기존 사용자 연결을 먼저 시도한다.

### Pet

사용자가 등록한 반려동물이다. 모든 상세/수정/삭제는 JWT subject와 펫 소유자의 `Users.id`가 일치해야 한다.

정책:

- 삭제는 soft delete다.
- `PetType.ETC`일 때 `breed`는 필수다.
- 프로필 이미지 URL이 있으면 File 도메인의 `AttachmentFileService`와 동기화한다.

### UserSanction

경고, 정지, 차단 이력을 저장한다.

제재 타입:

- `WARNING`
- `SUSPENSION`
- `BAN`

## 4. 인증 API

### `/api/auth`

| API | 설명 |
|---|---|
| `POST /api/auth/register` | 일반 회원가입 |
| `POST /api/auth/login` | 로그인, access/refresh token 발급 |
| `POST /api/auth/validate` | Authorization header의 access token 검증 |
| `POST /api/auth/refresh` | refresh token으로 access token 재발급 |
| `POST /api/auth/logout` | refresh token 제거 |
| `POST /api/auth/forgot-password` | 비밀번호 재설정 이메일 발송 |

### 로그인 흐름

1. `AuthController.login()`이 `AuthenticationManager`로 ID/비밀번호 인증을 수행한다.
2. `AuthService.login()`이 active user를 다시 조회한다.
3. `BANNED`면 로그인 차단.
4. `SUSPENDED`이고 만료 전이면 로그인 차단.
5. `SUSPENDED`가 만료됐으면 `ACTIVE`로 자동 해제한다.
6. Access Token과 Refresh Token을 생성한다.
7. Refresh Token, 만료 시각, `lastLoginAt`을 DB에 저장한다.
8. 이미 로드한 `Users` 엔티티를 `UsersConverter.toDTO()`로 변환해 응답한다.

토큰 정책:

- Access Token subject는 `Users.id`다.
- Access Token TTL은 `jwt.access-token-expiration-ms`이며 기본값은 15분이다.
- Refresh Token TTL은 1일이다.
- Refresh Token은 DB에도 저장해 로그아웃/만료/삭제 상태를 확인한다.
- refresh 시 기존 Refresh Token은 유지하고 Access Token만 새로 발급한다.

## 5. 회원가입

일반 회원가입은 `UsersService.createUser()`가 처리한다.

정책:

- nickname은 필수이고 50자를 넘을 수 없다.
- password는 필수이며 저장 전 `PasswordEncoder`로 암호화한다.
- nickname, username, email 중복 검사는 `findByNicknameOrUsernameOrEmail()` 단일 쿼리로 수행한다.
- 저장 중 unique 제약 충돌이 나면 `DataIntegrityViolationException`을 잡아 필드별 중복 예외로 변환한다.
- 일반 회원가입 사용자는 social profile 필드를 null로 둔다.
- 회원가입 전 이메일 인증이 완료된 경우 가입 즉시 `emailVerified = true`가 된다.
- `app.email-verification.skip-in-dev=true`면 개발 모드에서 이메일 인증을 자동 통과시킨다.
- 이메일 미인증 상태로 가입하면 가입 후 인증 메일 발송을 시도하되, 메일 발송 실패가 회원가입 성공 자체를 되돌리지는 않는다.

Soft delete 관련:

- `findByUsername`, `findByNickname`, `findByEmail`은 삭제되지 않은 사용자만 조회한다.
- 따라서 탈퇴한 사용자의 nickname/username/email은 재사용 가능하다.

## 6. OAuth2 로그인

OAuth2 로그인은 Spring Security OAuth2 성공 후 `OAuth2SuccessHandler`가 처리한다.

흐름:

1. 제공자 registration id를 `Provider`로 변환한다.
2. `OAuth2Service.processOAuth2Login()`을 호출한다.
3. provider별 attribute에서 `providerId`, `email`, `name`을 추출한다.
4. `SocialUser(provider, providerId)`가 있으면 기존 사용자로 로그인한다.
5. 없으면 이메일로 기존 사용자를 찾아 소셜 계정을 연결한다.
6. 기존 사용자도 없으면 신규 사용자를 생성한다.
7. 신규 소셜 사용자는 UUID suffix 기반 `id`, `username`을 만들고 DB unique 충돌 시 최대 3회 재시도한다.
8. 제재 상태를 확인한다.
9. 일반 로그인과 동일하게 access/refresh token을 발급하고 refresh token을 DB에 저장한다.
10. 프론트 OAuth2 callback URL로 token을 query parameter에 담아 redirect한다.

닉네임 정책:

- 소셜 신규 사용자는 `nickname = null`일 수 있다.
- 성공 핸들러는 닉네임이 비어 있으면 `needsNickname=true`를 callback URL에 추가한다.
- 프론트는 `/api/users/me/nickname`으로 닉네임 설정을 완료한다.

제공자별 데이터:

- Google: `sub`, `email`, `name`, `picture`, `email_verified`
- Naver: 표준화된 `id`, `email`, `name`, `profile_image`, `birthyear`, `birthday`, `gender`
- Kakao 분기도 서비스 코드에 존재하지만, 현재 프로젝트 설정과 UI에서 실제 지원 범위는 별도 확인이 필요하다.

## 7. 프로필 API

### `/api/users`

| API | 설명 |
|---|---|
| `GET /api/users/me` | 내 프로필, 펫, 케어 리뷰 요약, 위치서비스 리뷰, 모임 히스토리 조회 |
| `PUT /api/users/me` | 내 프로필 수정 |
| `PATCH /api/users/me/password` | 비밀번호 변경 |
| `PATCH /api/users/me/username` | username 변경 |
| `POST /api/users/me/nickname` | 소셜 로그인 사용자 닉네임 설정 |
| `GET /api/users/nickname/check` | 닉네임 사용 가능 여부 |
| `GET /api/users/id/check` | 로그인 ID 사용 가능 여부 |
| `POST /api/users/email/verify` | 로그인 사용자 이메일 인증 메일 발송 |
| `POST /api/users/email/verify/pre-registration` | 회원가입 전 이메일 인증 메일 발송 |
| `GET /api/users/email/verify/pre-registration/check` | 회원가입 전 이메일 인증 상태 확인 |
| `GET /api/users/email/verify/{token}` | 이메일 인증 처리 |
| `GET /api/users/{userId}/profile` | 다른 사용자 프로필 조회 |
| `GET /api/users/{userId}/reviews` | 특정 사용자 케어 리뷰 목록 |

내 프로필 조회:

- `UsersService.getMyProfile()`은 `findByIdStringWithPets()` fetch join으로 User와 Pet을 한 번에 가져온다.
- `UserProfileController`는 Care 리뷰 요약, Location 리뷰 요약, Meetup 히스토리를 조합해 `UserProfileWithReviewsDTO`로 응답한다.
- `SERVICE_PROVIDER`는 받은 Care 리뷰 기준, 일반 사용자는 작성한 Care 리뷰 기준으로 프로필 리뷰 모드를 구분한다.

프로필 수정:

- 본인 프로필만 수정 가능하다.
- 요청 DTO에 다른 사용자의 `idx`가 들어오면 `UserForbiddenException`을 던진다.
- 일반 사용자가 수정 가능한 필드만 반영한다.

비밀번호 변경:

- 이메일 인증 완료가 필요하다.
- 현재 비밀번호가 일치해야 한다.
- 새 비밀번호는 암호화해 저장한다.

## 8. 이메일 인증

이메일 인증은 `EmailVerificationService`가 담당한다.

토큰:

- 이메일 인증 토큰은 JWT다.
- 만료 시간은 24시간이다.
- `purpose` claim으로 용도를 구분한다.
- 회원가입 전 인증은 subject에 email을 넣고 `isPreRegistration=true` claim을 사용한다.

용도 enum:

- `REGISTRATION`
- `PASSWORD_RESET`
- `PET_CARE`
- `MEETUP`
- `LOCATION_REVIEW`
- `BOARD_EDIT`
- `COMMENT_EDIT`
- `MISSING_PET`

회원가입 전 인증:

1. 사용자가 이메일로 인증 메일을 요청한다.
2. 이미 가입된 email이면 중복 예외를 던진다.
3. 링크 검증 성공 시 Redis에 `email_verification:pre_registration:{email}` 값을 24시간 저장한다.
4. 회원가입 시 Redis 값을 확인해 `emailVerified`에 반영한다.
5. 가입 완료 후 Redis 인증 상태를 삭제한다.

기존 사용자 인증:

1. 로그인 사용자가 purpose와 함께 인증 메일을 요청한다.
2. 토큰 검증 성공 시 `Users.emailVerified = true`로 업데이트한다.
3. purpose는 프론트 redirect URL 결정에 사용된다.

서비스 레벨 권한:

- `checkEmailVerification(userId)`는 미인증 사용자에게 `EmailVerificationRequiredException`을 던진다.
- 개발 모드 skip 설정이 켜져 있으면 체크를 통과한다.

## 9. 반려동물 API

### `/api/pets`

| API | 설명 |
|---|---|
| `GET /api/pets` | 내 펫 목록 조회 |
| `GET /api/pets/{petIdx}` | 내 펫 상세 조회 |
| `POST /api/pets` | 펫 생성 |
| `PUT /api/pets/{petIdx}` | 펫 수정 |
| `DELETE /api/pets/{petIdx}` | 펫 soft delete |
| `POST /api/pets/{petIdx}/restore` | 펫 복구 |
| `GET /api/pets/type/{petType}` | 타입별 펫 조회 |

소유권 정책:

- 상세/수정/삭제/복구는 JWT subject와 펫 소유자의 `Users.id`가 같아야 한다.
- 불일치 시 `UserForbiddenException.ownPetOnly()`를 던진다.

데이터 정책:

- 삭제된 펫은 내 목록과 상세 조회에서 제외된다.
- `PetType.ETC`는 breed가 필수다.
- `profileImageUrl`이 있으면 `AttachmentFileService`를 통해 File 도메인과 단일 첨부를 동기화한다.
- 수정 시 `profileImageUrl`이 빈 문자열이면 해당 펫의 파일 연결을 삭제한다.

## 10. 제재

제재 상태는 `Users.status`, `Users.warningCount`, `Users.suspendedUntil`, `UserSanction` 이력으로 관리한다.

정책:

- 로그인 시 `BANNED` 사용자는 차단된다.
- `SUSPENDED`이고 `suspendedUntil`이 미래면 차단된다.
- 정지 기간이 만료된 사용자는 로그인 시 자동으로 `ACTIVE`로 바뀔 수 있다.
- `UserSanctionScheduler`도 만료된 정지를 자동 해제한다.
- 경고는 `incrementWarningCount()` DB update로 원자적으로 증가한다.
- 경고 3회 이상이면 자동으로 3일 정지를 적용한다.
- 신고 처리의 `WARN_USER`, `SUSPEND_USER` 액션은 `UserSanctionService.applySanctionFromReport()`로 연결된다.

제재 타입:

| 타입 | 효과 |
|---|---|
| `WARNING` | 경고 이력 저장, warning count 증가 |
| `SUSPENSION` | `status=SUSPENDED`, `suspendedUntil` 설정 |
| `BAN` | `status=BANNED`, `suspendedUntil=null` |

## 11. 관리자 API

### `/api/admin/users`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `GET /api/admin/users/paging` | 사용자 목록 페이징 조회, role/status/q 필터 |
| `GET /api/admin/users/{id}` | 사용자 단건 조회 |
| `PATCH /api/admin/users/{id}/status` | 상태, 경고 수, 정지 기간 변경 |
| `DELETE /api/admin/users/{id}` | 사용자 soft delete |
| `POST /api/admin/users/{id}/restore` | 사용자 복구 |

### `/api/master/admin-users`

`MASTER` 전용.

| API | 설명 |
|---|---|
| `GET /api/master/admin-users` | 관리자 계정 목록 |
| `POST /api/master/admin-users` | 관리자 계정 생성 |
| `PATCH /api/master/admin-users/{id}/promote-to-admin` | 일반 사용자 ADMIN 승격 |
| `DELETE /api/master/admin-users/{id}` | 관리자 계정 삭제 |
| `PATCH /api/master/admin-users/{id}/password` | 관리자 비밀번호 변경 |

성능/권한 메모:

- 관리자 목록은 페이징 API만 사용한다.
- 삭제 권한 검증에는 전체 User+Pet 조회 대신 role projection을 쓰는 경량 조회가 있다.

## 12. 성능과 리팩토링 포인트

현재 코드에 반영된 사항:

- 로그인/refresh 응답 DTO 생성 시 같은 user를 다시 조회하지 않고 이미 로드한 엔티티를 변환한다.
- 회원가입 중복 검사는 nickname/username/email을 단일 쿼리로 확인한다.
- 저장 시 unique 제약 충돌도 필드별 예외로 보정한다.
- `findByUsername`, `findByNickname`, `findByEmail`은 soft delete 사용자를 제외한다.
- 사용자 목록의 `socialUsers` 접근 N+1은 `@BatchSize(size = 50)`로 완화한다.
- 내 프로필과 관리자 사용자 단건 조회는 User+Pet fetch join을 사용한다.
- Care 리뷰 프로필 요약은 리뷰 목록과 평균을 한 번의 서비스 호출에서 계산한다.
- 관리자 사용자 목록은 페이징 기반이다.
- 관리자 삭제 권한 검증은 role projection으로 경량화한다.

남은 주의점:

- `UserSanctionService.addWarning()`은 경고 증가 후 최신 warning count 확인을 위해 사용자를 다시 조회한다.
- OAuth2 성공 핸들러는 token을 query parameter로 전달한다. 구현은 단순하지만 브라우저 history/log 노출 리스크가 있어 장기적으로 더 안전한 전달 방식을 검토할 수 있다.
- OAuth2 경로의 제재 예외는 일반 로그인과 달리 `RuntimeException` 메시지를 redirect query의 `error`로 전달한다.
- `GET /api/pets/type/{petType}`는 현재 사용자 소유 필터가 아니라 타입 기준 전체 조회다. 사용자용 API로 노출할 의도가 맞는지 검토 여지가 있다.
- `AuthController.validateToken()`은 일부 실패 응답을 컨트롤러에서 직접 조립한다.

## 13. 관련 문서

- [사용자 인증 및 프로필 아키텍처](<../architecture/user/사용자 인증 및 프로필 아키텍처.md>)
- [이메일 인증 시스템 아키텍처](<../architecture/user/이메일 인증 시스템 아키텍처.md>)
- [신고 및 제재 시스템 아키텍처](<../architecture/user/신고 및 제재 시스템 아키텍처.md>)
- [User 백엔드 성능 최적화 리팩토링](../refactoring/user/user-backend-performance-optimization.md)
- [OAuth SocialUser 쿼리 트러블슈팅](../refactoring/user/social-users-query/troubleshooting.md)
- [로그인 시 N+1 문제 해결](../troubleshooting/users/login-n-plus-one-issue.md)
- [Soft Delete 닉네임 재사용 문제](../troubleshooting/users/soft-delete-nickname-reuse.md)
