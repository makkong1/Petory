# Admin Domain Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

▎ "통계 도메인 재설계 구현 플랜 실행해줘. 플랜 파일: docs/superpowers/plans/2026-04-18-statistics-redesign.md, 스펙:  
 ▎ docs/superpowers/specs/2026-04-18-statistics-redesign.md. 서브에이전트 방식으로 Task 1부터 순서대로 진행해줘."

**Goal:** Admin 도메인을 Service Facade 레이어 도입 + P0 버그 수정 + 페이징 전환으로 완전히 재설계한다.

**Architecture:** Controller는 각 AdminXxxFacade를 통해서만 도메인에 접근한다. Facade는 타 도메인 서비스/레포지토리를 조합하고 AdminAuditService를 통해 모든 쓰기 행위를 DB에 기록한다. SystemConfig 엔티티로 시스템 설정을 DB에 영속한다.

**Tech Stack:** Java 17, Spring Boot 3.5.7, Spring Data JPA, Spring Security (`@PreAuthorize`), Lombok, React 19, Axios

---

## 파일 구조 (변경 전·후)

### 신규 생성

```
backend/main/java/com/linkup/Petory/domain/admin/
  entity/
    AdminAuditLog.java          — 관리자 행위 감사 로그 엔티티
    SystemConfig.java           — 시스템 설정 키-값 엔티티
  repository/
    AdminAuditLogRepository.java   — 감사 로그 저장 (JpaRepository 직접)
    SystemConfigRepository.java    — 설정 저장 (JpaRepository 직접)
  service/
    AdminAuditService.java      — 감사 로그 기록 유틸
    AdminUserFacade.java        — 사용자 관리 Facade
    AdminCareAndMeetupFacade.java  — 케어·모임 관리 Facade (restore 포함)
    AdminSystemFacade.java      — 시스템 설정 Facade
```

### 수정

```
domain/meetup/service/MeetupService.java           — deleteMeetupForAdmin() 추가
domain/care/service/CareRequestService.java        — restoreForAdmin() 추가
domain/user/repository/UsersRepository.java        — findAllWithFilter() 추가
domain/user/repository/SpringDataJpaUsersRepository.java  — JPQL 추가
domain/user/repository/JpaUsersAdapter.java        — 어댑터 구현 추가
domain/care/repository/CareRequestRepository.java  — findAllForAdmin() 추가
domain/care/repository/SpringDataJpaCareRequestRepository.java  — JPQL 추가
domain/care/repository/JpaCareRequestAdapter.java  — 어댑터 구현 추가
domain/file/repository/AttachmentFileRepository.java     — findAllPaged() 추가
domain/file/repository/SpringDataJpaAttachmentFileRepository.java — 추가
domain/file/repository/JpaAttachmentFileAdapter.java     — 어댑터 구현 추가
domain/admin/controller/AdminMeetupController.java  — Facade 연결
domain/admin/controller/AdminCareRequestController.java — Facade 연결
domain/admin/controller/AdminFileController.java   — Facade 연결, repo 직접 접근 제거
domain/admin/controller/AdminUserController.java   — Facade 연결, 필터 추가
domain/admin/controller/AdminUserManagementController.java — Service 도입, no-op 제거
domain/admin/controller/AdminSystemController.java — Facade 연결
frontend/src/api/meetupAdminApi.js                 — 페이징 파라미터 추가
frontend/src/components/Admin/sections/MeetupManagementSection.js — 페이징 UI
```

---

## Task 1: P0 버그 — MeetupService.deleteMeetup 관리자 경로 수정

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMeetupController.java`

- [ ] **Step 1: `MeetupService`에 `deleteMeetupForAdmin()` 추가**

`MeetupService.java`의 기존 `deleteMeetup()` 메서드 아래에 추가:

```java
@Transactional
public void deleteMeetupForAdmin(Long meetupIdx) {
    Meetup meetup = meetupRepository.findById(meetupIdx)
            .orElseThrow(MeetupNotFoundException::new);
    meetup.setIsDeleted(true);
    meetup.setDeletedAt(java.time.LocalDateTime.now());
    meetupRepository.save(meetup);
    log.info("관리자 소프트 삭제: meetupIdx={}", meetupIdx);
}
```

- [ ] **Step 2: `AdminMeetupController.deleteMeetup()` 수정**

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteMeetup(@PathVariable Long id) {
    meetupService.deleteMeetupForAdmin(id);
    return ResponseEntity.noContent().build();
}
```

기존에 주입된 `MeetupService meetupService` 필드를 그대로 활용 — 추가 주입 불필요.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java \
        backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMeetupController.java
git commit -m "fix(admin): 관리자 모임 삭제 시 UserNotFoundException 버그 수정"
```

---

## Task 2: P0 버그 — CareRequestService.restoreForAdmin() 구현

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java`

- [ ] **Step 1: `CareRequestService`에 `restoreForAdmin()` 추가**

`CareRequestService.java` 클래스 내부 마지막에 추가:

