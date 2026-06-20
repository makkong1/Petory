# Report 도메인

> 기준: 현재 `domain/report`, 관리자 신고 처리 API, `UserSanctionService`, 프론트 신고/관리자 화면 코드.  
> 역할: 사용자 신고를 접수하고, 관리자가 신고 상태·조치 기록을 남기며, 일부 조치를 User 제재 시스템으로 연결한다.

---

## 1. 도메인 책임

Report 도메인은 여러 도메인의 콘텐츠 또는 사용자를 하나의 `report` 테이블로 신고하는 공통 모더레이션 도메인이다.

- 일반 사용자 신고 접수
- 신고 대상 존재 검증
- 동일 사용자·동일 대상 중복 신고 방지
- 관리자 신고 목록/상세 조회
- 신고 상태 처리와 관리자 메모 기록
- `WARN_USER`, `SUSPEND_USER` 조치 시 UserSanction 연동
- 관리자 감사 로그 기록
- 통계 도메인의 신규/처리 신고 집계 대상

현재 Report 자체는 신고 대상 콘텐츠를 직접 삭제하지 않는다. 실제 게시글 블라인드/삭제는 관리자 UI에서 Board 관리자 API를 별도로 호출한다.

---

## 2. 주요 코드 위치

| 영역 | 파일 |
|---|---|
| 일반 신고 API | `domain/report/controller/ReportController.java` |
| 관리자 신고 API | `domain/admin/controller/AdminReportController.java` |
| 관리자 facade | `domain/admin/service/AdminReportFacade.java` |
| 신고 서비스 | `domain/report/service/ReportService.java` |
| 신고 엔티티 | `domain/report/entity/Report.java` |
| 대상 타입 enum | `domain/report/entity/ReportTargetType.java` |
| 처리 상태 enum | `domain/report/entity/ReportStatus.java` |
| 조치 enum | `domain/report/entity/ReportActionType.java` |
| repository adapter | `domain/report/repository/JpaReportAdapter.java` |
| Spring Data JPA | `domain/report/repository/SpringDataJpaReportRepository.java` |
| 사용자 제재 연동 | `domain/user/service/UserSanctionService.java` |
| 프론트 신고 API | `frontend/src/api/reportApi.js` |
| 프론트 관리자 목록 | `frontend/src/components/Admin/sections/ReportManagementSection.js` |
| 프론트 관리자 상세 | `frontend/src/components/Admin/sections/ReportDetailModal.js` |

---

## 3. API

### 3.1 일반 사용자 신고 접수

```http
POST /api/reports
Authorization: Bearer <JWT>
Content-Type: application/json
```

요청 body:

```json
{
  "targetType": "BOARD",
  "targetIdx": 10,
  "reporterId": 3,
  "reason": "부적절한 내용입니다."
}
```

`ReportController`는 `@PreAuthorize("isAuthenticated()")`로 인증을 요구한다.

주의: 현재 구현은 인증된 사용자 ID를 서버에서 주입하지 않고 request body의 `reporterId`를 사용한다. 프론트는 `user.idx`를 넣지만, 서버 레벨에서 JWT 사용자와 `reporterId` 일치 검증은 없다.

### 3.2 관리자 신고 API

```http
GET  /api/admin/reports?targetType=&status=
GET  /api/admin/reports/{id}
POST /api/admin/reports/{id}/handle
```

권한:

- `ADMIN`
- `MASTER`

처리 요청 body:

```json
{
  "status": "RESOLVED",
  "actionTaken": "WARN_USER",
  "adminNote": "반복 신고 확인"
}
```

`AdminReportFacade.handleReport()`는 처리 후 `AdminAuditService`에 `REPORT_HANDLE` 로그를 남긴다.

---

## 4. 데이터 모델

### 4.1 Report

테이블명은 `report`다.

| 필드 | 설명 |
|---|---|
| `idx` | 신고 PK |
| `targetType` | 신고 대상 타입 |
| `targetIdx` | 신고 대상 ID |
| `reporter` | 신고자 |
| `reason` | 신고 사유 |
| `status` | 처리 상태 |
| `handledBy` | 처리 관리자 |
| `handledAt` | 처리 시각 |
| `actionTaken` | 관리자 조치 기록 |
| `adminNote` | 관리자 메모 |
| `createdAt`, `updatedAt` | `BaseTimeEntity`에서 관리 |

중복 방지:

```text
UNIQUE (target_type, target_idx, reporter_idx)
```

엔티티 기본값:

- `status`: `PENDING`
- `actionTaken`: `NONE`

### 4.2 ReportTargetType

```java
BOARD
COMMENT
MISSING_PET
PET_CARE_PROVIDER
CARE_REVIEW
```

