# Admin 도메인

> 기준: 현재 `domain/admin`, 관리자용 결제 컨트롤러, 프론트 `AdminPanel`/관리자 API 코드.  
> 역할: Petory 운영자가 여러 도메인의 데이터를 조회·상태 변경·삭제·복구·집계할 수 있게 하는 관리자 API/화면 계층이다.

---

## 1. 도메인 책임

Admin 도메인은 독립 비즈니스 도메인이라기보다 운영자용 gateway에 가깝다.

- 사용자/관리자 계정 관리
- 신고 처리
- 커뮤니티 게시글/댓글 모더레이션
- 실종 제보/댓글 관리
- 케어 요청 관리
- 모임 관리
- 파일 메타데이터 관리
- 위치 서비스 데이터 관리
- 통계 조회와 backfill
- 시스템 설정 관리
- 관리자용 펫코인 지급/조회
- facade 기반 작업의 감사 로그 기록

현재 구조는 두 가지가 공존한다.

| 구조 | 대상 |
|---|---|
| Facade 기반 | 사용자, 관리자 계정, 케어, 모임, 파일, 신고, 시스템 설정 |
| 도메인 서비스 직접 호출 | 게시판, 실종 제보, 위치 서비스, 통계, 관리자 결제 |

따라서 "모든 관리자 API가 Admin facade와 audit를 거친다"는 상태는 아니다.

---

## 2. 권한 경계

`SecurityConfig` 전역 규칙:

| 경로 | 권한 |
|---|---|
| `/api/master/**` | `MASTER` |
| `/api/admin/**` | `ADMIN` 또는 `MASTER` |

일부 컨트롤러는 클래스 또는 메서드 레벨에서 더 좁게 제한한다.

- `AdminSystemController`: `/api/master/system`, `MASTER`
- `AdminUserManagementController`: `/api/master/admin-users`, `MASTER`
- `AdminStatisticsController`: `/api/admin/statistics` 경로지만 `MASTER`
- `AdminLocationController`: 목록은 `ADMIN/MASTER`, 데이터 적재/임포트는 `MASTER`

삭제 계정 인증 경계:

- `UsersDetailsServiceImpl`은 `findActiveByIdString()`으로 로그인 사용자를 조회한다.
- `AuthenticatedUserIdResolver`도 `findActiveByIdString()`으로 현재 사용자 PK를 해석한다.
- refresh token 검증은 `findActiveByRefreshToken()`을 사용한다.
- 관리자/일반 사용자 soft delete 시 refresh token을 제거한다.

---

## 3. 주요 코드 위치

| 영역 | 파일 |
|---|---|
| 사용자 관리 | `AdminUserController`, `AdminUserFacade` |
| 관리자 계정 관리 | `AdminUserManagementController`, `AdminUserFacade` |
| 케어/모임 관리 | `AdminCareRequestController`, `AdminMeetupController`, `AdminCareAndMeetupFacade` |
| 파일 관리 | `AdminFileController`, `AdminFileFacade` |
| 신고 관리 | `AdminReportController`, `AdminReportFacade` |
| 시스템 설정 | `AdminSystemController`, `AdminSystemFacade` |
| 감사 로그 | `AdminAuditService`, `AdminAuditLog` |
| 커뮤니티 관리 | `AdminBoardController` |
| 실종 제보 관리 | `AdminMissingPetController` |
| 위치 서비스 관리 | `AdminLocationController` |
| 통계 관리 | `AdminStatisticsController`, `StatisticsService` |
| 관리자 결제 | `domain/payment/controller/AdminPaymentController.java` |
| 프론트 관리자 패널 | `frontend/src/components/Admin/AdminPanel.js` |

---

## 4. Admin 전용 엔티티

### 4.1 AdminAuditLog

테이블명은 `admin_audit_log`다.

| 필드 | 설명 |
|---|---|
| `idx` | 감사 로그 PK |
| `adminIdx` | 작업한 관리자 ID |
| `action` | 작업 코드 |
| `targetType` | 대상 타입 문자열 |
| `targetIdx` | 대상 ID |
| `detail` | 부가 정보 |
| `createdAt` | 생성 시각 |

