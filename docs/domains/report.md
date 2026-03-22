# Report 도메인 - 포트폴리오 상세 설명

## 1. 개요

Report 도메인은 신고 접수 및 제재 시스템으로, 부적절한 콘텐츠나 사용자를 신고하고 관리자가 처리합니다. 폴리모픽 관계를 통한 다양한 대상 타입 신고 지원, 자동 제재 시스템 연동, 중복 신고 방지 등의 기능을 제공합니다.

**주요 기능**:
- 신고 접수 (게시글, 댓글, 실종제보, 펫케어 제공자 등)
- 신고 처리 (관리자)
- 자동 제재 시스템 연동 (경고, 이용제한)
- 신고 대상 미리보기
- 신고 이력 관리
- 중복 신고 방지

---

## 2. 기능 설명

### 2.1 신고 접수

**신고 접수 프로세스**:
1. 신고 대상 선택 (게시글, 댓글, 실종제보, 펫케어 제공자)
2. 신고 사유 입력
3. 신고 대상 유효성 검증 (`validateTarget()`)
4. 중복 신고 확인 (`existsByTargetTypeAndTargetIdxAndReporterIdx()`)
5. 신고 생성 및 저장

**중복 신고 방지**:
- Unique 제약조건: `(target_type, target_idx, reporter_idx)`로 동일 사용자의 동일 대상 중복 신고 방지
- 중복 신고 시 `ReportConflictException.alreadyReported()` 발생

### 2.2 신고 처리

**신고 처리 프로세스**:
1. 관리자가 신고 검토
2. 처리 상태 설정 (PENDING → RESOLVED/REJECTED)
3. 조치 내용 선택 (NONE, DELETE_CONTENT, WARN_USER, SUSPEND_USER, OTHER)
4. 관리자 메모 작성
5. 자동 제재 적용 (`WARN_USER` 또는 `SUSPEND_USER` 조치 시 `UserSanctionService` 호출)

**자동 제재 시스템**:
- `WARN_USER` 또는 `SUSPEND_USER` 조치 시 자동으로 `UserSanctionService.applySanctionFromReport()` 호출
- 제재 사유 문자열: `신고 #{id} 처리: {관리자 메모 또는 원 신고 사유}`
- **`SUSPEND_USER`**: `UserSanctionService.addBan()`으로 **영구 차단** 처리(코드 주석 기준)
- **대상 ID 전달 방식**: `applySanctionFromReport` 첫 인자로 **`report.getTargetIdx()`**가 그대로 전달됨 → `PET_CARE_PROVIDER`는 `targetIdx`가 사용자 ID와 일치. `BOARD`/`COMMENT`/`MISSING_PET`은 대상이 콘텐츠 ID이므로, 경고·정지 조치 시 **사용자 제재로서는 부적합하거나** `UserNotFoundException` 등으로 실패할 수 있음(§4.6)

### 2.3 신고 대상 미리보기

**미리보기 프로세스**:
1. 신고 상세 조회 시 `buildTargetPreview()` 호출
2. 신고 대상 타입별로 해당 Repository에서 조회
3. 대상 정보 추출 (제목, 내용 요약, 작성자 등)
4. 삭제된 대상의 경우 "(삭제됨)" 표시

---

## 3. 서비스 로직 설명

### 3.1 핵심 비즈니스 로직

#### 로직 1: 신고 접수
**구현 위치**: `ReportService.createReport()`

```java
@Transactional
public ReportDTO createReport(ReportRequestDTO request) {
    // 1. 유효성 검증
    if (request.targetType() == null) {
        throw ReportValidationException.targetTypeRequired();
    }
    if (request.targetIdx() == null) {
        throw ReportValidationException.targetIdxRequired();
    }
    if (request.reporterId() == null) {
        throw ReportValidationException.reporterRequired();
    }
    if (!StringUtils.hasText(request.reason())) {
        throw ReportValidationException.reasonRequired();
    }
    
    // 2. 신고자 확인
    Users reporter = usersRepository.findById(request.reporterId())
            .orElseThrow(UserNotFoundException::new);
    
    // 3. 신고 대상 유효성 검증
    validateTarget(request.targetType(), request.targetIdx());
    
    // 4. 중복 신고 확인
    if (reportRepository.existsByTargetTypeAndTargetIdxAndReporterIdx(
            request.targetType(),
            request.targetIdx(),
            reporter.getIdx())) {
        throw ReportConflictException.alreadyReported();
    }
    
    // 5. 신고 생성
    Report report = Report.builder()
            .targetType(request.targetType())
            .targetIdx(request.targetIdx())
            .reporter(reporter)
            .reason(request.reason().trim())
            .build();
    
    Report saved = reportRepository.save(report);
    return reportConverter.toDTO(saved);
}
```

