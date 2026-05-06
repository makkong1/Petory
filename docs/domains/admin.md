# Admin 도메인

## 개요

Petory의 운영자 기능 묶음. `ADMIN` 또는 `MASTER` 권한 사용자가 사용자, 케어, 모임, 파일, 신고, 게시판, 실종 제보, 위치 서비스, 통계, 시스템 설정, 관리자용 펫코인 기능을 관리한다.

현재 admin 기능은 두 가지 구조가 공존한다.

1. **Facade 기반 경로**
- `AdminUserFacade`
- `AdminCareAndMeetupFacade`
- `AdminFileFacade`
- `AdminReportFacade`
- `AdminSystemFacade`

2. **도메인 서비스 직접 호출 경로**
- `AdminBoardController`
- `AdminMissingPetController`
- `AdminLocationController`
- `AdminStatisticsController`
- `AdminPaymentController`

즉, “모든 admin API가 facade를 거친다”는 상태는 아직 아니고, 사용자/케어·모임/파일/신고/시스템 설정만 facade 중심으로 정리된 상태다.

---

## 인증/권한

- 보안 경계는 `SecurityConfig`에서 `/api/admin/** -> ADMIN or MASTER`, `/api/master/** -> MASTER`로 잡는다.
- 대부분의 admin controller는 클래스 레벨 `@PreAuthorize`를 사용한다.
- 소프트 삭제된 계정은 로그인, `UserDetailsService`, refresh token 검증, `AuthenticatedUserIdResolver` 경로에서 활성 사용자로 취급되지 않는다.
- 일반 사용자 삭제와 관리자 계정 삭제는 분리되어 있다.
  - 일반 사용자 삭제: `DELETE /api/admin/users/{id}`
  - 관리자 계정 삭제: `DELETE /api/master/admin-users/{id}`

---

## 엔티티

### `AdminAuditLog`

테이블: `admin_audit_log`

관리자 쓰기 행위 감사 로그.

| 필드 | 설명 |
|------|------|
| `adminIdx` | 행위자 관리자 idx |
| `action` | 행위 코드 |
| `targetType` | 대상 타입 |
| `targetIdx` | 대상 idx |
| `detail` | 부가 정보 |
| `createdAt` | 생성 시각 |

인덱스:
- `(admin_idx, created_at)`

### `SystemConfig`

테이블: `system_config`

마스터 설정 키-값 저장소.

| 필드 | 설명 |
|------|------|
| `configKey` | 설정 키, UNIQUE |
| `configValue` | 설정 값 |
| `description` | 설명 |
| `createdAt`, `updatedAt` | 시간 컬럼 |

---

## Facade 레이어

### `AdminUserFacade`

사용자/관리자 계정 관리 전용 facade.

주요 책임:
- 일반 사용자 목록/상세/상태 변경
- 일반 사용자 소프트 삭제/복구
- 관리자 계정 생성/승격/삭제/비밀번호 변경
- 관리자 관련 감사 로그 기록

감사 로그 대상:
- `USER_STATUS_UPDATE`
- `USER_DELETE`
- `USER_RESTORE`
- `ADMIN_CREATE`
- `USER_PROMOTE_ADMIN`
- `ADMIN_DELETE`
- `ADMIN_PASSWORD_CHANGE`

### `AdminCareAndMeetupFacade`

케어 요청과 모임 관리 facade.

주요 책임:
- 케어 요청 필터 목록
- 케어 요청 상세
  - 삭제된 요청도 관리자 상세 조회 가능
- 케어 상태 변경/삭제/복구
- 모임 목록 필터 조회
  - `status`, `q`를 실제 DB 필터로 사용
- 모임 삭제/참여자 조회

감사 로그 대상:
- `CARE_STATUS_UPDATE`
- `CARE_DELETE`
- `CARE_RESTORE`
- `MEETUP_DELETE`

### `AdminFileFacade`

파일 관리 facade.

주요 책임:
- 파일 목록 페이징
- 대상별 파일 조회
- 단건 삭제
- 대상 전체 삭제