인덱스:

```text
idx_audit_admin_created (admin_idx, created_at)
```

`AdminAuditService.log()`는 `@Async` + `REQUIRES_NEW`로 실행된다. 저장 실패는 원본 관리자 작업을 롤백하지 않고 로그만 남긴다.

### 4.2 SystemConfig

테이블명은 `system_config`다.

| 필드 | 설명 |
|---|---|
| `idx` | 설정 PK |
| `configKey` | 설정 키. unique |
| `configValue` | 설정 값 |
| `description` | 설명 |
| `createdAt`, `updatedAt` | `BaseTimeEntity`에서 관리 |

---

## 5. Facade 기반 관리자 기능

### 5.1 사용자 관리

`/api/admin/users` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/users/paging` | 사용자 페이징 목록. `role`, `status`, `q`, `page`, `size` |
| GET | `/api/admin/users/{id}` | 사용자 상세 |
| PATCH | `/api/admin/users/{id}/status` | 사용자 상태/경고/정지 정보 변경 |
| DELETE | `/api/admin/users/{id}` | 일반 사용자 탈퇴 처리 (복구 불가, id/username/nickname/email 즉시 익명화) |

감사 로그:

- `USER_STATUS_UPDATE`
- `USER_DELETE`

`deleteUser()`는 대상 role이 `ADMIN` 또는 `MASTER`이면 실패한다. 관리자 계정 삭제는 MASTER 전용 경로를 써야 한다.

### 5.2 관리자 계정 관리

`/api/master/admin-users` (`MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/master/admin-users` | ADMIN 계정 목록 |
| POST | `/api/master/admin-users` | ADMIN 계정 생성 |
| PATCH | `/api/master/admin-users/{id}/promote-to-admin` | 일반 사용자 ADMIN 승격 |
| DELETE | `/api/master/admin-users/{id}` | ADMIN 계정 soft delete |
| PATCH | `/api/master/admin-users/{id}/password` | ADMIN 비밀번호 변경 |

감사 로그:

- `ADMIN_CREATE`
- `USER_PROMOTE_ADMIN`
- `ADMIN_DELETE`
- `ADMIN_PASSWORD_CHANGE`

MASTER 계정은 이 경로에서 생성/삭제/변경 대상이 아니다.

### 5.3 케어 요청 관리

`/api/admin/care-requests` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/care-requests` | 목록. `status`, `deleted`, `q`, `page`, `size` |
| GET | `/api/admin/care-requests/{id}` | 상세 |
| PATCH | `/api/admin/care-requests/{id}/status?status=` | 상태 변경 |
| POST | `/api/admin/care-requests/{id}/delete` | soft delete |
| POST | `/api/admin/care-requests/{id}/restore` | 복구 |

감사 로그:

- `CARE_STATUS_UPDATE`
- `CARE_DELETE`
- `CARE_RESTORE`

### 5.4 모임 관리

`/api/admin/meetups` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/meetups` | 목록. `status`, `q`, `page`, `size` |
| GET | `/api/admin/meetups/{id}` | 상세 |
| DELETE | `/api/admin/meetups/{id}` | soft delete |
| GET | `/api/admin/meetups/{id}/participants` | 참여자 목록 |

감사 로그:

- `MEETUP_DELETE`

### 5.5 파일 관리

`/api/admin/files` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/files` | 파일 메타데이터 목록. `targetType`, `q`, `page`, `size` |
| GET | `/api/admin/files/target` | 대상별 파일 조회. `targetType`, `targetIdx` |
| DELETE | `/api/admin/files/{id}` | 파일 메타데이터 단건 삭제 |
| DELETE | `/api/admin/files/target` | 대상 파일 메타데이터 일괄 삭제 |

감사 로그:

- `FILE_DELETE`
- `FILE_BULK_DELETE`

주의: 삭제는 `file` row 삭제다. 로컬 디스크의 물리 파일 삭제는 하지 않는다.

### 5.6 신고 관리

