# Admin 도메인

## 개요

Petory 서비스의 운영 관리 도메인. ADMIN / MASTER 권한 사용자가 사용자·케어·모임·파일·신고·시스템 설정을 관리한다.

**아키텍처 특징**: 사용자/케어·모임/파일/신고/시스템 설정의 핵심 admin API는 각 `AdminXxxFacade`를 통해 도메인 서비스·레포지토리를 조합한다. 이 facade가 담당하는 쓰기 행위는 `AdminAuditService`로 DB에 비동기 기록한다.

---

## 엔티티

### AdminAuditLog

**테이블명**: `admin_audit_log`

관리자 행위 감사 로그. 불변 엔티티 (setter 없음).

| 필드 | 타입 | 설명 |
|------|------|------|
| `idx` | Long (PK) | |
| `adminIdx` | Long (NOT NULL) | 행위자 관리자 idx |
| `action` | String (50) | 행위 코드 (예: `USER_DELETE`, `CARE_RESTORE`) |
| `targetType` | String (30) | 대상 타입 (예: `USER`, `CARE_REQUEST`, `SYSTEM`) |
| `targetIdx` | Long (nullable) | 대상 엔티티 idx (SYSTEM 설정 변경 시 null) |
| `detail` | String (500) | 부가 정보 (예: `status=BANNED`) |
| `createdAt` | LocalDateTime | `@CreatedDate` 자동 기록 |

**인덱스**: `(admin_idx, created_at)` 복합 인덱스

---

### SystemConfig

**테이블명**: `system_config`

시스템 설정 키-값 저장. `configKey`는 UNIQUE.

| 필드 | 타입 | 설명 |
|------|------|------|
| `idx` | Long (PK) | |
| `configKey` | String (100, UNIQUE) | 설정 키 |
| `configValue` | String (500) | 설정 값 |
| `description` | String (200, nullable) | 설명 |
| `createdAt`, `updatedAt` | LocalDateTime | `BaseTimeEntity` 상속 |

**인덱스**: `config_key` UNIQUE 인덱스

---

## Facade 레이어

### AdminUserFacade

사용자 관리 통합 Facade.

| 메서드 | 설명 | 감사 로그 |
|--------|------|-----------|
| `getUsers(role, status, keyword, page, size)` | 필터+페이징 목록 | — |
| `getUser(id)` | 단건 조회 | — |
| `updateStatus(targetId, dto, adminIdx)` | 상태 변경 | `USER_STATUS_UPDATE` |
| `deleteUser(targetId, adminIdx)` | 소프트 삭제 (ADMIN/MASTER 계정 제외) | `USER_DELETE` |
| `restoreUser(targetId, adminIdx)` | 삭제 복구 | `USER_RESTORE` |
| `getAdminUsers()` | ADMIN 계정 목록 | — |
| `createAdminUser(dto, masterIdx)` | ADMIN 계정 생성 (MASTER 전용) | `ADMIN_CREATE` |
| `promoteToAdmin(targetId, masterIdx)` | ADMIN으로 승격 (MASTER 전용) | `USER_PROMOTE_ADMIN` |
| `deleteAdminUser(targetId, masterIdx)` | ADMIN 계정 소프트 삭제 | `ADMIN_DELETE` |
| `changeAdminPassword(targetId, newPassword, masterIdx)` | ADMIN 비밀번호 변경 | `ADMIN_PASSWORD_CHANGE` |

---

### AdminCareAndMeetupFacade

케어 요청 및 모임 관리 통합 Facade.

| 메서드 | 설명 | 감사 로그 |
|--------|------|-----------|
| `getCareRequests(status, deleted, keyword, page, size)` | 케어 요청 필터+페이징 | — |
| `getCareRequest(id)` | 단건 조회 (삭제된 요청도 관리자 조회 가능) | — |
| `updateCareStatus(id, status, adminIdx)` | 상태 변경 | `CARE_STATUS_UPDATE` |
| `deleteCareRequest(id, adminIdx)` | 소프트 삭제 | `CARE_DELETE` |
| `restoreCareRequest(id, adminIdx)` | 삭제 복구 | `CARE_RESTORE` |
| `getMeetups(status, keyword, page, size)` | 모임 목록 페이징 | — |
| `getMeetup(id)` | 단건 조회 | — |
| `deleteMeetup(id, adminIdx)` | 소프트 삭제 | `MEETUP_DELETE` |
| `getMeetupParticipants(id)` | 참여자 목록 | — |