### 4.3 ReportStatus

```java
PENDING
RESOLVED
REJECTED
```

ERD 일부 문서에는 `REVIEWING`이 남아 있지만 현재 enum에는 없다.

### 4.4 ReportActionType

```java
NONE
DELETE_CONTENT
SUSPEND_USER
WARN_USER
OTHER
```

`DELETE_CONTENT`는 신고 row의 조치 기록일 뿐, `ReportService.handleReport()`가 콘텐츠 삭제를 자동 수행하지 않는다.

---

## 5. 신고 생성 흐름

`ReportService.createReport()` 흐름:

1. `targetType`, `targetIdx`, `reporterId`, `reason` 검증
2. 신고자 `Users` 조회
3. 신고 대상 존재 검증
4. 동일 사용자·동일 대상 중복 신고 확인
5. `Report` 생성
6. `report` 테이블 저장
7. `ReportDTO` 반환

대상 검증:

| targetType | 검증 방식 |
|---|---|
| `BOARD` | `BoardRepository.existsById(targetIdx)` |
| `COMMENT` | 일반 `Comment` 또는 `MissingPetComment` 존재 확인 |
| `MISSING_PET` | `MissingPetBoardRepository.existsById(targetIdx)` |
| `PET_CARE_PROVIDER` | `Users` 존재 + `Role.SERVICE_PROVIDER` 확인 |
| `CARE_REVIEW` | `CareReviewRepository.existsById(targetIdx)` |

프론트에서 현재 주로 쓰는 신고 진입점:

- 커뮤니티 게시글
- 커뮤니티 댓글
- 실종 제보
- 실종 제보 댓글

백엔드는 `PET_CARE_PROVIDER`, `CARE_REVIEW`도 지원하지만 현재 관리자 UI 탭에는 `CARE_REVIEW`가 노출되지 않고, 일반 프론트 주요 신고 버튼도 위 네 가지 중심이다.

---

## 6. 신고 목록과 상세

### 6.1 목록

`GET /api/admin/reports`는 `targetType`, `status` 필터를 받는다.

Repository query:

```text
WHERE (:targetType IS NULL OR r.targetType = :targetType)
  AND (:status IS NULL OR r.status = :status)
ORDER BY r.createdAt DESC
```

서비스는 조회된 목록 안에서 `targetType + targetIdx`별 신고 수를 계산해 `ReportDTO.reportCount`에 넣고, 최종적으로 다음 순서로 정렬한다.

1. `reportCount DESC`
2. `createdAt DESC`

주의: `reportCount`는 현재 필터로 조회된 목록 안에서 계산한다. 전체 DB 기준 동일 대상 신고 총수와 다를 수 있다.

### 6.2 상세

`GET /api/admin/reports/{id}`는 신고 정보와 대상 미리보기를 함께 반환한다.

미리보기 필드:

- `type`
- `id`
- `title`
- `summary`
- `authorName`

대상이 삭제되었거나 조회되지 않으면 `(삭제됨)`, `(탈퇴/없음)` 같은 placeholder를 반환한다.

---

## 7. 신고 처리와 제재 연동

`ReportService.handleReport()` 흐름:

1. 처리 상태 `status` 필수 검증
2. 신고 row 조회
3. 관리자 `Users` 조회
4. `report.handle(admin, status, actionTaken, adminNote)`
5. `WARN_USER`, `SUSPEND_USER`이면 `UserSanctionService.applySanctionFromReport()` 호출
6. JPA 변경 감지로 신고 처리 정보 저장

`Report.handle()`이 저장하는 값:

- `status`
- `handledBy`
- `handledAt`
- `adminNote`
- `actionTaken` 또는 기본 `NONE`

### 7.1 UserSanction 연동

`UserSanctionService.applySanctionFromReport()` 분기:

| actionTaken | 실제 동작 |
|---|---|
| `WARN_USER` | 경고 추가 |
| `SUSPEND_USER` | 3일 이용제한 추가 |
| `NONE` | 제재 없음 |
| `DELETE_CONTENT` | 제재 없음 |
| `OTHER` | 제재 없음 |

경고는 `Users.warningCount`를 원자적으로 증가시키고, 경고가 3회 이상이면 3일 이용제한을 추가한다.

### 7.2 제재 대상 ID 주의

현재 `handleReport()`는 제재 대상 사용자 ID로 `report.getTargetIdx()`를 그대로 넘긴다.

```java
userSanctionService.applySanctionFromReport(
    report.getTargetIdx(),
    req.getActionTaken(),
    sanctionReason,
    admin.getIdx(),
    reportId
);
```