**핵심 로직**:
- **유효성 검증**: 대상 타입, 대상 ID, 신고자, 신고 사유 확인
- **대상 유효성 검증**: `validateTarget()`로 신고 대상 존재 확인
- **중복 신고 방지**: `existsByTargetTypeAndTargetIdxAndReporterIdx()`로 중복 신고 방지
- **Unique 제약조건**: `(target_type, target_idx, reporter_idx)` Unique 제약조건으로 중복 방지

#### 로직 2: 신고 대상 유효성 검증
**구현 위치**: `ReportService.validateTarget()`

```java
private void validateTarget(ReportTargetType targetType, Long targetIdx) {
    boolean exists;
    switch (targetType) {
        case BOARD: {
            exists = boardRepository.existsById(targetIdx);
            if (!exists) {
                throw ReportTargetNotFoundException.board();
            }
            break;
        }
        case COMMENT: {
            exists = commentRepository.existsById(targetIdx);
            if (!exists) {
                exists = missingPetCommentRepository.existsById(targetIdx);
            }
            if (!exists) {
                throw ReportTargetNotFoundException.comment();
            }
            break;
        }
        case MISSING_PET: {
            exists = missingPetBoardRepository.existsById(targetIdx);
            if (!exists) {
                throw ReportTargetNotFoundException.missingPet();
            }
            break;
        }
        case PET_CARE_PROVIDER: {
            Users provider = usersRepository.findById(targetIdx)
                    .orElseThrow(ReportTargetNotFoundException::provider);
            if (provider.getRole() != Role.SERVICE_PROVIDER) {
                throw ReportForbiddenException.providerOnly();
            }
            break;
        }
        default:
            throw ReportValidationException.unsupportedTarget();
    }
}
```

**핵심 로직**:
- **대상 타입별 검증**: 신고 대상 타입에 따라 해당 Repository에서 존재 확인
- **댓글 검증**: 일반 댓글(`Comment`) 또는 실종제보 댓글(`MissingPetComment`) 모두 확인
- **펫케어 제공자 검증**: `Role.SERVICE_PROVIDER` 역할 확인

#### 로직 3: 신고 처리 및 자동 제재
**구현 위치**: `ReportService.handleReport()`

```java
@Transactional
public ReportDTO handleReport(Long reportId, Long adminUserId, ReportHandleRequest req) {
    if (req.getStatus() == null) {
        throw ReportValidationException.statusRequired();
    }
    
    // 1. 신고 및 관리자 확인
    Report report = reportRepository.findById(reportId)
            .orElseThrow(ReportNotFoundException::new);
    Users admin = usersRepository.findById(adminUserId)
            .orElseThrow(UserNotFoundException::new);
    
    // 2. 신고 처리 정보 설정
    report.setStatus(req.getStatus());
    report.setHandledBy(admin);
    report.setHandledAt(LocalDateTime.now());
    report.setAdminNote(req.getAdminNote());
    report.setActionTaken(req.getActionTaken() != null ? req.getActionTaken() : ReportActionType.NONE);
    
    // 3. 제재 조치가 있으면 자동 적용 (아래 §2.2·§4.6 참고: targetIdx를 사용자 ID로 넘김)
    if (req.getActionTaken() != null &&
            (req.getActionTaken() == ReportActionType.WARN_USER ||
                    req.getActionTaken() == ReportActionType.SUSPEND_USER)) {
        String sanctionReason = String.format("신고 #%d 처리: %s", reportId,
                req.getAdminNote() != null ? req.getAdminNote() : report.getReason());
        userSanctionService.applySanctionFromReport(
                report.getTargetIdx(),
                req.getActionTaken(),
                sanctionReason,
                admin.getIdx(),
                reportId);
    }
    
    // 명시적 save 없음 — 영속 컨텍스트 내 변경 감지로 트랜잭션 커밋 시 반영
    return reportConverter.toDTO(report);
}
```