---

### AdminFileFacade

파일 관리 Facade.

| 메서드 | 설명 | 감사 로그 |
|--------|------|-----------|
| `getFiles(targetType, keyword, page, size)` | 파일 목록 페이징 | — |
| `getFilesByTarget(targetType, targetIdx)` | 대상별 파일 조회 | — |
| `deleteFile(id, adminIdx)` | 단건 삭제 | `FILE_DELETE` |
| `deleteFilesByTarget(targetType, targetIdx, adminIdx)` | 대상 전체 삭제 | `FILE_BULK_DELETE` |

---

### AdminReportFacade

신고 관리 Facade.

| 메서드 | 설명 | 감사 로그 |
|--------|------|-----------|
| `getReports(targetType, status)` | 신고 목록 조회 | — |
| `getReportDetail(id)` | 신고 상세 조회 | — |
| `getReportAssist(id)` | AI 보조 제안 조회 | — |
| `handleReport(id, request, adminIdx)` | 신고 처리 | `REPORT_HANDLE` |

---

### AdminSystemFacade

시스템 설정 DB 영속 Facade.

| 메서드 | 설명 | 감사 로그 |
|--------|------|-----------|
| `getAllConfigs()` | 전체 설정 `Map<String,String>` 반환 | — |
| `getConfig(key, defaultValue)` | 단건 설정 조회 | — |
| `upsertConfig(key, value, description, adminIdx)` | Insert-or-Update | `SYSTEM_CONFIG_UPDATE` |
| `upsertConfigs(settings, adminIdx)` | 벌크 Upsert | 각 키별 `SYSTEM_CONFIG_UPDATE` |

---

### AdminAuditService

비동기 감사 로그 기록 유틸리티.

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(Long adminIdx, String action, String targetType, Long targetIdx, String detail)
```

- `@Async`: 감사 로그 저장 실패가 본 트랜잭션 롤백으로 이어지지 않음
- `REQUIRES_NEW`: 별도 트랜잭션으로 분리

---

## API

### `/api/admin/users` — 일반 사용자 관리 (ADMIN, MASTER)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/admin/users/paging` | 사용자 목록 | `?role&status&q&page=0&size=20` | `UserPageResponseDTO` |
| GET | `/api/admin/users/{id}` | 단건 조회 | — | `UsersDTO` |
| PATCH | `/api/admin/users/{id}/status` | 상태 변경 | `UsersDTO` body | `UsersDTO` |
| DELETE | `/api/admin/users/{id}` | 소프트 삭제 | — | 204 |
| POST | `/api/admin/users/{id}/restore` | 복구 | — | `UsersDTO` |

---

### `/api/master/admin-users` — 관리자 계정 관리 (MASTER 전용)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/master/admin-users` | ADMIN 계정 목록 | — | `List<UsersDTO>` |
| POST | `/api/master/admin-users` | ADMIN 계정 생성 | `UsersDTO` (role=ADMIN 필수) | `UsersDTO` |
| PATCH | `/api/master/admin-users/{id}/promote-to-admin` | ADMIN 승격 | — | `UsersDTO` |
| DELETE | `/api/master/admin-users/{id}` | ADMIN 소프트 삭제 | — | 204 |
| PATCH | `/api/master/admin-users/{id}/password` | 비밀번호 변경 | `{"newPassword": "..."}` | 204 |

---

### `/api/admin/care-requests` — 케어 요청 관리 (ADMIN, MASTER)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/admin/care-requests` | 목록 페이징 | `?status&deleted&q&page=0&size=20` | `Page<CareRequestDTO>` |
| GET | `/api/admin/care-requests/{id}` | 단건 조회 | — | `CareRequestDTO` |
| PATCH | `/api/admin/care-requests/{id}/status` | 상태 변경 | `?status=...` | `CareRequestDTO` |
| POST | `/api/admin/care-requests/{id}/delete` | 소프트 삭제 | — | 204 |
| POST | `/api/admin/care-requests/{id}/restore` | 복구 | — | `CareRequestDTO` |

