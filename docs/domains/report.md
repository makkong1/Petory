# Report 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 신고 접수 및 제재 시스템으로, 부적절한 콘텐츠나 사용자를 신고하고 관리자가 처리합니다.
- **주요 기능**: 
  - 신고 접수 (게시글, 댓글, 사용자 등)
  - 신고 처리 (관리자)
  - 자동 제재 시스템 (경고 누적 → 이용제한)
  - 신고 이력 관리

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 신고 접수 및 처리
- **설명**: 사용자가 부적절한 콘텐츠를 신고하고 관리자가 처리합니다.
- **사용자 시나리오**: 
  1. 신고 접수 (대상 타입, 대상 ID, 신고 사유)
  2. 관리자가 신고 검토
  3. 조치 결정 (경고, 콘텐츠 숨김/삭제, 사용자 정지/차단)
  4. 자동 제재 적용 (경고 3회 → 이용제한)
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 신고 처리 및 자동 제재
```java
// ReportService.java
@Transactional
public void resolveReport(long reportId, long adminId, ReportActionType action, String note) {
    Report report = reportRepository.findById(reportId).orElseThrow();
    
    // 신고 대상에 따라 조치 적용
    if (report.getTargetType() == ReportTargetType.BOARD) {
        Board board = boardRepository.findById(report.getTargetIdx()).orElseThrow();
        switch (action) {
            case CONTENT_HIDE:
                board.setStatus(ContentStatus.HIDDEN);
                break;
            case CONTENT_DELETE:
                board.setStatus(ContentStatus.DELETED);
                break;
        }
        boardRepository.save(board);
    } else if (report.getTargetType() == ReportTargetType.USER) {
        // 자동 제재 적용
        userSanctionService.applySanctionFromReport(
            report.getTargetIdx(), action, note, adminId, reportId);
    }
    
    // 신고 처리 완료
    report.setStatus(ReportStatus.RESOLVED);
    report.setHandledBy(admin);
    report.setActionTaken(action);
    report.setAdminNote(note);
    reportRepository.save(report);
    
    // 신고자에게 알림 발송
    notificationService.notifyReportHandled(report);
}
```

**설명**:
- **처리 흐름**: 신고 조회 → 대상 타입 확인 → 조치 적용 → 신고 처리 완료 → 알림 발송
- **자동 제재**: 사용자 신고 시 자동으로 제재 적용

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
```
domain/report/
  ├── controller/
  │   └── ReportController.java
  ├── service/
  │   └── ReportService.java
  ├── entity/
  │   ├── Report.java
  │   ├── ReportTargetType.java
  │   ├── ReportStatus.java
  │   └── ReportActionType.java
  └── repository/
      └── ReportRepository.java
```

### 3.2 엔티티 구조

#### Report (신고)
```java
@Entity
@Table(name = "report",
       uniqueConstraints = @UniqueConstraint(columnNames = {"target_type", "target_idx", "reporter_idx"}))
public class Report {
    private Long idx;
    private ReportTargetType targetType;   // 신고 대상 타입 (BOARD, COMMENT, USER 등)
    private Long targetIdx;                 // 신고 대상 ID
    private Users reporter;                 // 신고자
    private String reason;                  // 신고 사유
    private ReportStatus status;            // 상태 (PENDING, PROCESSING, RESOLVED, REJECTED)
    private Users handledBy;               // 처리자 (관리자)
    private LocalDateTime handledAt;        // 처리 시간
    private ReportActionType actionTaken;   // 조치 내용 (NONE, WARNING, SUSPENSION, BAN, DELETED)
    private String adminNote;              // 관리자 메모
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 3.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ Report : "신고자"
    Users ||--o{ Report : "처리자"
    Report -.->|폴리모픽| Board
    Report -.->|폴리모픽| Comment
    Report -.->|폴리모픽| Users
```

---

## 4. 트러블슈팅

### 5. DB 최적화

#### 인덱스 전략
```sql
-- 신고 상태별 조회
CREATE INDEX idx_report_status_created 
ON report(status, created_at DESC);

-- 신고 대상별 조회
CREATE INDEX idx_report_target 
ON report(target_type, target_idx);
```

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **폴리모픽 관계**: 다양한 대상 타입 신고 지원
2. **자동 제재 시스템**: 경고 누적 시 자동 이용제한
3. **중복 신고 방지**: Unique 제약조건

### 학습한 점
- 폴리모픽 관계 설계
- 자동 제재 시스템 구현
- 신고 처리 프로세스 설계

### 개선 가능한 부분
- 신고 우선순위: 긴급 신고 우선 처리
- 자동 처리: 명백한 스팸 자동 처리
- AI 기반 분류: 신고 내용 자동 분류
