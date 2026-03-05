# 예외처리 구조 정리

> 전역 처리 vs 도메인 전용 예외를 구분하여 예외처리 체계를 정리한 문서입니다.

---

## 1. 전역 처리 (GlobalExceptionHandler)

### 1.1 처리 순서 (우선순위 높음 → 낮음)

| 순서 | 예외 | HTTP | 처리 방식 | 비고 |
|------|------|------|-----------|------|
| 1 | `AuthorizationDeniedException` | 403 | Spring Security 권한 거부 | SSE 등 응답 커밋 후 |
| 2 | `AsyncRequestTimeoutException` | - | void (로그만) | SSE 타임아웃 정상 동작 |
| 3 | `EmailVerificationRequiredException` | 403 | errorCode, redirectUrl, purpose | User 도메인, 리다이렉트용 |
| 4 | `AuthenticationException` | 400 | 로그인 실패 | Spring Security |
| 5 | `IllegalArgumentException` | 400 | fallback | 도메인 예외 전환 시 감소 |
| 6 | `IllegalStateException` | 409 | fallback | 도메인 예외 전환 시 감소 |
| 7 | **`ApiException`** | (예외별) | **errorCode, message** | **모든 도메인 전용 예외** |
| 8 | `Exception` | 500 | 서버 오류 | 최종 fallback |

### 1.2 ApiException 처리 (핵심)

- **모든 도메인 전용 예외**는 `ApiException`을 상속
- `@ExceptionHandler(ApiException.class)` 하나로 **통합 처리**
- 응답 형식: `{ error, message, status, errorCode? }`
- **도메인별 개별 핸들러 불필요** (EmailVerificationRequiredException 제외)

---

## 2. 도메인 전용 예외

### 2.1 구조 원칙

```
domain/{domain}/exception/
├── *NotFoundException.java    # 404 - 리소스 미존재
├── *ForbiddenException.java   # 403 - 권한 부족
├── *ValidationException.java # 400 - 입력 검증 실패
├── *ConflictException.java   # 409 - 중복/상태 충돌
└── (도메인별 특수 예외)
```

### 2.2 도메인별 현황

| 도메인 | 완료 | 예외 클래스 | HTTP 매핑 |
|--------|------|-------------|-----------|
| **User** | ✅ | UserNotFoundException, DuplicateUserFieldException, EmailVerificationRequiredException, UnauthenticatedException, UserForbiddenException, UserBannedException, UserSuspendedException, PetNotFoundException, UserValidationException, InvalidPasswordException, InvalidRefreshTokenException, InvalidEmailVerificationTokenException | 404, 409, 403, 401, 400 |
| **Board** | ✅ | BoardNotFoundException, MissingPetBoardNotFoundException, CommentNotFoundException, CommentNotBelongToBoardException, BoardValidationException | 404, 400 |
| **Care** | ❌ | CareRequestNotFoundException, CareApplicationNotFoundException, CareCommentNotFoundException, CareCommentNotBelongException, CareForbiddenException, CareValidationException, CareConflictException, CarePaymentException | 404, 403, 400, 409, 500 |
| **Payment** | ❌ | PetCoinTransactionNotFoundException, PetCoinEscrowNotFoundException, PaymentForbiddenException, PaymentValidationException, PaymentConflictException, InsufficientBalanceException | 404, 403, 400, 409 |
| **Meetup** | ❌ | MeetupNotFoundException, MeetupParticipantNotFoundException, MeetupValidationException, MeetupForbiddenException, MeetupConflictException | 404, 400, 403, 409 |
| **Chat** | ❌ | ConversationNotFoundException, ChatMessageNotFoundException, ConversationParticipantNotFoundException, ChatForbiddenException, ChatValidationException, ChatConflictException | 404, 403, 400, 409 |
| **Location** | ❌ | LocationServiceNotFoundException, LocationServiceReviewNotFoundException, LocationServiceAlreadyDeletedException, LocationReviewAlreadyDeletedException, LocationReviewDuplicateException | 404, 409 |
| **Report** | ❌ | ReportNotFoundException, ReportTargetNotFoundException, ReportValidationException, ReportForbiddenException, ReportConflictException | 404, 400, 409 |
| **File** | ❌ | FileNotFoundException, FileStorageException, FileValidationException, FileUploadValidationException | 404, 500, 400 |
| **Notification** | ❌ | NotificationNotFoundException, NotificationForbiddenException | 404, 403 |
| **Statistics** | - | (현재 예외 없음, 추후 확장 시) | - |