**삭제 방식 주의**: 단건 삭제(`deleteFile`)는 `fileRepository.deleteById(id)`로 **하드 삭제**(물리 삭제)이며 소프트 삭제가 아니다. 대상 전체 삭제(`deleteFilesByTarget`)는 `attachmentFileService.deleteAll()`에 위임한다.

감사 로그 대상:
- `FILE_DELETE`
- `FILE_BULK_DELETE`

### `AdminReportFacade`

신고 관리 facade.

주요 책임:
- 신고 목록/상세 조회
- AI 보조 제안 조회
- 신고 처리

**`getReportAssist()` 주의**: Ollama 연동. AI가 결과를 반환하지 않으면 null을 반환한다. 클라이언트는 null 응답을 처리해야 한다.

감사 로그 대상:
- `REPORT_HANDLE` — detail에 `status=...,action=...` 기록

### `AdminSystemFacade`

시스템 설정 facade.

주요 책임:
- 전체 설정 조회
- 단건 설정 조회
- 단건/벌크 upsert
- 설정 변경 감사 로그 기록

감사 로그 대상:
- `SYSTEM_CONFIG_UPDATE` — 단건 upsert마다 기록 (targetIdx는 null, detail에 `key=value` 기록)

특징:
- 기존 키 수정 시 `description`도 함께 갱신 가능 (description 파라미터가 null이면 갱신 안 함)
- 벌크 upsert(`upsertConfigs`)는 키별로 `upsertConfig`를 순차 호출하므로 감사 로그도 키별로 개별 기록됨

### `AdminAuditService`

감사 로그 저장 유틸리티.

시그니처:

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
void log(Long adminIdx, String action, String targetType, Long targetIdx, String detail)
```

특징:
- 감사 로그 실패가 본 트랜잭션 롤백으로 이어지지 않는다.
- facade 기반 admin 쓰기 작업의 공통 감사 경로다.

---

## Controller 구조

### Facade 기반 controller

| Controller | 경로 | 의존 |
|------------|------|------|
| `AdminUserController` | `/api/admin/users` | `AdminUserFacade` |
| `AdminUserManagementController` | `/api/master/admin-users` | `AdminUserFacade` |
| `AdminCareRequestController` | `/api/admin/care-requests` | `AdminCareAndMeetupFacade` |
| `AdminMeetupController` | `/api/admin/meetups` | `AdminCareAndMeetupFacade` |
| `AdminFileController` | `/api/admin/files` | `AdminFileFacade` |
| `AdminReportController` | `/api/admin/reports` | `AdminReportFacade` |
| `AdminSystemController` | `/api/master/system` | `AdminSystemFacade` |

### 서비스 직접 호출 controller

| Controller | 경로 | 실제 의존 |
|------------|------|-----------|
| `AdminBoardController` | `/api/admin/boards` | `BoardService`, `CommentService` |
| `AdminMissingPetController` | `/api/admin/missing-pets` | `MissingPetBoardService`, `MissingPetCommentService` |
| `AdminLocationController` | `/api/admin/location-services` | `LocationServiceAdminService`, `LocationServiceService`, `PublicDataLocationService` |
| `AdminStatisticsController` | `/api/admin/statistics` | `StatisticsService` |
| `AdminPaymentController` | `/api/admin/payment` | `PetCoinService`, `UsersRepository`, `PetCoinTransactionRepository` |

---

## API

### `/api/admin/users` - 일반 사용자 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/users/paging` | 사용자 목록 조회 (`role`, `status`, `q`, `page`, `size`) |
| GET | `/api/admin/users/{id}` | 사용자 상세 조회 |
| PATCH | `/api/admin/users/{id}/status` | 상태 변경 |
| DELETE | `/api/admin/users/{id}` | 일반 사용자 소프트 삭제 |
| POST | `/api/admin/users/{id}/restore` | 삭제 복구 |

### `/api/master/admin-users` - 관리자 계정 관리