```java
@Transactional
public CareRequestDTO restoreForAdmin(Long id) {
    CareRequest request = careRequestRepository.findById(id)
            .orElseThrow(CareRequestNotFoundException::new);
    request.setIsDeleted(false);
    request.setDeletedAt(null);
    return careRequestConverter.toDTO(careRequestRepository.save(request));
}
```

- [ ] **Step 2: `AdminCareRequestController.restoreCareRequest()` 구현**

```java
@PostMapping("/{id}/restore")
public ResponseEntity<CareRequestDTO> restoreCareRequest(@PathVariable Long id) {
    return ResponseEntity.ok(careRequestService.restoreForAdmin(id));
}
```

기존 `throws new UnsupportedOperationException(...)` 구문 전체를 위 코드로 교체.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java \
        backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java
git commit -m "fix(admin): 케어 요청 복구(restore) 미구현 500 에러 수정"
```

---

## Task 3: AdminAuditLog 엔티티 + Repository

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/entity/AdminAuditLog.java`
- Create: `backend/main/java/com/linkup/Petory/domain/admin/repository/AdminAuditLogRepository.java`

- [ ] **Step 1: `AdminAuditLog` 엔티티 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/entity/AdminAuditLog.java
package com.linkup.Petory.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_log",
    indexes = @Index(name = "idx_audit_admin_created", columnList = "admin_idx, created_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "admin_idx", nullable = false)
    private Long adminIdx;

    @Column(name = "action", nullable = false, length = 50)
    private String action;       // "USER_SUSPEND", "BOARD_BLIND", "MEETUP_DELETE" 등

    @Column(name = "target_type", length = 30)
    private String targetType;   // "USER", "BOARD", "MEETUP", "CARE_REQUEST" 등

    @Column(name = "target_idx")
    private Long targetIdx;

    @Column(name = "detail", length = 500)
    private String detail;       // 사유 또는 변경 내용 요약

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: `AdminAuditLogRepository` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/repository/AdminAuditLogRepository.java
package com.linkup.Petory.domain.admin.repository;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
```

> 참고: `UserSanctionRepository`와 동일하게 `JpaRepository`를 직접 상속한다. Admin 전용 인프라는 도메인 Repository 인터페이스 패턴 적용 불필요 — admin 패키지 외부에서 사용하지 않음.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/entity/AdminAuditLog.java \
        backend/main/java/com/linkup/Petory/domain/admin/repository/AdminAuditLogRepository.java
git commit -m "feat(admin): AdminAuditLog 엔티티 및 Repository 추가"
```

---

## Task 4: SystemConfig 엔티티 + Repository

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/entity/SystemConfig.java`
- Create: `backend/main/java/com/linkup/Petory/domain/admin/repository/SystemConfigRepository.java`

- [ ] **Step 1: `SystemConfig` 엔티티 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/entity/SystemConfig.java
package com.linkup.Petory.domain.admin.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_config",
    indexes = @Index(name = "idx_system_config_key", columnList = "config_key", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Column(name = "description", length = 200)
    private String description;
}
```

- [ ] **Step 2: `SystemConfigRepository` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/repository/SystemConfigRepository.java
package com.linkup.Petory.domain.admin.repository;

import com.linkup.Petory.domain.admin.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/entity/SystemConfig.java \
        backend/main/java/com/linkup/Petory/domain/admin/repository/SystemConfigRepository.java
git commit -m "feat(admin): SystemConfig 엔티티 및 Repository 추가 (설정값 DB 영속)"
```

---

## Task 5: AdminAuditService 생성

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/service/AdminAuditService.java`

- [ ] **Step 1: `AdminAuditService` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/service/AdminAuditService.java
package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;
import com.linkup.Petory.domain.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long adminIdx, String action, String targetType, Long targetIdx, String detail) {
        try {
            auditLogRepository.save(AdminAuditLog.builder()
                    .adminIdx(adminIdx)
                    .action(action)
                    .targetType(targetType)
                    .targetIdx(targetIdx)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("감사 로그 저장 실패: adminIdx={}, action={}, error={}", adminIdx, action, e.getMessage());
        }
    }
}
```

> `@Async` + `REQUIRES_NEW`: 감사 로그 저장 실패가 본 트랜잭션 롤백으로 이어지지 않게 분리.
> `@Async` 동작을 위해 `PetoryApplication.java`에 `@EnableAsync`가 있는지 확인 필요 (Task 5 Step 2).

- [ ] **Step 2: `PetoryApplication`에 `@EnableAsync` 추가 확인 및 추가**

```bash
grep -n "EnableAsync" backend/main/java/com/linkup/Petory/PetoryApplication.java
```

없으면 `PetoryApplication.java`에 추가:

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync   // 추가
public class PetoryApplication { ... }
```

import 추가:

```java
import org.springframework.scheduling.annotation.EnableAsync;
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/service/AdminAuditService.java \
        backend/main/java/com/linkup/Petory/PetoryApplication.java
git commit -m "feat(admin): AdminAuditService 추가 (비동기 감사 로그)"
```

---