---

## 3. 공통 예외 재사용

### 3.1 여러 도메인에서 사용하는 예외

| 예외 | 소속 | 사용 도메인 |
|------|------|-------------|
| `UserNotFoundException` | user | Board, Care, Payment, Meetup, Chat, Location, Report, Notification |
| `UnauthenticatedException` | user | Care, Payment, Meetup |
| `EmailVerificationRequiredException` | user | Board, Care, Meetup, Location |
| `PetNotFoundException` | user | Care |
| `BoardNotFoundException` | board | Report (신고 대상 검증) |
| `CommentNotFoundException` | board | Report |
| `MissingPetBoardNotFoundException` | board | Report |
| `CareRequestNotFoundException` | care | Chat (confirmCareDeal) |

### 3.2 재사용 원칙

- **다른 도메인 예외 import**: `import com.linkup.Petory.domain.user.exception.UserNotFoundException;`
- **도메인 경계**: User, Pet 등 공통 엔티티 관련은 user 도메인 예외 사용
- **의존 방향**: Care → User, Chat → Care/User (하위 도메인이 상위 도메인 예외 사용)

---

## 4. HTTP 상태 코드 매핑

| HTTP | 예외 패턴 | 용도 |
|------|-----------|------|
| 400 | *ValidationException, *BadRequestException | 입력 검증 실패, 잘못된 요청 |
| 401 | UnauthenticatedException | 인증 필요 (로그인 안 됨) |
| 403 | *ForbiddenException, EmailVerificationRequiredException | 권한 부족, 제재, 이메일 인증 필요 |
| 404 | *NotFoundException | 리소스 미존재 |
| 409 | *ConflictException, Duplicate* | 중복 데이터, 상태 충돌 |
| 500 | *StorageException, *PaymentException | 서버/시스템 오류 |

---

## 5. 특수 케이스 (전역 핸들러 외부)

| 케이스 | 처리 방식 | 이유 |
|--------|-----------|------|
| **WebSocket** (ChatWebSocketController) | catch 후 에러 메시지 브로드캐스트 | WebSocket 응답 형식 상이 |
| **SSE** (NotificationSseService) | IOException/Exception catch, 연결 해제 | 스트림 특성상 예외 전파 어려움 |
| **스케줄러** (BoardPopularityScheduler, CareRequestScheduler) | catch 후 로그만 | 배치 실패 시 재시도/알림 정책에 따름 |
| **AI 에이전트** (ReportAssistAgentService) | catch 후 Optional.empty() | LLM 실패 시 기본값 반환 |
| **외부 API** (NaverMapService, GeocodingController) | catch 후 fallback/에러 응답 | 외부 의존성 |

---

## 6. 문서 링크

| 도메인 | 문서 |
|--------|------|
| 개요 | [00-exception-analysis-overview.md](./00-exception-analysis-overview.md) |
| User | (완료, 문서 별도) |
| Board | [board/보드예외처리.md](./board/보드예외처리.md) |
| Care | [care/케어예외처리.md](./care/케어예외처리.md) |
| Payment | [payment/결제예외처리.md](./payment/결제예외처리.md) |
| Meetup | [meetup/모임예외처리.md](./meetup/모임예외처리.md) |
| Chat | [chat/채팅예외처리.md](./chat/채팅예외처리.md) |
| Location | [location/위치예외처리.md](./location/위치예외처리.md) |
| Report | [report/신고예외처리.md](./report/신고예외처리.md) |
| File | [file/파일예외처리.md](./file/파일예외처리.md) |
| Notification | [notification/알림예외처리.md](./notification/알림예외처리.md) |
| Statistics | [statistics/통계예외처리.md](./statistics/통계예외처리.md) |