이 방식은 `PET_CARE_PROVIDER`처럼 `targetIdx`가 사용자 ID인 신고에는 맞다. 하지만 `BOARD`, `COMMENT`, `MISSING_PET`, `CARE_REVIEW`은 `targetIdx`가 콘텐츠 ID라서 실제 작성자 사용자 ID와 다르다.

따라서 콘텐츠 신고에서 `WARN_USER` 또는 `SUSPEND_USER`를 선택하면 잘못된 사용자 ID에 제재를 시도하거나 `UserNotFoundException`이 날 수 있다. 콘텐츠 작성자 제재가 필요하면 target 타입별 작성자 조회가 먼저 필요하다.

---

## 8. 관리자 프론트 동작

`ReportManagementSection`:

- 기본 targetType: `BOARD`
- 기본 status: `PENDING`
- 탭: `BOARD`, `COMMENT`, `MISSING_PET`, `PET_CARE_PROVIDER`
- `CARE_REVIEW` 탭은 현재 없다.

`ReportDetailModal`:

- 신고 상세 조회
- 이미 `PENDING`이 아니면 읽기 전용
- 처리 상태: `RESOLVED`, `REJECTED`
- 조치: `NONE`, `DELETE_CONTENT`, `SUSPEND_USER`, `WARN_USER`, `OTHER`
- `BOARD` 대상인 경우 별도 Board 관리자 API로 블라인드/해제/삭제/복구 버튼 제공

즉, 신고 처리와 실제 콘텐츠 조치는 분리되어 있다.

---

## 9. 통계 연동

Statistics 도메인은 ReportRepository를 사용한다.

| 집계 항목 | 기준 |
|---|---|
| `newReports` | `createdAt` 기간 내 신고 수 |
| `resolvedReports` | `status=RESOLVED`이고 `updatedAt` 기간 내 처리된 신고 수 |

---

## 10. 예외

| 상황 | 예외 |
|---|---|
| 대상 타입 없음 | `ReportValidationException.targetTypeRequired()` |
| 대상 ID 없음 | `ReportValidationException.targetIdxRequired()` |
| 신고자 ID 없음 | `ReportValidationException.reporterRequired()` |
| 사유 없음 | `ReportValidationException.reasonRequired()` |
| 처리 상태 없음 | `ReportValidationException.statusRequired()` |
| 지원하지 않는 대상 | `ReportValidationException.unsupportedTarget()` |
| 이미 신고한 대상 | `ReportConflictException.alreadyReported()` |
| 신고 row 없음 | `ReportNotFoundException` |
| 신고 대상 없음 | `ReportTargetNotFoundException` |
| 제공자 신고 대상이 서비스 제공자가 아님 | `ReportForbiddenException.providerOnly()` |
| 신고자/관리자 사용자 없음 | `UserNotFoundException` |

---

## 11. 현재 한계와 주의사항

- 일반 신고 생성은 인증 사용자와 request `reporterId` 일치 여부를 검증하지 않는다.
- `WARN_USER`, `SUSPEND_USER`는 `targetIdx`를 사용자 ID로 넘기므로 콘텐츠 신고에서는 제재 대상 매핑이 틀릴 수 있다.
- `DELETE_CONTENT`는 기록용 enum이며 ReportService가 실제 삭제를 수행하지 않는다.
- `CARE_REVIEW`는 백엔드 enum/검증은 있지만 관리자 UI 탭에는 없다.
- 관리자 report API는 Page가 아니라 List를 반환한다.
- `reportCount`는 필터링된 목록 안에서 계산되므로 전체 동일 대상 신고 총수와 다를 수 있다.
- 현재 코드에는 `ReportAssistAgentService`나 `/api/admin/reports/{id}/assist` 엔드포인트가 없다. 관련 내용은 오래된 설계 문서에만 남아 있다.
- 신고 중복 방지는 애플리케이션 체크와 DB unique 제약을 함께 사용하지만, 동시 요청에서는 DB unique 충돌이 최종 방어선이다.

---

## 12. DomainV2 페이지에 넣을 포인트

- `ReportTargetType + targetIdx` 폴리모픽 구조로 여러 도메인의 신고를 하나의 테이블에 모았다.
- `(target_type, target_idx, reporter_idx)` unique 제약으로 동일 사용자 중복 신고를 막는다.
- 관리자 목록은 target/status 필터와 신고 수 기반 정렬을 제공한다.
- 상세 조회는 대상 미리보기를 붙여 관리자가 신고 맥락을 바로 확인할 수 있게 한다.
- 신고 처리 기록과 실제 콘텐츠 조치는 분리되어 있다.
- UserSanction 연동은 있지만 현재 제재 대상 ID 매핑은 사용자 대상 신고에만 안전하다.