**핵심 로직**:
- **신고 처리**: 상태, 처리자, 처리 시간, 관리자 메모, 조치 내용 설정(명시 `save` 없음, JPA 변경 감지)
- **자동 제재**: `WARN_USER` 또는 `SUSPEND_USER`일 때 `applySanctionFromReport`(§4.6)
- **제재 사유 문자열**: `신고 #%d 처리: %s`

#### 로직 4: 신고 대상 미리보기
**구현 위치**: `ReportService.buildTargetPreview()`

**핵심 로직**:
- **대상 타입별 조회**: 신고 대상 타입에 따라 해당 Repository에서 조회
- **정보 추출**: 제목, 내용 요약(300자), 작성자 이름 추출
- **삭제된 대상 처리**: 타입별로 `BOARD`/`MISSING_PET`은 `title("(삭제됨)")`, `COMMENT`은 `summary("(삭제됨)")`, `PET_CARE_PROVIDER`는 `title("(탈퇴/없음)")` 등으로 구분
- **내용 요약**: `ellipsis()`로 300자 제한 및 말줄임표 추가

### 3.2 서비스 메서드 구조

#### ReportService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `createReport()` | 신고 접수 | 유효성 검증, 대상 검증, 중복 확인, 신고 생성 |
| `getReports()` | 신고 목록 조회 | findReportsWithFilters, target별 신고 횟수(reportCount) 계산, 신고 횟수 DESC·생성일시 DESC 정렬 |
| `getReportDetail()` | 신고 상세 조회 | ReportNotFoundException, buildTargetPreview (대상 미리보기) |
| `handleReport()` | 신고 처리 | ReportValidationException.statusRequired, ReportNotFoundException, UserNotFoundException, WARN_USER/SUSPEND_USER 시 UserSanctionService.applySanctionFromReport |
| `validateTarget()` | 신고 대상 유효성 검증 | ReportTargetNotFoundException(board/comment/missingPet/provider), ReportForbiddenException.providerOnly, ReportValidationException.unsupportedTarget |
| `buildTargetPreview()` | 신고 대상 미리보기 | 대상 타입별 정보 추출 |
| `ellipsis()` | 텍스트 요약 | 최대 길이 제한 및 말줄임표 추가 |

#### ReportAssistAgentService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `getAssistSuggestions()` | AI 보조 제안 | Ollama 연동, 요약·심각도·조치 제안, 실패 시 Optional.empty() |

### 3.3 트랜잭션 처리
- **클래스 기본**: `@Transactional(readOnly = true)` — `createReport`, `handleReport`만 `@Transactional`
- **신고 접수**: DB 저장·중복 검사가 한 트랜잭션
- **신고 처리**: `handleReport`와 `UserSanctionService.applySanctionFromReport`(기본 전파 `REQUIRED`)가 **동일 트랜잭션**에서 실행
- **조회**: 목록·상세·미리보기는 읽기 전용 트랜잭션
- **격리 수준**: 기본값 (READ_COMMITTED)
- **중복 신고**: 애플리케이션 `exists…` + DB `UniqueConstraint`

---

## 4. 아키텍처 설명

### 4.1 엔티티 구조

