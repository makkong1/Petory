# 유저 제한 및 제재 시스템

## 개요

Petory 플랫폼의 유저 제재 시스템은 신고 처리와 연동되어 자동으로 유저에게 경고, 이용제한, 영구 차단을 적용하는 시스템입니다.

## 주요 기능

### 1. 제재 타입

- **WARNING (경고)**: 경고 횟수 증가, 3회 누적 시 자동 이용제한
- **SUSPENSION (이용제한)**: 일시적 이용 제한 (기간 설정 가능)
- **BAN (영구 차단)**: 웹사이트 이용 영구 차단

### 2. 자동화 규칙

- **경고 3회 누적**: 자동으로 이용제한 3일 적용
- **만료 자동 해제**: 매일 자정에 만료된 이용제한 자동 해제
- **신고 처리 연동**: 신고 처리 시 선택한 조치에 따라 자동 제재 적용

## 데이터베이스 설계

### Users 테이블 추가 필드

```sql
ALTER TABLE users 
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN warning_count INT NOT NULL DEFAULT 0,
ADD COLUMN suspended_until DATETIME NULL;
```

**필드 설명:**
- `status`: 유저 상태 (ACTIVE, SUSPENDED, BANNED)
- `warning_count`: 누적 경고 횟수
- `suspended_until`: 이용제한 종료일 (null이면 영구 차단)

### UserSanction 테이블

```sql
CREATE TABLE user_sanctions (
    idx BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_idx BIGINT NOT NULL,
    sanction_type VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    duration_days INT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NULL,
    admin_idx BIGINT NULL,
    report_idx BIGINT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (user_idx) REFERENCES users(idx),
    FOREIGN KEY (admin_idx) REFERENCES users(idx),
    INDEX idx_user_idx (user_idx),
    INDEX idx_ends_at (ends_at)
);
```

**필드 설명:**
- `user_idx`: 제재 대상 유저 ID
- `sanction_type`: 제재 타입 (WARNING, SUSPENSION, BAN)
- `reason`: 제재 사유
- `duration_days`: 제재 기간 (일 단위, null이면 영구)
- `starts_at`: 제재 시작일
- `ends_at`: 제재 종료일 (null이면 영구)
- `admin_idx`: 제재를 처리한 관리자 ID
- `report_idx`: 관련 신고 ID (nullable)

## 시스템 동작 흐름

### 1. 신고 처리 시 제재 적용

```
신고 처리
  ↓
관리자가 조치 선택 (WARN_USER / SUSPEND_USER)
  ↓
ReportService.handleReport()
  ↓
UserSanctionService.applySanctionFromReport()
  ↓
제재 타입에 따라 처리:
  - WARN_USER → addWarning()
  - SUSPEND_USER → addBan()
```

### 2. 경고 누적 시 자동 이용제한

```
경고 추가 (addWarning)
  ↓
warning_count 증가
  ↓
warning_count >= 3?
  ↓ (Yes)
자동 이용제한 3일 적용 (addSuspension)
```

### 3. 로그인 시 제재 체크

```
로그인 요청
  ↓
AuthService.login()
  ↓
유저 상태 확인:
  - BANNED → 로그인 거부
  - SUSPENDED → 종료일 확인
    - 만료됨 → 자동 해제 후 로그인 허용
    - 미만료 → 로그인 거부
```

### 4. 만료된 이용제한 자동 해제

```
스케줄러 실행 (매일 자정)
  ↓
UserSanctionScheduler.releaseExpiredSuspensions()
  ↓
만료된 이용제한 조회
  ↓
활성 제재 확인
  ↓
활성 제재 없음 → 상태를 ACTIVE로 변경
```

## API 사용법

### 1. 경고 추가

```java
userSanctionService.addWarning(
    userId,           // 제재 대상 유저 ID
    reason,          // 제재 사유
    adminId,          // 처리 관리자 ID
    reportId          // 관련 신고 ID (nullable)
);
```

### 2. 이용제한 추가