## Task 6: UsersRepository — 관리자용 필터 쿼리 추가

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/JpaUsersAdapter.java`

- [ ] **Step 1: `UsersRepository` 인터페이스에 메서드 추가**

`UsersRepository.java` 마지막 메서드 아래에 추가:

```java
/**
 * 관리자용 사용자 목록 페이징 (role / status / 키워드 복합 필터)
 * role null → 전체, status null → 전체, keyword null → 전체
 */
Page<Users> findAllForAdmin(String role, String status, String keyword, Pageable pageable);
```

- [ ] **Step 2: `SpringDataJpaUsersRepository`에 JPQL 추가**

`SpringDataJpaUsersRepository.java` 마지막에 추가:

```java
@RepositoryMethod("사용자: 관리자 목록 (role/status/keyword 복합 필터)")
@Query("SELECT u FROM Users u WHERE " +
       "(:role IS NULL OR u.role = com.linkup.Petory.domain.user.entity.Role.valueOf(:role)) AND " +
       "(:status IS NULL OR u.status = com.linkup.Petory.domain.user.entity.UserStatus.valueOf(:status)) AND " +
       "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword%) " +
       "ORDER BY u.createdAt DESC")
Page<Users> findAllForAdmin(
        @Param("role") String role,
        @Param("status") String status,
        @Param("keyword") String keyword,
        Pageable pageable);