#### Report (신고)
```java
@Entity
@Table(name = "report", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"target_type", "target_idx", "reporter_idx"}))
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private ReportTargetType targetType; // 신고 대상 타입
    
    @Column(name = "target_idx", nullable = false)
    private Long targetIdx; // 신고 대상 ID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_idx", nullable = false)
    private Users reporter; // 신고자
    
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason; // 신고 사유
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING; // 상태
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private Users handledBy; // 처리자 (관리자)
    
    @Column(name = "handled_at")
    private LocalDateTime handledAt; // 처리 시간
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", nullable = false, length = 32)
    @Builder.Default
    private ReportActionType actionTaken = ReportActionType.NONE; // 조치 내용
    
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote; // 관리자 메모
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ReportStatus.PENDING;
        }
        if (this.actionTaken == null) {
            this.actionTaken = ReportActionType.NONE;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**특징**:
- `BaseTimeEntity`를 상속하지 않음 (`@PrePersist`, `@PreUpdate`로 직접 관리)
- Unique 제약조건: `(target_type, target_idx, reporter_idx)`로 중복 신고 방지
- 폴리모픽 관계: `targetType`과 `targetIdx`로 다양한 대상 타입 신고 지원

#### ReportTargetType (신고 대상 타입)
```java
public enum ReportTargetType {
    BOARD,              // 커뮤니티 게시글
    COMMENT,            // 댓글 (일반 댓글 또는 실종제보 댓글)
    MISSING_PET,        // 실종 제보
    PET_CARE_PROVIDER   // 펫케어 제공자
}
```

#### ReportStatus (신고 상태)
```java
public enum ReportStatus {
    PENDING,    // 대기 중
    RESOLVED,   // 처리 완료
    REJECTED    // 거부됨
}
```

#### ReportActionType (조치 내용)
```java
public enum ReportActionType {
    NONE,           // 조치 없음
    DELETE_CONTENT, // 콘텐츠 삭제
    SUSPEND_USER,   // 사용자 이용제한 (자동 제재 적용)
    WARN_USER,      // 사용자 경고 (자동 제재 적용)
    OTHER           // 기타
}
```

### 4.2 도메인 구조
```
domain/report/
  ├── controller/
  │   └── ReportController.java          # /api/reports (일반 사용자 신고 생성)
  ├── service/
  │   ├── ReportService.java
  │   └── ReportAssistAgentService.java  # AI 보조 (Ollama, 요약·심각도·조치 제안)
  ├── entity/
  │   ├── Report.java
  │   ├── ReportTargetType.java (enum)
  │   ├── ReportStatus.java (enum)
  │   └── ReportActionType.java (enum)
  ├── repository/
  │   ├── ReportRepository.java
  │   ├── JpaReportAdapter.java
  │   └── SpringDataJpaReportRepository.java
  ├── converter/
  │   └── ReportConverter.java
  ├── dto/
  │   ├── ReportDTO.java
  │   ├── ReportRequestDTO.java
  │   ├── ReportDetailDTO.java
  │   ├── ReportHandleRequest.java
  │   └── ReportAssistSuggestion.java
  └── exception/
      ├── ReportValidationException.java
      ├── ReportConflictException.java
      ├── ReportNotFoundException.java
      ├── ReportTargetNotFoundException.java
      └── ReportForbiddenException.java
```
**참고**: 관리자 API는 `domain/admin/controller/AdminReportController` (`/api/admin/reports`)

### 4.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ Report : "신고자"
    Users ||--o{ Report : "처리자"
    Report -.->|폴리모픽| Board : "게시글"
    Report -.->|폴리모픽| Comment : "댓글"
    Report -.->|폴리모픽| MissingPetBoard : "실종제보"
    Report -.->|폴리모픽| Users : "펫케어 제공자"
```

### 4.4 예외 처리
| 예외 | 발생 시점 |
|------|-----------|
| `ReportValidationException` | targetTypeRequired, targetIdxRequired, reporterRequired, reasonRequired, statusRequired, unsupportedTarget |
| `ReportConflictException` | alreadyReported (중복 신고) |
| `ReportNotFoundException` | 신고 조회 실패 (getReportDetail, handleReport) |
| `ReportTargetNotFoundException` | board, comment, missingPet, provider (대상 없음) |
| `ReportForbiddenException` | providerOnly (SERVICE_PROVIDER 역할 아님) |
| `UserNotFoundException` | 신고자(`createReport`)·관리자(`handleReport`) 조회 실패 |
| `IllegalArgumentException` | `AdminReportController.handleReport`에서 인증 principal 없음 또는 로그인 ID로 사용자 조회 실패 |

### 4.5 API 설계

#### ReportController (`/api/reports`, 일반 사용자)
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/reports` | POST | 신고 접수 (`@PreAuthorize("isAuthenticated()")`, body `ReportRequestDTO`). `ReportValidationException`, `ReportConflictException`, `ReportTargetNotFoundException`, `ReportForbiddenException`, `UserNotFoundException` |

**`reporterId`**: 요청 본문 필드로 받으며, **JWT 로그인 사용자와 일치 여부는 검증하지 않음**(클라이언트는 본인 `reporterId`로 보내야 함).

#### AdminReportController (`/api/admin/reports`, `@PreAuthorize("hasAnyRole('ADMIN','MASTER')")`)
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/admin/reports` | GET | 신고 목록 (`targetType`, `status` 선택 쿼리, 필터 없으면 전체). 동일 `(targetType, targetIdx)` 건수로 `reportCount` 채운 뒤 **건수 DESC, 생성일시 DESC** 정렬 |
| `/api/admin/reports/{id}` | GET | 신고 상세 (`ReportDetailDTO`: `report` + `target` 미리보기) |
| `/api/admin/reports/{id}/assist` | GET | AI 보조 (`ReportAssistAgentService`, Ollama). 성공 시 `ReportAssistSuggestion`, 없거나 실패 시 **응답 본문 `null`** |
| `/api/admin/reports/{id}/handle` | POST | 신고 처리 (body `ReportHandleRequest`). **처리 관리자 ID는 본문이 아니라 `Authentication`의 JWT subject → `usersRepository.findByIdString` → `idx`로 결정**. `ReportValidationException.statusRequired`, `ReportNotFoundException`, `UserNotFoundException`, 관리자 식별 실패 시 `IllegalArgumentException` |