```java
userSanctionService.addSuspension(
    userId,           // 제재 대상 유저 ID
    reason,          // 제재 사유
    adminId,          // 처리 관리자 ID
    reportId,         // 관련 신고 ID (nullable)
    days              // 제재 기간 (일)
);
```

### 3. 영구 차단

```java
userSanctionService.addBan(
    userId,           // 제재 대상 유저 ID
    reason,          // 제재 사유
    adminId,          // 처리 관리자 ID
    reportId          // 관련 신고 ID (nullable)
);
```

### 4. 제재 해제 (관리자 수동)

```java
userSanctionService.releaseSanction(userId);
```

### 5. 유저 제재 이력 조회

```java
List<UserSanction> sanctions = userSanctionService.getUserSanctions(userId);
```

## 신고 처리 연동

신고 처리 시 `ReportActionType`에 따라 자동으로 제재가 적용됩니다:

- **WARN_USER**: 경고 추가 (3회 누적 시 자동 이용제한)
- **SUSPEND_USER**: 영구 차단
- **NONE, DELETE_CONTENT, OTHER**: 제재 없음

```java
// ReportService.handleReport() 내부에서 자동 호출
if (req.getActionTaken() == ReportActionType.WARN_USER || 
    req.getActionTaken() == ReportActionType.SUSPEND_USER) {
    userSanctionService.applySanctionFromReport(
        report.getTargetIdx(),
        req.getActionTaken(),
        sanctionReason,
        adminIdx,
        reportId
    );
}
```

## 스케줄러 설정

매일 자정에 만료된 이용제한을 자동으로 해제합니다:

```java
@Scheduled(cron = "0 0 0 * * *") // 매일 자정
public void releaseExpiredSuspensions() {
    userSanctionService.releaseExpiredSuspensions();
}
```

## 로그인 차단 메시지

### 영구 차단
```
"영구 차단된 계정입니다. 웹사이트 이용이 불가능합니다."
```

### 이용제한 중
```
"이용제한 중인 계정입니다. 해제일: {종료일시}"
```

## 제재 상태 확인

### Users 엔티티 메서드

```java
// 제재 상태인지 확인
boolean isSanctioned = user.isSanctioned();

// 상태 확인
if (user.getStatus() == Users.UserStatus.BANNED) {
    // 영구 차단
}
if (user.getStatus() == Users.UserStatus.SUSPENDED) {
    // 이용제한 중
}
```

## 관리자 기능

### 제재 이력 조회
- 유저별 제재 이력 조회 가능
- 제재 타입, 사유, 기간, 처리 관리자 정보 포함

### 수동 제재 해제
- 관리자가 수동으로 제재 해제 가능
- `releaseSanction()` 메서드 사용

## 주의사항

1. **경고 누적**: 경고는 자동으로 누적되며, 3회 도달 시 자동 이용제한이 적용됩니다.
2. **영구 차단**: `SUSPEND_USER` 조치는 실제로는 영구 차단(BAN)으로 처리됩니다.
3. **자동 해제**: 이용제한은 기간 만료 시 자동으로 해제되지만, 영구 차단은 수동 해제만 가능합니다.
4. **로그인 차단**: 제재된 유저는 로그인 자체가 차단됩니다.

## 향후 개선 사항

- [ ] 제재 기간 설정 UI 추가
- [ ] 제재 이력 관리 페이지 추가
- [ ] 제재 통계 및 분석 기능
- [ ] 제재 알림 기능 (이메일/알림)
- [ ] 제재 상세 설정 (경고 횟수 임계값, 자동 이용제한 기간 등)

## 관련 파일

- `backend/main/java/com/linkup/Petory/domain/user/entity/UserSanction.java`
- `backend/main/java/com/linkup/Petory/domain/user/entity/Users.java`
- `backend/main/java/com/linkup/Petory/domain/user/service/UserSanctionService.java`
- `backend/main/java/com/linkup/Petory/domain/user/repository/UserSanctionRepository.java`
- `backend/main/java/com/linkup/Petory/domain/user/scheduler/UserSanctionScheduler.java`
- `backend/main/java/com/linkup/Petory/domain/report/service/ReportService.java`
- `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java`