권한:
- `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/master/admin-users` | ADMIN 계정 목록 |
| POST | `/api/master/admin-users` | ADMIN 계정 생성 |
| PATCH | `/api/master/admin-users/{id}/promote-to-admin` | 사용자 ADMIN 승격 |
| DELETE | `/api/master/admin-users/{id}` | ADMIN 계정 소프트 삭제 |
| PATCH | `/api/master/admin-users/{id}/password` | ADMIN 비밀번호 변경 |

### `/api/admin/care-requests` - 케어 요청 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/care-requests` | 목록 조회 (`status`, `deleted`, `q`, `page`, `size`) |
| GET | `/api/admin/care-requests/{id}` | 상세 조회 |
| PATCH | `/api/admin/care-requests/{id}/status` | 상태 변경 |
| POST | `/api/admin/care-requests/{id}/delete` | 소프트 삭제 |
| POST | `/api/admin/care-requests/{id}/restore` | 복구 |

주의:
- 관리자 상세 조회는 삭제된 요청도 열 수 있다.

### `/api/admin/meetups` - 모임 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/meetups` | 목록 조회 (`status`, `q`, `page`, `size`) |
| GET | `/api/admin/meetups/{id}` | 상세 조회 |
| DELETE | `/api/admin/meetups/{id}` | 소프트 삭제 |
| GET | `/api/admin/meetups/{id}/participants` | 참여자 목록 |

주의:
- 현재 `status`, `q`는 실제 repository 필터로 반영된다.

### `/api/admin/files` - 파일 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/files` | 파일 목록 조회 (`targetType`, `q`, `page`, `size`) |
| GET | `/api/admin/files/target` | 대상별 파일 조회 |
| DELETE | `/api/admin/files/{id}` | 단건 삭제 |
| DELETE | `/api/admin/files/target` | 대상 전체 삭제 |

### `/api/admin/reports` - 신고 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/reports` | 신고 목록 (`targetType`, `status`) |
| GET | `/api/admin/reports/{id}` | 신고 상세 |
| GET | `/api/admin/reports/{id}/assist` | AI 보조 제안 |
| POST | `/api/admin/reports/{id}/handle` | 신고 처리 |

### `/api/master/system` - 시스템 설정

권한:
- `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/master/system/settings` | 전체 설정 조회 |
| PUT | `/api/master/system/settings` | 벌크 upsert |
| GET | `/api/master/system/settings/{key}` | 단건 조회 |
| PUT | `/api/master/system/settings/{key}` | 단건 upsert |

### `/api/admin/boards` - 커뮤니티 게시판 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/boards/paging` | 게시글 목록 (`status` 기본 `ALL`, `deleted`, `category`, `q`, `page` 기본 `0`, `size` 기본 `20`) |
| GET | `/api/admin/boards/{id}` | 게시글 상세 |
| PATCH | `/api/admin/boards/{id}/blind` | 블라인드 |
| PATCH | `/api/admin/boards/{id}/unblind` | 블라인드 해제 |
| POST | `/api/admin/boards/{id}/delete` | 소프트 삭제 |
| POST | `/api/admin/boards/{id}/restore` | 복구 |
| GET | `/api/admin/boards/{boardId}/comments` | 댓글 목록 (`status` 기본 `ALL`, `deleted`) — `commentService.getCommentsForAdmin()` 호출 후 인메모리 stream 필터링 적용 |
| PATCH | `/api/admin/boards/{boardId}/comments/{commentId}/blind` | 댓글 블라인드 |
| PATCH | `/api/admin/boards/{boardId}/comments/{commentId}/unblind` | 댓글 블라인드 해제 |
| POST | `/api/admin/boards/{boardId}/comments/{commentId}/delete` | 댓글 삭제 |
| POST | `/api/admin/boards/{boardId}/comments/{commentId}/restore` | 댓글 복구 |

현재 상태:
- facade를 거치지 않고 board/comment 서비스 직접 호출
- admin audit과 직접 연결되어 있지 않다