**신고 접수 요청 예시**:
```http
POST /api/reports
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "targetType": "BOARD",
  "targetIdx": 123,
  "reporterId": 1,
  "reason": "부적절한 내용이 포함되어 있습니다."
}
```

**신고 접수 응답 예시** (`ReportDTO`, `reportCount`는 목록 집계 시에만 설정):
```json
{
  "idx": 1,
  "targetType": "BOARD",
  "targetIdx": 123,
  "reporterId": 1,
  "reporterName": "hong",
  "reason": "부적절한 내용이 포함되어 있습니다.",
  "status": "PENDING",
  "actionTaken": "NONE",
  "handledBy": null,
  "handledByName": null,
  "handledAt": null,
  "adminNote": null,
  "createdAt": "2024-01-15T14:00:00",
  "updatedAt": "2024-01-15T14:00:00",
  "reportCount": null
}
```

**신고 목록 조회 요청 예시** (관리자):
```http
GET /api/admin/reports?targetType=BOARD&status=PENDING
Authorization: Bearer <JWT>
```

**신고 상세 조회 응답 예시**:
```json
{
  "report": {
    "idx": 1,
    "targetType": "BOARD",
    "targetIdx": 123,
    "reporterId": 1,
    "reason": "부적절한 내용이 포함되어 있습니다.",
    "status": "PENDING",
    "actionTaken": "NONE",
    "createdAt": "2024-01-15T14:00:00"
  },
  "target": {
    "type": "BOARD",
    "id": 123,
    "title": "게시글 제목",
    "summary": "게시글 내용 요약...",
    "authorName": "홍길동"
  }
}
```

**신고 처리 요청 예시** (관리자, 처리자는 Authorization JWT에서 유도):
```http
POST /api/admin/reports/1/handle
Authorization: Bearer <관리자 JWT>
Content-Type: application/json

{
  "status": "RESOLVED",
  "actionTaken": "WARN_USER",
  "adminNote": "경고 조치를 적용합니다."
}
```

### 4.6 자동 제재와 `targetIdx` (구현 기준)
`ReportService.handleReport`는 `WARN_USER`/`SUSPEND_USER`일 때 `UserSanctionService.applySanctionFromReport(report.getTargetIdx(), …)`를 호출한다. 제재 서비스는 첫 인자를 **사용자 ID**로 `Users`를 조회한다. 따라서 **`PET_CARE_PROVIDER` 신고**는 `targetIdx`가 제공자 사용자 ID와 맞는다. **게시글·댓글·실종 제보 신고**에서 동일 조치를 선택하면 `targetIdx`가 콘텐츠 ID라 제재 의미와 맞지 않거나 조회 실패가 날 수 있다.

---

## 5. 트랜잭션 처리

### 5.1 트랜잭션 전략
- **신고 접수·처리**: `@Transactional` (§3.3)
- **조회**: 클래스 기본 `@Transactional(readOnly = true)`

### 5.2 동시성 제어
- **중복 신고 방지**: Unique 제약조건 `(target_type, target_idx, reporter_idx)`로 DB 레벨에서 중복 방지
- **신고 처리**: 트랜잭션으로 신고 처리와 자동 제재를 원자적으로 처리

---

## 6. 트러블슈팅

---

## 7. 성능 최적화

### 7.1 DB 최적화

#### 인덱스 전략

**report 테이블**:
```sql
-- 처리자별 신고 조회
CREATE INDEX handled_by ON report(handled_by);

-- 신고자별 신고 조회
CREATE INDEX reporter_idx ON report(reporter_idx);

-- 신고 대상별 조회 (Unique 제약조건, 중복 신고 방지)
CREATE UNIQUE INDEX target_type ON report(target_type, target_idx, reporter_idx);
```