`/api/admin/reports` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/reports` | 신고 목록. `targetType`, `status` |
| GET | `/api/admin/reports/{id}` | 신고 상세 |
| POST | `/api/admin/reports/{id}/handle` | 신고 처리 |

감사 로그:

- `REPORT_HANDLE`

현재 코드에는 `GET /api/admin/reports/{id}/assist`가 없다. 오래된 문서에 남아 있는 Ollama/AI 신고 보조 API는 현재 구현 기준에서 제외한다.

### 5.7 시스템 설정

`/api/master/system` (`MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/master/system/settings` | 전체 설정 조회 |
| PUT | `/api/master/system/settings` | 설정 bulk upsert |
| GET | `/api/master/system/settings/{key}` | 단건 설정 조회 |
| PUT | `/api/master/system/settings/{key}` | 단건 upsert |

감사 로그:

- `SYSTEM_CONFIG_UPDATE`

bulk upsert는 key별로 `upsertConfig()`를 호출하므로 감사 로그도 key별로 남는다.

---

## 6. 도메인 서비스 직접 호출 관리자 기능

### 6.1 커뮤니티 관리

`/api/admin/boards` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/boards/paging` | 게시글 목록. `status`, `deleted`, `category`, `q`, `page`, `size` |
| GET | `/api/admin/boards/{id}` | 게시글 상세. 조회수 증가 없음 |
| PATCH | `/api/admin/boards/{id}/blind` | 게시글 블라인드 |
| PATCH | `/api/admin/boards/{id}/unblind` | 게시글 블라인드 해제 |
| POST | `/api/admin/boards/{id}/delete` | 게시글 soft delete |
| POST | `/api/admin/boards/{id}/restore` | 게시글 복구 |
| GET | `/api/admin/boards/{boardId}/comments` | 댓글 목록. `status`, `deleted` |
| PATCH | `/api/admin/boards/{boardId}/comments/{commentId}/blind` | 댓글 블라인드 |
| PATCH | `/api/admin/boards/{boardId}/comments/{commentId}/unblind` | 댓글 블라인드 해제 |
| POST | `/api/admin/boards/{boardId}/comments/{commentId}/delete` | 댓글 삭제 |
| POST | `/api/admin/boards/{boardId}/comments/{commentId}/restore` | 댓글 복구 |

현재 `BoardService`, `CommentService`를 직접 호출한다. AdminAuditService와 직접 연결되어 있지 않다.

### 6.2 실종 제보 관리

`/api/admin/missing-pets` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/missing-pets/paging` | 목록. `status`, `deleted`, `q`, `page`, `size` |
| GET | `/api/admin/missing-pets/{id}` | 상세 |
| PATCH | `/api/admin/missing-pets/{id}/status` | 상태 변경 |
| POST | `/api/admin/missing-pets/{id}/delete` | soft delete |
| POST | `/api/admin/missing-pets/{id}/restore` | 복구 |
| GET | `/api/admin/missing-pets/{boardId}/comments` | 댓글 목록. `deleted` |
| POST | `/api/admin/missing-pets/{boardId}/comments/{commentId}/delete` | 댓글 삭제 |

현재 `MissingPetBoardService`, `MissingPetCommentService`를 직접 호출한다. AdminAuditService와 직접 연결되어 있지 않다.

### 6.3 위치 서비스 관리

`/api/admin/location-services`

| Method | URL | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/admin/location-services` | `ADMIN`, `MASTER` | 장소 목록. `sido`, `sigungu`, `category`, `size`, `q` |
| POST | `/api/admin/location-services/load-data` | `MASTER` | 초기 데이터 로드 |
| POST | `/api/admin/location-services/import-public-data` | `MASTER` | CSV 업로드 임포트. multipart `file` |
| POST | `/api/admin/location-services/import-public-data-path` | `MASTER` | 서버 경로 기반 CSV 임포트 |

CSV 업로드 제한:

- 최대 200MB
- 확장자 `.csv`
- 허용 content type: `text/csv`, `application/csv`, `text/plain`, `application/octet-stream`, `application/vnd.ms-excel`

