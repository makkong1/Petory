# 백엔드 예외처리 분석 및 리팩토링 계획

> **구조 정리**: [01-exception-handling-structure.md](./01-exception-handling-structure.md) — 전역 vs 도메인 전용, 공통 재사용 예외 정리

## 1. 현재 상태 요약

### 1.1 예외 처리 인프라
- **GlobalExceptionHandler**: `@RestControllerAdvice`로 전역 예외 처리
- **ApiException**: 모든 도메인 전용 예외의 베이스 → `@ExceptionHandler(ApiException.class)`로 통합 처리
- **특수 핸들러**: `EmailVerificationRequiredException` (redirectUrl, purpose), `AuthorizationDeniedException`, `AsyncRequestTimeoutException`, `AuthenticationException`
- **Fallback**: `IllegalArgumentException`(400), `IllegalStateException`(409), `Exception`(500)

### 1.2 도메인별 예외 리팩토링 현황

| 도메인 | 상태 | 도메인 전용 예외 |
|--------|------|------------------|
| **user** | ✅ 완료 | UserNotFoundException, DuplicateUserFieldException, EmailVerificationRequiredException, UnauthenticatedException, UserForbiddenException, UserBannedException, UserSuspendedException, PetNotFoundException, UserValidationException 등 |
| **board** | ✅ 완료 | BoardNotFoundException, MissingPetBoardNotFoundException, CommentNotFoundException, CommentNotBelongToBoardException, BoardValidationException |
| care | ✅ 완료 | CareRequestNotFoundException, CareApplicationNotFoundException, CareCommentNotFoundException 등 |
| payment | ✅ 완료 | PetCoinTransactionNotFoundException, PetCoinEscrowNotFoundException, InsufficientBalanceException 등 |
| meetup | ✅ 완료 | MeetupNotFoundException, MeetupParticipantNotFoundException, MeetupValidationException 등 |
| chat | ✅ 완료 | ConversationNotFoundException, ChatMessageNotFoundException, ChatForbiddenException 등 |
| location | ✅ 완료 | LocationServiceNotFoundException, LocationServiceReviewNotFoundException, LocationReviewDuplicateException 등 |
| report | ✅ 완료 | ReportNotFoundException, ReportTargetNotFoundException, ReportValidationException 등 |
| file | ✅ 완료 | FileNotFoundException, FileStorageException, FileValidationException, FileUploadValidationException |
| notification | ✅ 완료 | NotificationNotFoundException, NotificationForbiddenException |
| statistics | - | 예외 없음 |

---

## 2. 문제점

### 2.1 공통 문제
1. **RuntimeException 남용**: 의미 있는 비즈니스 예외가 모두 `RuntimeException`으로 처리됨
2. **메시지 혼재**: 한글/영어 혼용 ("User not found" vs "유저를 찾을 수 없습니다")
3. **HTTP 상태 코드 불명확**: `IllegalArgumentException` → 400, `IllegalStateException` → 409로만 매핑
4. **에러 코드 부재**: 프론트엔드에서 예외 유형별 분기 처리 어려움
5. **Controller에서 try-catch 중복**: AuthController, MeetupController 등에서 예외를 잡아 수동 응답 생성

### 2.2 도메인별 문제

#### User 도메인
- `RuntimeException("유저 없음")`, `RuntimeException("User not found")` 등 동일 의미 다른 표현
- 인증/제재/중복/검증 예외가 모두 RuntimeException
- `UserSanctionService`는 `IllegalArgumentException`, `AuthService`는 `RuntimeException` 사용

#### Board 도메인
- `RuntimeException("Board not found")` - 404에 적합한 전용 예외 없음
- `EmailVerificationRequiredException`은 user 도메인 예외인데 board에서 사용

#### Care 도메인
- `RuntimeException` vs `IllegalArgumentException` vs `IllegalStateException` 혼용

#### Payment 도메인
- `RuntimeException("Escrow not found")` - 404 전용 예외 없음
- `IllegalStateException` 적절히 사용 (HOLD 상태 검증)

#### Meetup 도메인
- Controller에서 `catch (Exception e)` 후 `RuntimeException` 재throw 다수
- 비즈니스 로직이 Controller에 노출

#### Location 도메인
- `catch (Exception e)` 후 로그만 하고 재throw 또는 빈 응답

---

## 3. 개선 방향

### 3.1 예외 계층 설계 원칙
1. **도메인별 exception 패키지**: `domain/{domain}/exception/`
2. **공통 베이스**: `ApiException` 또는 `BusinessException` (HTTP status, errorCode 포함)
3. **구체적 예외**: `UserNotFoundException`, `DuplicateNicknameException` 등
4. **GlobalExceptionHandler 확장**: 도메인 예외별 `@ExceptionHandler` 추가

### 3.2 HTTP 상태 매핑
| 예외 유형 | HTTP Status | 용도 |
|----------|-------------|------|
| *NotFoundException | 404 | 리소스 미존재 |
| *UnauthorizedException | 401 | 인증 실패 |
| *ForbiddenException | 403 | 권한 부족, 제재 |
| *ConflictException / Duplicate* | 409 | 중복 데이터 |
| *ValidationException | 400 | 입력 검증 실패 |
| *BadRequestException | 400 | 잘못된 요청 |

### 3.3 리팩토링 순서
1. **User 도메인** (가장 복잡, Auth/회원가입/프로필/제재)
2. Board 도메인
3. Care 도메인
4. Payment 도메인
5. Meetup, Location 등
6. Admin, File 도메인
7. Statistics 도메인 (현재 예외 없음, 추후 확장 시 참고)

---

## 4. 참고: 기존 EmailVerificationRequiredException
- `domain/user/exception/EmailVerificationRequiredException.java`
- `purpose` 필드로 인증 용도 구분
- GlobalExceptionHandler에서 403 + `errorCode`, `redirectUrl` 반환
- **유지**하며 다른 user 예외와 함께 정리