**선정 이유**:
- 자주 조회되는 컬럼 (reporter_idx, handled_by)
- WHERE 절에서 자주 사용되는 조건 (target_type, target_idx)
- JOIN에 사용되는 외래키 (reporter_idx, handled_by)
- 중복 신고 방지를 위한 Unique 제약조건 (target_type, target_idx, reporter_idx)

### 7.2 애플리케이션 레벨 최적화

#### 대상 미리보기 최적화
**구현 위치**: `ReportService.buildTargetPreview()`

**최적화 사항**:
- **Lazy Loading**: `FetchType.LAZY`로 필요할 때만 조회
- **내용 요약**: 300자 제한으로 불필요한 데이터 전송 감소
- **삭제된 대상 처리**: 삭제된 대상의 경우 별도 조회 없이 "(삭제됨)" 표시

**효과**: 신고 상세 조회 시 불필요한 데이터 조회 감소, 응답 시간 단축

#### 중복 신고 확인 최적화
**구현 위치**: `ReportRepository.existsByTargetTypeAndTargetIdxAndReporterIdx()`

**최적화 사항**:
- **Exists 쿼리**: `exists()` 메서드로 존재 여부만 확인 (전체 데이터 조회 없음)
- **Unique 제약조건**: DB 레벨에서도 중복 방지

**효과**: 중복 신고 확인 시 빠른 응답 시간

---

## 8. 핵심 포인트 요약

### 8.1 폴리모픽 관계
- **다양한 대상 타입**: `ReportTargetType` enum으로 게시글, 댓글, 실종제보, 펫케어 제공자 신고 지원
- **유연한 확장**: 새로운 대상 타입 추가 시 enum과 `validateTarget()`만 수정하면 됨
- **대상 미리보기**: `buildTargetPreview()`로 신고 대상 타입별 정보 추출

### 8.2 중복 신고 방지
- **Unique 제약조건**: `(target_type, target_idx, reporter_idx)`로 DB 레벨에서 중복 방지
- **애플리케이션 레벨 확인**: `existsByTargetTypeAndTargetIdxAndReporterIdx()`로 중복 확인
- **에러 처리**: 중복 신고 시 `ReportConflictException.alreadyReported()` 발생

### 8.3 자동 제재 시스템
- **제재 연동**: `WARN_USER` → `addWarning`, `SUSPEND_USER` → `addBan`(영구 차단)
- **제재 사유 문자열**: `신고 #{id} 처리: …`
- **동일 트랜잭션**: `handleReport`와 제재 로직(§3.3)
- **`targetIdx` 의미**: §4.6 — `PET_CARE_PROVIDER` 외 타입에서 경고·정지는 콘텐츠 ID를 사용자 ID로 취급하는 한계가 있음

### 8.4 신고 대상 유효성 검증
- **대상 타입별 검증**: 신고 대상 타입에 따라 해당 Repository에서 존재 확인
- **댓글 검증**: 일반 댓글(`Comment`) 또는 실종제보 댓글(`MissingPetComment`) 모두 확인
- **펫케어 제공자 검증**: `Role.SERVICE_PROVIDER` 역할 확인

### 8.5 성능 최적화
- **인덱스**: §7.1 참고 — 엔티티에 추가 `@Index` 선언은 없고, 스키마·운영 DB 기준으로 보완 가능
- **Lazy Loading**: `FetchType.LAZY`로 필요할 때만 조회
- **내용 요약**: 300자 제한으로 불필요한 데이터 전송 감소
- **Exists 쿼리**: 중복 신고 확인 시 존재 여부만 확인

### 8.6 엔티티 설계 특징
- **BaseTimeEntity 미사용**: `@PrePersist`, `@PreUpdate`로 직접 시간 관리
- **Unique 제약조건**: `(target_type, target_idx, reporter_idx)`로 중복 신고 방지
- **폴리모픽 관계**: `targetType`과 `targetIdx`로 다양한 대상 타입 신고 지원
- **상태 관리**: `ReportStatus` enum으로 상태 관리 (PENDING, RESOLVED, REJECTED)
- **조치 내용**: `ReportActionType` enum으로 조치 내용 관리 (NONE, DELETE_CONTENT, WARN_USER, SUSPEND_USER, OTHER)
