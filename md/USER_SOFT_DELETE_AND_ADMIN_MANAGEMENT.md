# 사용자 소프트 삭제 및 관리자 패널 개선

## 개요

사용자 관리 시스템을 물리 삭제에서 소프트 삭제로 변경하고, 관리자 패널의 사용자 관리 기능을 개선했습니다.

## 1. 소프트 삭제 구현

### 1.1 문제점

**기존 방식:**
- `usersRepository.deleteById(idx)` - 물리 삭제
- 데이터 복구 불가능
- 관련 데이터 무결성 문제 발생 가능

### 1.2 해결 방법

**소프트 삭제 패턴 적용:**
- 다른 엔티티들(Board, Comment 등)과 동일한 패턴 사용
- `isDeleted` + `deletedAt` 컬럼 추가

### 1.3 데이터베이스 변경

**ALTER 문:**
```sql
-- Users 테이블에 소프트 삭제 컬럼 추가
ALTER TABLE users 
ADD COLUMN is_deleted TINYINT(1) DEFAULT 0 AFTER suspended_until,
ADD COLUMN deleted_at DATETIME NULL AFTER is_deleted;

-- 기존 데이터는 삭제되지 않은 상태로 설정
UPDATE users SET is_deleted = 0 WHERE is_deleted IS NULL;
```

**컬럼 설명:**
- `is_deleted`: 삭제 여부 플래그 (TINYINT(1), 기본값 0)
- `deleted_at`: 삭제 시각 (DATETIME, NULL 가능)

### 1.4 엔티티 변경

**Users.java:**
```java
// 소프트 삭제 관련 필드
@Column(name = "is_deleted")
@Builder.Default
private Boolean isDeleted = false;

@Column(name = "deleted_at")
private LocalDateTime deletedAt;
```

### 1.5 DTO 변경

**UsersDTO.java:**
```java
// 소프트 삭제 관련 필드
private Boolean isDeleted;
private LocalDateTime deletedAt;
```

### 1.6 서비스 로직 변경

**기존 (물리 삭제):**
```java
public void deleteUser(long idx) {
    usersRepository.deleteById(idx);
}
```

**변경 후 (소프트 삭제):**
```java
public void deleteUser(long idx) {
    Users user = usersRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("User not found"));
    user.setIsDeleted(true);
    user.setDeletedAt(LocalDateTime.now());
    usersRepository.save(user);
}
```

### 1.7 계정 복구 기능 추가

**UsersService:**
```java
public UsersDTO restoreUser(long idx) {
    Users user = usersRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("User not found"));
    user.setIsDeleted(false);
    user.setDeletedAt(null);
    Users restored = usersRepository.save(user);
    return usersConverter.toDTO(restored);
}
```

**API 엔드포인트:**
```java
@PostMapping("/{id}/restore")
public ResponseEntity<UsersDTO> restoreUser(@PathVariable Long id) {
    return ResponseEntity.ok(usersService.restoreUser(id));
}
```

## 2. 관리자 패널 사용자 관리 개선

### 2.1 문제점

**기존 방식:**
- 관리자가 사용자의 모든 정보 수정 가능 (id, username, email, password 등)
- 개인정보 보호 문제
- 삭제 시 물리 삭제로 데이터 손실

### 2.2 개선 방향

**관리자 권한 명확화:**
- 기본 정보 수정 제거 (사용자가 직접 수정)
- 상태 관리만 가능 (제재 관련)
- 소프트 삭제로 데이터 보존

### 2.3 상태 관리 기능

**관리 가능한 항목:**
1. **계정 상태 (status)**
   - `ACTIVE`: 정상
   - `SUSPENDED`: 이용제한 중
   - `BANNED`: 영구 차단

2. **경고 횟수 (warningCount)**
   - 사용자 경고 횟수 관리

3. **정지 기간 (suspendedUntil)**
   - SUSPENDED 상태일 때 정지 종료일 설정

4. **역할 승격 (role)**
   - 일반 사용자 → ADMIN 승격만 가능

### 2.4 백엔드 구현

**상태 관리 API:**
```java
@PatchMapping("/{id}/status")
public ResponseEntity<UsersDTO> updateUserStatus(
    @PathVariable Long id, 
    @RequestBody UsersDTO dto
) {
    return ResponseEntity.ok(usersService.updateUserStatus(id, dto));
}
```