현재 프론트 `LocationServiceManagementSection`은 CSV 업로드 임포트 중심이다.

### 6.4 통계 관리

`/api/admin/statistics` (`MASTER`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/admin/statistics/daily` | 일별 통계. `startDate`, `endDate`, 기본 최근 30일 |
| GET | `/api/admin/statistics/weekly` | 주별 통계. `year` |
| GET | `/api/admin/statistics/monthly` | 월별 통계. `year` |
| GET | `/api/admin/statistics/summary` | 오늘 스냅샷 |
| POST | `/api/admin/statistics/backfill` | 수동 집계. `startDate`, `endDate` |

프론트 `SystemDashboardSection`은 현재 daily 조회와 최근 30일 backfill을 사용한다.

### 6.5 관리자 결제

`/api/admin/payment` (`ADMIN`, `MASTER`)

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/admin/payment/charge` | 특정 사용자에게 펫코인 지급 |
| GET | `/api/admin/payment/balance/{userId}` | 특정 사용자 잔액 조회 |
| GET | `/api/admin/payment/transactions/{userId}` | 특정 사용자 거래 내역 페이징 |

현재 백엔드 API는 있으나 `AdminPanel` 메뉴에는 관리자 결제 섹션이 없다.

---

## 7. 프론트 관리자 패널

`AdminPanel`은 `usePermission().requireAdmin()`으로 `ADMIN` 또는 `MASTER`만 접근시킨다.

현재 메뉴:

- 전체 대시보드
- 사용자 관리
- 신고 관리
- 커뮤니티 관리
- 실종/목격 관리
- 케어 서비스 관리
- 지역 서비스 관리
- 산책 모임 관리
- 파일 관리

현재 메뉴에 없는 백엔드 관리자 기능:

- `/api/master/system` 시스템 설정
- `/api/admin/payment` 관리자 결제
- `/api/admin/statistics/weekly`, `/monthly`, `/summary` 전용 화면

---

## 8. 현재 한계와 주의사항

- 모든 관리자 쓰기 작업이 audit를 남기는 것은 아니다. facade 기반 경로만 직접 감사 로그와 연결되어 있다.
- Board/MissingPet/Location/Statistics/AdminPayment는 facade 계층으로 통일되어 있지 않다.
- `AdminReportController`에는 오래된 문서의 `/assist` API가 없다.
- `fileAdminApi.getStatistics()`는 프론트 함수는 있지만 백엔드 `GET /api/admin/files/statistics`가 없다.
- `UserModal`/`userApi`에는 `POST /api/admin/users`, `PUT /api/admin/users/{id}` 같은 오래된 함수가 남아 있지만 현재 백엔드 `AdminUserController`에는 없다.
- 관리자 결제와 시스템 설정은 백엔드 API가 있지만 현재 `AdminPanel` 메뉴에는 없다.
- `AdminStatisticsController`는 `/api/admin/**` 경로지만 클래스 레벨 권한은 `MASTER` 전용이다.
- 위치 CSV 경로 임포트는 서버 파일 경로를 받으므로 운영에서는 접근 경로와 파일 권한을 제한해야 한다.

---

## 9. DomainV2 페이지에 넣을 포인트

- Admin은 별도 비즈니스보다 여러 도메인의 운영 API를 모으는 gateway 계층이다.
- `/api/admin/**`와 `/api/master/**`를 분리해 `ADMIN/MASTER`와 `MASTER` 전용 권한 경계를 둔다.
- Facade 기반 경로는 도메인 서비스 호출과 감사 로그 기록을 한곳에 묶었다.
- `AdminAuditService`는 비동기 별도 트랜잭션으로 로그 실패가 원본 작업에 영향을 주지 않게 했다.
- 사용자 soft delete 계정은 로그인, refresh, resolver 경로에서 active 사용자 조회로 차단한다.
- 현재 한계는 일부 관리자 컨트롤러가 여전히 도메인 서비스를 직접 호출하고, audit 적용 범위가 균일하지 않다는 점이다.
