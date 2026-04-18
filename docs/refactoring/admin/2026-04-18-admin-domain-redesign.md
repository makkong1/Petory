# Admin 도메인 전체 재설계 (2026-04-18)

## 문제

1. **P0 버그**: `AdminMeetupController.deleteMeetup(id, "ADMIN")` → `MeetupService.deleteMeetup()`이 `"ADMIN"` 문자열로 `usersRepository.findByIdString()` 호출 → `UserNotFoundException` 발생
2. **P0 버그**: `AdminCareRequestController.restoreCareRequest()` → `throw new UnsupportedOperationException()` — 구현 없이 500 반환
3. **Controller가 Repository 직접 호출**: `AdminUserManagementController`가 `UsersRepository`, `UsersConverter`, `PasswordEncoder`를 직접 주입
4. **감사 로그 부재**: 관리자의 모든 쓰기 행위(삭제, 상태변경, 계정 생성 등)가 기록되지 않음
5. **풀스캔 쿼리**: `AdminFileController`가 `findAll()` 전체 조회 후 메모리 필터
6. **시스템 설정 하드코딩**: `AdminSystemController`가 in-memory Map으로 설정 관리, DB 영속 없음

## 원인

- Admin 도메인에 전용 Facade 레이어가 없어 Controller가 여러 도메인 내부에 직접 의존
- `deleteMeetup(id, userId)` 메서드 시그니처가 관리자 케이스를 고려하지 않음
- `restoreCareRequest` 기능이 처음부터 구현되지 않은 채 배포됨

## 해결 (Before → After)

### 아키텍처

**Before**
```
AdminXxxController → MeetupService / UsersRepository / CareRequestService (직접 의존)
```

**After**
```
AdminXxxController → AdminXxxFacade → 도메인 Service · Repository
                                    → AdminAuditService (비동기 감사 로그)
```

### 신규 파일

| 파일 | 역할 |
|------|------|
| `AdminAuditLog.java` | 감사 로그 엔티티 (불변) |
| `SystemConfig.java` | 시스템 설정 키-값 엔티티 |
| `AdminAuditLogRepository.java` | 감사 로그 저장 |
| `SystemConfigRepository.java` | 설정 저장 |
| `AdminAuditService.java` | `@Async + REQUIRES_NEW` 감사 로그 기록 |
| `AdminUserFacade.java` | 사용자 관리 통합 Facade |
| `AdminCareAndMeetupFacade.java` | 케어·모임 관리 Facade |
| `AdminSystemFacade.java` | 시스템 설정 Facade |

### Repository 확장

| Repository | 추가된 메서드 |
|------------|-------------|
| `UsersRepository` | `findAllForAdmin(role, status, keyword, pageable)` |
| `CareRequestRepository` | `findAllForAdmin(status, deleted, keyword, pageable)` |
| `AttachmentFileRepository` | `findAllForAdmin(targetType, keyword, pageable)` |

JPQL 패턴: `CAST(u.role AS string) = :role` (문자열 파라미터 vs Enum 필드 비교)

### P0 버그 수정

```java
// Before: AdminMeetupController
meetupService.deleteMeetup(id, "ADMIN"); // → UserNotFoundException

// After: MeetupService에 신규 메서드 추가
public void deleteMeetupForAdmin(Long meetupIdx) {
    Meetup meetup = meetupRepository.findById(meetupIdx).orElseThrow(MeetupNotFoundException::new);
    meetup.setIsDeleted(true);
    meetup.setDeletedAt(LocalDateTime.now());
}
```

```java
// Before: AdminCareRequestController
throw new UnsupportedOperationException("복구 기능이 구현되지 않았습니다."); // → 500

// After: CareRequestService에 구현
public CareRequestDTO restoreForAdmin(Long id) {
    CareRequest request = careRequestRepository.findById(id).orElseThrow(...);
    request.setIsDeleted(false);
    request.setDeletedAt(null);
    return careRequestConverter.toDTO(careRequestRepository.save(request));
}
```

### 컨트롤러 변경 요약

| 컨트롤러 | Before | After |
|----------|--------|-------|
| `AdminUserController` | `UsersService` 직접 호출, 필터 없음 | `AdminUserFacade`, role/status/q 필터 |
| `AdminUserManagementController` | Repository 직접 주입, no-op `changeAdminRole` | `AdminUserFacade`, 불필요 엔드포인트 제거 |
| `AdminCareRequestController` | `restoreCareRequest` UnsupportedOperationException | `AdminCareAndMeetupFacade`, 완전 구현 |
| `AdminMeetupController` | UserNotFoundException 버그 | `AdminCareAndMeetupFacade`, 수정된 메서드 |
| `AdminFileController` | `findAll()` 풀스캔 | `findAllForAdmin()` 페이징 |
| `AdminSystemController` | in-memory Map, scheduler stub | `AdminSystemFacade`, DB 영속 |

## 성능 비교

| 항목 | Before | After |
|------|--------|-------|
| 파일 목록 조회 | `findAll()` 전체 로드 | DB 레벨 페이징 (LIMIT/OFFSET) |
| 사용자 목록 | 페이지는 있으나 role/status 필터 없음 | 복합 필터 JPQL |
| 관리자 쓰기 감사 | 없음 | 비동기 DB 기록 (`admin_audit_log`) |
| 시스템 설정 | 메모리 (재시작 시 초기화) | DB 영속 (`system_config`) |

## 참고

- `@Async` 동작: `PetoryApplication`에 `@EnableAsync` 이미 있음
- `REQUIRES_NEW` 전파: 감사 로그 실패가 본 트랜잭션 롤백으로 이어지지 않음 (eventual consistency 수용)
- `upsertConfig` 자기 호출: `upsertConfigs → upsertConfig` Spring AOP 미적용이지만 외부 `@Transactional`로 커버