---

### `/api/admin/meetups` — 모임 관리 (ADMIN, MASTER)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/admin/meetups` | 목록 페이징 | `?status&q&page=0&size=20` | `Page<MeetupDTO>` |
| GET | `/api/admin/meetups/{id}` | 단건 조회 | — | `MeetupDTO` |
| DELETE | `/api/admin/meetups/{id}` | 소프트 삭제 | — | 204 |
| GET | `/api/admin/meetups/{id}/participants` | 참여자 목록 | — | `List<MeetupParticipantsDTO>` |

---

### `/api/admin/files` — 파일 관리 (ADMIN, MASTER)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/admin/files` | 목록 페이징 | `?targetType&q&page=0&size=20` | `Page<FileDTO>` |
| GET | `/api/admin/files/target` | 대상별 파일 | `?targetType&targetIdx` | `List<FileDTO>` |
| DELETE | `/api/admin/files/{id}` | 단건 삭제 | — | 204 |
| DELETE | `/api/admin/files/target` | 대상 전체 삭제 | `?targetType&targetIdx` | 204 |

---

### `/api/admin/reports` — 신고 관리 (ADMIN, MASTER)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/admin/reports` | 신고 목록 | `?targetType&status` | `List<ReportDTO>` |
| GET | `/api/admin/reports/{id}` | 신고 상세 | — | `ReportDetailDTO` |
| GET | `/api/admin/reports/{id}/assist` | AI 보조 제안 | — | `ReportAssistSuggestion` |
| POST | `/api/admin/reports/{id}/handle` | 신고 처리 | `ReportHandleRequest` body | `ReportDTO` |

---

### `/api/master/system` — 시스템 설정 (MASTER 전용)

| Method | URL | 설명 | Request | Response |
|--------|-----|------|---------|----------|
| GET | `/api/master/system/settings` | 전체 설정 | — | `Map<String,String>` |
| PUT | `/api/master/system/settings` | 벌크 Upsert | `Map<String,String>` body | `{message, count}` |
| GET | `/api/master/system/settings/{key}` | 단건 조회 | — | `{key, value}` |
| PUT | `/api/master/system/settings/{key}` | 단건 Upsert | `{value, description}` | 204 |

---

## 비즈니스 로직 핵심 규칙

1. **ADMIN/MASTER 삭제 분리**: 일반 사용자 삭제(`/api/admin/users/{id} DELETE`)는 ADMIN/MASTER 계정에 적용 불가. ADMIN 계정 삭제는 `/api/master/admin-users/{id} DELETE`만 사용.
2. **삭제 계정 인증 차단**: 소프트 삭제된 계정은 로그인/`UserDetailsService`/refresh token 검증/관리자 식별 해석 경로에서 활성 사용자로 취급되지 않는다.
3. **소프트 삭제 일관성**: 사용자·관리자 삭제 시 `isDeleted=true`, `deletedAt=LocalDateTime.now()`를 기록하고 refresh token도 함께 제거한다.
4. **감사 로그 비동기**: facade가 담당하는 관리자 쓰기 작업은 `AdminAuditService.log()`를 호출한다. 로그 실패가 본 트랜잭션을 중단시키지 않음.
5. **시스템 설정 Upsert**: `SystemConfig`는 `configKey` 기준 Insert-or-Update. DB UNIQUE 제약으로 보호하며, 단건 수정 시 `description`도 함께 갱신할 수 있다.
6. **promoteToAdmin 멱등성**: 이미 ADMIN인 사용자는 감사 로그 없이 즉시 반환.

---

## 관련 문서

- 아키텍처: `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- 리팩토링 기록: `docs/refactoring/admin/2026-04-18-admin-domain-redesign.md`
- 인증/계약 경계 강화: `docs/refactoring/admin/2026-05-04-admin-auth-contract-hardening.md`