**상태 관리 서비스:**
```java
public UsersDTO updateUserStatus(long idx, UsersDTO dto) {
    Users user = usersRepository.findById(idx)
            .orElseThrow(() -> new RuntimeException("User not found"));

    // 상태 업데이트
    if (dto.getStatus() != null) {
        user.setStatus(Enum.valueOf(Users.UserStatus.class, dto.getStatus()));
    }

    // 경고 횟수 업데이트
    if (dto.getWarningCount() != null) {
        user.setWarningCount(dto.getWarningCount());
    }

    // 정지 기간 업데이트
    if (dto.getSuspendedUntil() != null) {
        user.setSuspendedUntil(dto.getSuspendedUntil());
    }

    // 역할 업데이트 (일반 사용자 → ADMIN 승격만)
    if (dto.getRole() != null && !dto.getRole().equals("ADMIN") && !dto.getRole().equals("MASTER")) {
        user.setRole(Enum.valueOf(Role.class, dto.getRole()));
    }

    Users updated = usersRepository.save(user);
    return usersConverter.toDTO(updated);
}
```

### 2.5 프론트엔드 구현

**UserStatusModal 컴포넌트 생성:**
- 기본 정보 수정 제거
- 상태 관리만 가능한 모달

**주요 기능:**
1. 계정 상태 선택 (ACTIVE/SUSPENDED/BANNED)
2. 경고 횟수 입력
3. 정지 기간 설정 (SUSPENDED일 때만)
4. 역할 승격 (일반 → ADMIN)

**UserList 변경:**
- 삭제 버튼 → "계정 삭제" 버튼 (소프트 삭제)
- 삭제된 계정은 "복구" 버튼 표시
- 수정 버튼 → "상태 관리" 버튼

### 2.6 API 엔드포인트 정리

**사용자 관리 API:**
- `GET /api/admin/users/paging` - 사용자 목록 조회 (페이징)
- `GET /api/admin/users/{id}` - 사용자 상세 조회
- `DELETE /api/admin/users/{id}` - 계정 삭제 (소프트 삭제)
- `POST /api/admin/users/{id}/restore` - 계정 복구
- `PATCH /api/admin/users/{id}/status` - 상태 관리

## 3. 개선 효과

### 3.1 데이터 보존
- 삭제된 계정도 데이터 보존
- 복구 가능
- 데이터 무결성 유지

### 3.2 권한 명확화
- 관리자는 상태 관리만 가능
- 사용자 개인정보 보호
- 역할과 책임 명확화

### 3.3 일관성
- 다른 엔티티(Board, Comment 등)와 동일한 소프트 삭제 패턴
- 코드 일관성 유지
- 유지보수 용이

## 4. 사용 예시

### 4.1 계정 삭제
```javascript
// 프론트엔드
await userApi.deleteUser(userId);

// 백엔드 처리
user.setIsDeleted(true);
user.setDeletedAt(LocalDateTime.now());
```

### 4.2 계정 복구
```javascript
// 프론트엔드
await userApi.restoreUser(userId);

// 백엔드 처리
user.setIsDeleted(false);
user.setDeletedAt(null);
```

### 4.3 상태 관리
```javascript
// 프론트엔드
await userApi.updateUserStatus(userId, {
  status: 'SUSPENDED',
  warningCount: 3,
  suspendedUntil: '2024-12-31T23:59:59'
});
```

## 5. 참고 사항

### 5.1 제약사항
- ADMIN/MASTER 계정 삭제는 별도 엔드포인트 사용
- 관리자 역할 변경은 AdminUserManagementController에서만 가능
- 기본 정보(id, username, email 등)는 사용자가 직접 수정

### 5.2 향후 개선 사항
- 삭제된 계정 필터링 기능 추가
- 삭제된 계정 자동 정리 스케줄러 (선택사항)
- 상태 변경 이력 기록 (UserSanction 활용)

## 6. 삭제/밴된 사용자 콘텐츠 필터링

사용자가 삭제되거나 밴되면 작성한 콘텐츠도 자동으로 필터링됩니다.

**자세한 내용은 `DELETED_USER_CONTENT_FILTERING.md` 참고**