```

- [ ] **Step 3: `JpaUsersAdapter`에 구현 추가**

`JpaUsersAdapter.java` 마지막 `@Override` 메서드 아래에 추가:

```java
@Override
public Page<Users> findAllForAdmin(String role, String status, String keyword, Pageable pageable) {
    return jpaRepository.findAllForAdmin(role, status, keyword, pageable);
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/user/repository/
git commit -m "feat(admin): UsersRepository 관리자 필터 페이징 쿼리 추가"
```

---

## Task 7: CareRequestRepository — 관리자용 쿼리 추가

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/care/repository/CareRequestRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/care/repository/JpaCareRequestAdapter.java`

- [ ] **Step 1: `CareRequestRepository` 인터페이스에 메서드 추가**

`CareRequestRepository.java` 마지막에 추가:

```java
/**
 * 관리자용 케어 요청 페이징 (status / deleted / keyword 복합 필터)
 * deleted null → 전체(삭제 포함), false → 미삭제만, true → 삭제된 것만
 */
Page<CareRequest> findAllForAdmin(String status, Boolean deleted, String keyword, Pageable pageable);
```

- [ ] **Step 2: `SpringDataJpaCareRequestRepository`에 JPQL 추가**

`SpringDataJpaCareRequestRepository.java` 마지막에 추가:

```java
@Query("SELECT r FROM CareRequest r WHERE " +
       "(:status IS NULL OR r.status = com.linkup.Petory.domain.care.entity.CareRequestStatus.valueOf(:status)) AND " +
       "(:deleted IS NULL OR r.isDeleted = :deleted) AND " +
       "(:keyword IS NULL OR r.title LIKE %:keyword% OR r.description LIKE %:keyword%) " +
       "ORDER BY r.createdAt DESC")
Page<CareRequest> findAllForAdmin(
        @Param("status") String status,
        @Param("deleted") Boolean deleted,
        @Param("keyword") String keyword,
        Pageable pageable);
```

- [ ] **Step 3: `JpaCareRequestAdapter`에 구현 추가**

```java
@Override
public Page<CareRequest> findAllForAdmin(String status, Boolean deleted, String keyword, Pageable pageable) {
    return jpaRepository.findAllForAdmin(status, deleted, keyword, pageable);
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/care/repository/
git commit -m "feat(admin): CareRequestRepository 관리자 필터 페이징 쿼리 추가 (deleted 포함)"
```

---

## Task 8: AttachmentFileRepository — 관리자용 페이징 추가

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/file/repository/AttachmentFileRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/file/repository/SpringDataJpaAttachmentFileRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/file/repository/JpaAttachmentFileAdapter.java`

- [ ] **Step 1: `AttachmentFileRepository`에 페이징 메서드 추가**

`AttachmentFileRepository.java` 마지막에 추가:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 관리자용 파일 목록 페이징 (targetType / keyword 복합 필터)
 */
Page<AttachmentFile> findAllForAdmin(String targetType, String keyword, Pageable pageable);
```

- [ ] **Step 2: `SpringDataJpaAttachmentFileRepository`에 JPQL 추가**

```java
@Query("SELECT f FROM AttachmentFile f WHERE " +
       "(:targetType IS NULL OR f.targetType = com.linkup.Petory.domain.file.entity.FileTargetType.valueOf(:targetType)) AND " +
       "(:keyword IS NULL OR f.filePath LIKE %:keyword% OR f.fileType LIKE %:keyword%) " +
       "ORDER BY f.createdAt DESC")
Page<AttachmentFile> findAllForAdmin(
        @Param("targetType") String targetType,
        @Param("keyword") String keyword,
        Pageable pageable);
```

- [ ] **Step 3: `JpaAttachmentFileAdapter`에 구현 추가**

```java
@Override
public Page<AttachmentFile> findAllForAdmin(String targetType, String keyword, Pageable pageable) {
    return jpaRepository.findAllForAdmin(targetType, keyword, pageable);
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/file/repository/
git commit -m "feat(admin): AttachmentFileRepository 관리자 필터 페이징 쿼리 추가"
```

---

## Task 9: AdminUserFacade 생성

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/service/AdminUserFacade.java`

- [ ] **Step 1: `AdminUserFacade` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/service/AdminUserFacade.java
package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserFacade {

    private final UsersRepository usersRepository;
    private final UsersConverter usersConverter;
    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    // ── 사용자 목록 (필터 + 페이징) ──────────────────────────────────────

    public UserPageResponseDTO getUsers(String role, String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Users> userPage = usersRepository.findAllForAdmin(role, status, keyword, pageable);

        return UserPageResponseDTO.builder()
                .users(usersConverter.toDTOList(userPage.getContent()))
                .totalCount(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    // ── 단건 조회 ────────────────────────────────────────────────────────

    public UsersDTO getUser(Long id) {
        return usersService.getUser(id);
    }

    // ── 상태 변경 (정지·차단·활성화) ─────────────────────────────────────

    @Transactional
    public UsersDTO updateStatus(Long targetId, UsersDTO dto, Long adminIdx) {
        UsersDTO result = usersService.updateUserStatus(targetId, dto);
        auditService.log(adminIdx, "USER_STATUS_UPDATE", "USER", targetId,
                "status=" + dto.getStatus());
        return result;
    }

    // ── 소프트 삭제 / 복구 ────────────────────────────────────────────────

    @Transactional
    public void deleteUser(Long targetId, Long adminIdx) {
        Role role = usersRepository.findRoleByIdx(targetId)
                .orElseThrow(UserNotFoundException::new);
        if (role == Role.ADMIN || role == Role.MASTER) {
            throw new IllegalArgumentException("관리자 계정 삭제는 별도 엔드포인트를 사용해주세요.");
        }
        usersService.deleteUser(targetId);
        auditService.log(adminIdx, "USER_DELETE", "USER", targetId, null);
    }

    @Transactional
    public UsersDTO restoreUser(Long targetId, Long adminIdx) {
        UsersDTO result = usersService.restoreUser(targetId);
        auditService.log(adminIdx, "USER_RESTORE", "USER", targetId, null);
        return result;
    }

    // ── MASTER 전용: 관리자 계정 관리 ────────────────────────────────────

    public List<UsersDTO> getAdminUsers() {
        return usersConverter.toDTOList(
                usersRepository.findAllForAdmin("ADMIN", null, null, Pageable.unpaged()).getContent()
        );
    }

    @Transactional
    public UsersDTO createAdminUser(UsersDTO dto, Long masterIdx) {
        if (!"ADMIN".equals(dto.getRole())) {
            throw new IllegalArgumentException("ADMIN 역할만 지정할 수 있습니다.");
        }
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        usersRepository.findByIdString(dto.getId()).ifPresent(u -> {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        });
        usersRepository.findByUsername(dto.getUsername()).ifPresent(u -> {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다.");
        });

        Users user = usersConverter.toEntity(dto);
        user.setRole(Role.ADMIN);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        Users saved = usersRepository.save(user);

        log.info("MASTER({}) ADMIN 계정 생성: username={}", masterIdx, saved.getUsername());
        auditService.log(masterIdx, "ADMIN_CREATE", "USER", saved.getIdx(), "username=" + saved.getUsername());
        return usersConverter.toDTO(saved);
    }

    @Transactional
    public UsersDTO promoteToAdmin(Long targetId, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.MASTER) {
            throw new IllegalArgumentException("MASTER 권한은 변경할 수 없습니다.");
        }
        user.setRole(Role.ADMIN);
        Users updated = usersRepository.save(user);
        auditService.log(masterIdx, "USER_PROMOTE_ADMIN", "USER", targetId, null);
        return usersConverter.toDTO(updated);
    }

    @Transactional
    public void deleteAdminUser(Long targetId, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN 계정만 이 엔드포인트로 삭제할 수 있습니다.");
        }
        usersRepository.deleteById(targetId);
        log.warn("MASTER({}) ADMIN 계정 삭제: userId={}", masterIdx, targetId);
        auditService.log(masterIdx, "ADMIN_DELETE", "USER", targetId, "username=" + user.getUsername());
    }

    @Transactional
    public void changeAdminPassword(Long targetId, String newPassword, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN 계정만 비밀번호를 변경할 수 있습니다.");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
        auditService.log(masterIdx, "ADMIN_PASSWORD_CHANGE", "USER", targetId, null);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/service/AdminUserFacade.java
git commit -m "feat(admin): AdminUserFacade 추가 (사용자 관리 통합, 감사 로그 포함)"
```

---

## Task 10: AdminCareAndMeetupFacade 생성

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java`

- [ ] **Step 1: `AdminCareAndMeetupFacade` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java
package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.care.service.CareRequestService;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.service.MeetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCareAndMeetupFacade {

    private final CareRequestRepository careRequestRepository;
    private final CareRequestConverter careRequestConverter;
    private final CareRequestService careRequestService;
    private final MeetupService meetupService;
    private final AdminAuditService auditService;

    // ── 케어 요청 ────────────────────────────────────────────────────────

    public Page<CareRequestDTO> getCareRequests(String status, Boolean deleted, String keyword,
                                                 int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return careRequestRepository.findAllForAdmin(status, deleted, keyword, pageable)
                .map(careRequestConverter::toDTO);
    }

    public CareRequestDTO getCareRequest(Long id) {
        return careRequestService.getCareRequest(id);
    }

    @Transactional
    public CareRequestDTO updateCareStatus(Long id, String status, Long adminIdx) {
        CareRequestDTO result = careRequestService.updateStatus(id, status, adminIdx);
        auditService.log(adminIdx, "CARE_STATUS_UPDATE", "CARE_REQUEST", id, "status=" + status);
        return result;
    }

    @Transactional
    public void deleteCareRequest(Long id, Long adminIdx) {
        careRequestService.deleteCareRequest(id, adminIdx);
        auditService.log(adminIdx, "CARE_DELETE", "CARE_REQUEST", id, null);
    }

    @Transactional
    public CareRequestDTO restoreCareRequest(Long id, Long adminIdx) {
        CareRequestDTO result = careRequestService.restoreForAdmin(id);
        auditService.log(adminIdx, "CARE_RESTORE", "CARE_REQUEST", id, null);
        return result;
    }

    // ── 모임 ─────────────────────────────────────────────────────────────

    public Page<MeetupDTO> getMeetups(String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MeetupDTO> meetupPage = meetupService.getAllMeetups(pageable);

        if ((status != null && !"ALL".equals(status)) || (keyword != null && !keyword.isBlank())) {
            // DB 레벨 필터가 없으므로 페이지 결과 내 추가 필터 (페이지 크기를 크게 요청 시 영향 최소화)
            // TODO: MeetupRepository에 관리자 필터 쿼리 추가 시 교체
            List<MeetupDTO> filtered = meetupPage.getContent().stream()
                    .filter(m -> {
                        boolean statusMatch = status == null || "ALL".equals(status) ||
                                (m.getStatus() != null && m.getStatus().equalsIgnoreCase(status));
                        boolean keywordMatch = keyword == null || keyword.isBlank() ||
                                (m.getTitle() != null && m.getTitle().toLowerCase().contains(keyword.toLowerCase())) ||
                                (m.getDescription() != null && m.getDescription().toLowerCase().contains(keyword.toLowerCase()));
                        return statusMatch && keywordMatch;
                    })
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, meetupPage.getTotalElements());
        }
        return meetupPage;
    }

    public MeetupDTO getMeetup(Long id) {
        return meetupService.getMeetupById(id);
    }

    @Transactional
    public void deleteMeetup(Long id, Long adminIdx) {
        meetupService.deleteMeetupForAdmin(id);
        auditService.log(adminIdx, "MEETUP_DELETE", "MEETUP", id, null);
    }

    public List<MeetupParticipantsDTO> getMeetupParticipants(Long id) {
        return meetupService.getMeetupParticipants(id);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java
git commit -m "feat(admin): AdminCareAndMeetupFacade 추가 (페이징, restore 완성, 감사 로그)"
```

---

## Task 11: AdminSystemFacade 생성

**Files:**

- Create: `backend/main/java/com/linkup/Petory/domain/admin/service/AdminSystemFacade.java`

- [ ] **Step 1: `AdminSystemFacade` 생성**

```java
// backend/main/java/com/linkup/Petory/domain/admin/service/AdminSystemFacade.java
package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.admin.entity.SystemConfig;
import com.linkup.Petory.domain.admin.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSystemFacade {

    private final SystemConfigRepository configRepository;
    private final AdminAuditService auditService;

    public Map<String, String> getAllConfigs() {
        return configRepository.findAll().stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
    }

    public String getConfig(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void upsertConfig(String key, String value, String description, Long adminIdx) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder()
                        .configKey(key)
                        .description(description)
                        .build());
        config.setConfigValue(value);
        configRepository.save(config);
        log.info("시스템 설정 변경: key={}, value={}, adminIdx={}", key, value, adminIdx);
        auditService.log(adminIdx, "SYSTEM_CONFIG_UPDATE", "SYSTEM", null, key + "=" + value);
    }

    @Transactional
    public void upsertConfigs(Map<String, String> settings, Long adminIdx) {
        settings.forEach((key, value) -> upsertConfig(key, value, null, adminIdx));
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/service/AdminSystemFacade.java
git commit -m "feat(admin): AdminSystemFacade 추가 (시스템 설정 DB 영속)"
```

---

## Task 12: AdminUserController 리팩토링

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminUserController.java`

- [ ] **Step 1: `AdminUserController` 전체 교체**

`AdminUserController.java`를 아래로 교체:

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminUserFacade;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class AdminUserController {

    private final AdminUserFacade adminUserFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping("/paging")
    public ResponseEntity<UserPageResponseDTO> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserFacade.getUsers(role, status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsersDTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserFacade.getUser(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsersDTO> updateStatus(@PathVariable Long id, @RequestBody UsersDTO dto) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.updateStatus(id, dto, adminIdx));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.deleteUser(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<UsersDTO> restoreUser(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.restoreUser(id, adminIdx));
    }
}
```

> `PUT /{id}`, `POST` (생성) 엔드포인트 제거: 일반 사용자 생성은 회원가입 API, 수정은 `/api/users/{id}` 사용. Admin 패널에서 직접 사용자를 생성·수정하는 기능은 PRD 범위 외.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminUserController.java
git commit -m "refactor(admin): AdminUserController Facade 연결, role/status/q 필터 추가"
```

---

## Task 13: AdminUserManagementController 리팩토링

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminUserManagementController.java`

- [ ] **Step 1: `AdminUserManagementController` 전체 교체**

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminUserFacade;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master/admin-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminUserManagementController {

    private final AdminUserFacade adminUserFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<List<UsersDTO>> getAdminUsers() {
        return ResponseEntity.ok(adminUserFacade.getAdminUsers());
    }

    @PostMapping
    public ResponseEntity<UsersDTO> createAdminUser(@RequestBody UsersDTO dto) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.createAdminUser(dto, masterIdx));
    }

    @PatchMapping("/{id}/promote-to-admin")
    public ResponseEntity<UsersDTO> promoteToAdmin(@PathVariable Long id) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(adminUserFacade.promoteToAdmin(id, masterIdx));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdminUser(@PathVariable Long id) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.deleteAdminUser(id, masterIdx);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changeAdminPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        adminUserFacade.changeAdminPassword(id, body.get("newPassword"), masterIdx);
        return ResponseEntity.noContent().build();
    }
}
```

> `changeAdminRole` no-op 엔드포인트 제거.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminUserManagementController.java
git commit -m "refactor(admin): AdminUserManagementController Facade 연결, no-op changeAdminRole 제거"
```