### `/api/admin/missing-pets` - 실종/목격 관리

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/missing-pets/paging` | 목록 (`status`, `deleted`, `q`) |
| GET | `/api/admin/missing-pets/{id}` | 상세 |
| PATCH | `/api/admin/missing-pets/{id}/status` | 상태 변경 |
| POST | `/api/admin/missing-pets/{id}/delete` | 삭제 |
| POST | `/api/admin/missing-pets/{id}/restore` | 복구 |
| GET | `/api/admin/missing-pets/{boardId}/comments` | 댓글 목록 (`deleted`) |
| POST | `/api/admin/missing-pets/{boardId}/comments/{commentId}/delete` | 댓글 삭제 |

현재 상태:
- facade를 거치지 않고 missing pet 서비스 직접 호출

### `/api/admin/location-services` - 위치 서비스 관리

권한:
- 목록 조회: `ADMIN`, `MASTER`
- 데이터 적재/임포트: `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/location-services` | 지역 서비스 목록 (`sido`, `sigungu`, `eupmyeondong`, `roadName`, `category`, `size`, `q`) |
| POST | `/api/admin/location-services/load-data` | 초기 데이터 적재 |
| POST | `/api/admin/location-services/import-public-data` | CSV 업로드 임포트 (`multipart/form-data`, 파일 파라미터명 `file`) |
| POST | `/api/admin/location-services/import-public-data-path` | CSV 경로 임포트 |

### `/api/admin/statistics` - 통계 관리

권한:
- `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/admin/statistics/daily` | 일별 통계 (`startDate`, `endDate`; 미입력 시 최근 30일) |
| GET | `/api/admin/statistics/weekly` | 주별 통계 (`year`; 미입력 시 올해) |
| GET | `/api/admin/statistics/monthly` | 월별 통계 (`year`; 미입력 시 올해) |
| GET | `/api/admin/statistics/summary` | 오늘 스냅샷 |
| POST | `/api/admin/statistics/backfill` | 통계 백필 (`startDate`, `endDate` 필수 — ISO date 형식) |

### `/api/admin/payment` - 관리자용 펫코인 기능

위치:
- `domain/payment/controller/AdminPaymentController`

권한:
- `ADMIN`, `MASTER`

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/admin/payment/charge` | 관리자 수동 코인 지급 |
| GET | `/api/admin/payment/balance/{userId}` | 사용자 잔액 조회 |
| GET | `/api/admin/payment/transactions/{userId}` | 사용자 거래 내역 조회 |

현재 상태:
- admin facade를 사용하지 않고 payment 서비스/레포지토리를 직접 사용한다

---

## 현재 규칙

1. **일반 사용자 삭제와 관리자 삭제는 분리**된다.
2. **삭제 계정 인증 차단**이 적용되어, soft delete된 계정은 로그인/refresh/resolver 경로에서 막힌다.
3. **사용자 삭제 시 refresh token도 함께 제거**된다.
4. **facade가 담당하는 admin 쓰기 경로만 감사 로그와 직접 연결**되어 있다.
5. **모든 admin controller가 facade를 쓰는 상태는 아니다.**
6. **master 전용 경로**는 `/api/master/**`, 일부 `/api/admin/**` 클래스 레벨 `hasRole('MASTER')`로 한 번 더 제한한다.

---

## 현재 한계

1. 게시판, 실종 제보, 위치 서비스, 통계, 관리자용 결제는 facade 기반으로 통일되지 않았다.
2. 따라서 admin audit 적용 범위도 전 admin API에 일괄적이지 않다.
3. `AdminStatisticsController`는 `/api/admin/**` 경로이지만 실제 권한은 `MASTER` 전용이다.

---

## 관련 문서

- 아키텍처: `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- 리팩토링 기록: `docs/refactoring/admin/2026-04-18-admin-domain-redesign.md`
- 인증/계약 경계 강화: `docs/refactoring/admin/2026-05-04-admin-auth-contract-hardening.md`