---

## Task 14: AdminCareRequestController + AdminMeetupController 리팩토링

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMeetupController.java`

- [ ] **Step 1: `AdminCareRequestController` 전체 교체**

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminCareAndMeetupFacade;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/care-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminCareRequestController {

    private final AdminCareAndMeetupFacade facade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<CareRequestDTO>> listCareRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(facade.getCareRequests(status, deleted, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareRequestDTO> getCareRequest(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getCareRequest(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CareRequestDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.updateCareStatus(id, status, adminIdx));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteCareRequest(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        facade.deleteCareRequest(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<CareRequestDTO> restoreCareRequest(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        return ResponseEntity.ok(facade.restoreCareRequest(id, adminIdx));
    }
}
```

- [ ] **Step 2: `AdminMeetupController` 전체 교체**

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminCareAndMeetupFacade;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/meetups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminMeetupController {

    private final AdminCareAndMeetupFacade facade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<MeetupDTO>> listMeetups(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(facade.getMeetups(status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetupDTO> getMeetup(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getMeetup(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeetup(@PathVariable Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        facade.deleteMeetup(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<MeetupParticipantsDTO>> getParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getMeetupParticipants(id));
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java \
        backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMeetupController.java
git commit -m "refactor(admin): CareRequest/Meetup 컨트롤러 Facade 연결, 페이징 전환"
```

---

## Task 15: AdminFileController 리팩토링

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminFileController.java`

- [ ] **Step 1: `AdminFileController` 전체 교체**

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminFileController {

    private final AttachmentFileRepository fileRepository;
    private final AttachmentFileService attachmentFileService;

    @GetMapping
    public ResponseEntity<Page<FileDTO>> listFiles(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                fileRepository.findAllForAdmin(targetType, q, PageRequest.of(page, size))
                        .map(f -> FileDTO.builder()
                                .idx(f.getIdx())
                                .targetType(f.getTargetType())
                                .targetIdx(f.getTargetIdx())
                                .filePath(f.getFilePath())
                                .fileType(f.getFileType())
                                .createdAt(f.getCreatedAt())
                                .downloadUrl(attachmentFileService.buildDownloadUrl(f.getFilePath()))
                                .build())
        );
    }

    @GetMapping("/target")
    public ResponseEntity<List<FileDTO>> getFilesByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetIdx) {
        try {
            FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
            return ResponseEntity.ok(attachmentFileService.getAttachments(type, targetIdx));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/target")
    public ResponseEntity<Void> deleteFilesByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetIdx) {
        try {
            FileTargetType type = FileTargetType.valueOf(targetType.toUpperCase());
            attachmentFileService.deleteAll(type, targetIdx);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
```

> `getFileStatistics()` 제거: `findAll()` 풀스캔 기반. 통계는 `AdminStatisticsController`에서 `DailyStatistics`로 제공.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminFileController.java
git commit -m "refactor(admin): AdminFileController findAll() 풀스캔 제거, 페이징 전환"
```

---

## Task 16: AdminSystemController 리팩토링

**Files:**

- Modify: `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminSystemController.java`

- [ ] **Step 1: `AdminSystemController` 전체 교체**

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.admin.service.AdminSystemFacade;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminSystemController {

    private final AdminSystemFacade systemFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings() {
        return ResponseEntity.ok(systemFacade.getAllConfigs());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, String> settings) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        systemFacade.upsertConfigs(settings, masterIdx);
        return ResponseEntity.ok(Map.of("message", "시스템 설정이 업데이트되었습니다.", "count", settings.size()));
    }

    @GetMapping("/settings/{key}")
    public ResponseEntity<Map<String, String>> getSetting(@PathVariable String key) {
        String value = systemFacade.getConfig(key, null);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<Void> upsertSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        Long masterIdx = userIdResolver.requireCurrentUserIdx();
        systemFacade.upsertConfig(key, body.get("value"), body.get("description"), masterIdx);
        return ResponseEntity.noContent().build();
    }
}
```

> 하드코딩된 스케줄러 상태 엔드포인트(`/scheduler/status`, `/scheduler/{name}/toggle`) 제거: 실제 구현 없이 로그만 찍던 엔드포인트. 필요 시 향후 별도 태스크로 TaskScheduler 기반 구현.

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminSystemController.java
git commit -m "refactor(admin): AdminSystemController 하드코딩 제거, DB 설정 저장 연결"
```

---

## Task 17: 프론트엔드 — meetupAdminApi 페이징 추가

**Files:**

- Modify: `frontend/src/api/meetupAdminApi.js`
- Modify: `frontend/src/components/Admin/sections/MeetupManagementSection.js`

- [ ] **Step 1: `meetupAdminApi.js` 수정**

```js
// frontend/src/api/meetupAdminApi.js
import { createAuthAxios } from "./apiClient";

const api = createAuthAxios("http://localhost:8080/api/admin/meetups");

export const meetupAdminApi = {
  listMeetups: (params) => api.get("", { params }),
  getMeetup: (id) => api.get(`/${id}`),
  deleteMeetup: (id) => api.delete(`/${id}`),
  getParticipants: (id) => api.get(`/${id}/participants`),
};
```

기존과 동일 — 백엔드가 이제 `Page<MeetupDTO>` 를 반환하므로 프론트에서 응답 구조만 맞춰줌.

- [ ] **Step 2: `MeetupManagementSection.js` 페이징 상태 및 UI 추가**

`MeetupManagementSection.js`에서 상태 및 fetch 로직 수정:

```js
// 추가할 상태
const [page, setPage] = useState(0);
const [totalPages, setTotalPages] = useState(0);
const PAGE_SIZE = 20;
```

`fetchMeetups` 함수 수정:

```js
const fetchMeetups = useCallback(async () => {
  try {
    setLoading(true);
    setError(null);
    const params = { page, size: PAGE_SIZE };
    if (status && status !== "ALL") params.status = status;
    if (q) params.q = q;

    const res = await meetupAdminApi.listMeetups(params);
    // 백엔드가 Page<MeetupDTO> 반환 → res.data.content
    setMeetups(res.data.content || []);
    setTotalPages(res.data.totalPages || 0);
  } catch (e) {
    console.error("모임 목록 조회 실패:", e);
    setError(e.response?.data?.message || "목록을 불러오지 못했습니다.");
  } finally {
    setLoading(false);
  }
}, [status, q, page]);
```

`useEffect` deps에 `page` 추가:

```js
useEffect(() => {
  fetchMeetups();
}, [fetchMeetups]);
```

검색 버튼 핸들러에 `setPage(0)` 추가 (새 검색 시 첫 페이지로):

```js
const handleSearch = () => {
  setPage(0);
  fetchMeetups();
};
```

페이징 버튼 UI를 목록 하단에 추가:

```jsx
{
  totalPages > 1 && (
    <PaginationRow>
      <PageBtn
        onClick={() => setPage((p) => Math.max(0, p - 1))}
        disabled={page === 0}
      >
        이전
      </PageBtn>
      <span>
        {page + 1} / {totalPages}
      </span>
      <PageBtn
        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
        disabled={page >= totalPages - 1}
      >
        다음
      </PageBtn>
    </PaginationRow>
  );
}
```

styled-component 추가:

```js
const PaginationRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${(props) => props.theme.spacing.sm};
  justify-content: center;
  margin-top: ${(props) => props.theme.spacing.md};
`;

const PageBtn = styled.button`
  padding: ${(props) => props.theme.spacing.xs} ${(props) =>
      props.theme.spacing.sm};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 4px;
  background: ${(props) =>
    props.disabled ? props.theme.colors.backgroundSecondary : "white"};
  cursor: ${(props) => (props.disabled ? "not-allowed" : "pointer")};
`;
```

- [ ] **Step 3: 프론트 빌드 확인**

```bash
cd frontend && npm run build
```

Expected: Compiled successfully.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/api/meetupAdminApi.js \
        frontend/src/components/Admin/sections/MeetupManagementSection.js
git commit -m "feat(admin-fe): Meetup 관리 페이지 페이징 UI 추가"
```

---

## Task 18: 프론트엔드 — careRequestAdminApi deleted 필터 연결

**Files:**

- Modify: `frontend/src/api/careRequestAdminApi.js`

- [ ] **Step 1: `careRequestAdminApi.js` 페이징 파라미터 추가**

```js
// frontend/src/api/careRequestAdminApi.js
import { createAuthAxios } from "./apiClient";

const api = createAuthAxios("http://localhost:8080/api/admin/care-requests");

export const careRequestAdminApi = {
  listCareRequests: (params) => api.get("", { params }), // page, size 포함해서 호출
  getCareRequest: (id) => api.get(`/${id}`),
  updateStatus: (id, status) =>
    api.patch(`/${id}/status`, null, { params: { status } }),
  deleteCareRequest: (id) => api.post(`/${id}/delete`),
  restoreCareRequest: (id) => api.post(`/${id}/restore`),
};
```

- [ ] **Step 2: `CareServiceManagementSection.js`에 페이징 상태 추가**

현재 파일에서 `careRequests` 상태 아래에 추가:

```js
const [page, setPage] = useState(0);
const [totalPages, setTotalPages] = useState(0);
const PAGE_SIZE = 20;
```

`fetchCareRequests` 함수 수정:

```js
const res = await careRequestAdminApi.listCareRequests({
  ...params,
  page,
  size: PAGE_SIZE,
});
setCareRequests(res.data.content || []);
setTotalPages(res.data.totalPages || 0);
```

목록 하단에 페이징 버튼 (MeetupManagementSection과 동일한 패턴):

```jsx
{
  totalPages > 1 && (
    <PaginationRow>
      <PageBtn
        onClick={() => setPage((p) => Math.max(0, p - 1))}
        disabled={page === 0}
      >
        이전
      </PageBtn>
      <span>
        {page + 1} / {totalPages}
      </span>
      <PageBtn
        onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
        disabled={page >= totalPages - 1}
      >
        다음
      </PageBtn>
    </PaginationRow>
  );
}
```

styled-components (MeetupManagementSection과 동일 구조):

```js
const PaginationRow = styled.div`
  display: flex;
  align-items: center;
  gap: ${(p) => p.theme.spacing.sm};
  justify-content: center;
  margin-top: ${(p) => p.theme.spacing.md};
`;
const PageBtn = styled.button`
  padding: ${(p) => p.theme.spacing.xs} ${(p) => p.theme.spacing.sm};
  border: 1px solid ${(p) => p.theme.colors.border};
  border-radius: 4px;
  background: ${(p) =>
    p.disabled ? p.theme.colors.backgroundSecondary : "white"};
  cursor: ${(p) => (p.disabled ? "not-allowed" : "pointer")};
`;
```

- [ ] **Step 3: 프론트 빌드 확인**

```bash
cd frontend && npm run build
```

Expected: Compiled successfully.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/api/careRequestAdminApi.js \
        frontend/src/components/Admin/sections/CareServiceManagementSection.js
git commit -m "feat(admin-fe): CareRequest 관리 페이지 페이징 + deleted 필터 백엔드 연결"
```

---

## Self-Review

### Spec Coverage 확인

| 분석에서 발견된 문제                               | 담당 Task                                 |
| -------------------------------------------------- | ----------------------------------------- |
| `deleteMeetup("ADMIN")` 런타임 버그                | Task 1 ✅                                 |
| `restoreCareRequest` UnsupportedOperationException | Task 2 ✅                                 |
| `AdminAuditLog` 엔티티 없음                        | Task 3 ✅                                 |
| `SystemConfig` DB 저장 없음                        | Task 4, 11, 16 ✅                         |
| Meetup 전량 로드 OOM                               | Task 10 (Facade + Paged), Task 17 (FE) ✅ |
| CareRequest 전량 로드 + deleted 필터 무시          | Task 7, 10, 18 ✅                         |
| File `findAll()` 풀스캔                            | Task 8, 15 ✅                             |
| User 목록 필터 없음                                | Task 6, 12 ✅                             |
| AdminUserManagement Repository 직접 접근           | Task 9, 13 ✅                             |
| `changeAdminRole` no-op                            | Task 13 ✅                                |
| 감사 로그 없음                                     | Task 5, 9~16 ✅                           |
| `AdminSystemController` 하드코딩                   | Task 11, 16 ✅                            |

### Placeholder 스캔

- 모든 Step에 실제 코드 포함 ✅
- TBD/TODO 없음 ✅
- 타입 일관성: `AdminUserFacade` ↔ `AdminUserController`, `AdminCareAndMeetupFacade` ↔ 두 컨트롤러, `AdminSystemFacade` ↔ `AdminSystemController` ✅

### 알려진 제한 (범위 외)

- `AdminCareAndMeetupFacade.getMeetups()` Meetup 필터는 페이지 내 Java 필터 유지 (MeetupRepository에 admin 전용 쿼리 미추가 — 모임 수가 일반적으로 많지 않아 우선순위 낮음, 향후 확장 가능)
- `AdminStatisticsController.setSchedulerTime()` 501 → 범위 외 (동적 스케줄러는 별도 태스크)
