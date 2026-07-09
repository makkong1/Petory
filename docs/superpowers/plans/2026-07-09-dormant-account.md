# 휴면 계정(Dormant Account) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1년간 로그인하지 않은 사용자를 매일 자정 배치로 휴면 전환하고, 일반 로그인 시도 시 차단 후 본인 확인만으로 즉시 재활성화한다.

**Architecture:** `Users`에 `isDormant`/`dormantAt` 필드를 `isDeleted`/`deletedAt`과 동일한 패턴으로 추가한다. 매일 자정 스케줄러가 벌크 UPDATE 쿼리 1회로 휴면 대상을 일괄 전환한다. 로그인은 새 엔드포인트 없이 기존 `POST /api/auth/login`에 `confirmReactivate` 플래그를 추가해 처리한다.

**Tech Stack:** Spring Boot 3.5.7, Java 17, Spring Data JPA, `@Scheduled`, JUnit 5 + Mockito, React 19.

## Global Constraints

- 스펙 문서: `docs/superpowers/specs/2026-07-09-dormant-account-design.md` (모든 세부 결정의 근거)
- 휴면 기준: 마지막 로그인(`lastLoginAt`) 또는 가입일(`createdAt`, `lastLoginAt`이 null인 경우)로부터 1년
- 휴면 판정은 매일 자정 배치(cron `0 0 0 * * *`)로만 수행 (로그인 시점 지연 판정 아님)
- 휴면 상태는 기존 `UserStatus`(ACTIVE/SUSPENDED/BANNED)와 별개 필드(`isDormant`/`dormantAt`)로 관리
- 로그인 차단/재활성화는 **일반 로그인(`AuthService.login()`)에만** 적용 — OAuth2, refresh 토큰 갱신은 비범위
- 재활성화는 사용자 본인의 로그인 재시도로만 가능 (관리자 강제 재활성화 없음)
- 탈퇴(`isDeleted=true`) 계정은 휴면 배치 대상에서 제외
- 기존 코드 컨벤션 준수: `UsersRepository`(도메인 인터페이스) → `JpaUsersAdapter`(구현체) → `SpringDataJpaUsersRepository`(JPA 전용) 3단 구조 유지, `ApiException` 상속 예외 패턴 유지

---

### Task 1: Users 엔티티 + 리포지토리 휴면 필드/벌크 쿼리

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/user/entity/Users.java:95-101`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/repository/JpaUsersAdapter.java`

**Interfaces:**
- Produces: `UsersRepository.markDormantUsers(LocalDateTime cutoff, LocalDateTime now)` → `int` (업데이트된 행 수). Task 2의 `UserDormantService`가 이 메서드를 호출한다.
- Produces: `Users.isDormant`(`Boolean`, 기본 `false`), `Users.dormantAt`(`LocalDateTime`) 필드 + Lombok `@Getter`/`@Setter`로 자동 생성되는 `getIsDormant()`/`setIsDormant()`/`getDormantAt()`/`setDormantAt()`.

이 태스크는 순수 스캐폴딩(엔티티 필드 + 벌크 쿼리 선언)이라 독자적인 단위 테스트 대상이 없다. 기존 `incrementWarningCount`(같은 파일의 `@Modifying` 벌크 쿼리)도 별도 테스트 없이 컴파일 확인만 거치는 것과 동일한 컨벤션이다. 실제 동작 검증은 Task 2에서 이 메서드를 호출하는 `UserDormantService`의 단위 테스트로 이루어진다.

- [ ] **Step 1: `Users.java`에 필드 추가**

`backend/main/java/com/linkup/Petory/domain/user/entity/Users.java:101` (`deletedAt` 필드) 바로 뒤에 삽입:

```java
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 휴면 계정 관련 필드 (제재 상태 UserStatus와 독립적)
    @Column(name = "is_dormant")
    @Builder.Default
    private Boolean isDormant = false;

    @Column(name = "dormant_at")
    private LocalDateTime dormantAt;
```

- [ ] **Step 2: `UsersRepository` 인터페이스에 메서드 추가**

`backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java` 마지막 메서드(`countByRoleAndCreatedAtBetween`) 뒤에 추가:

```java
    /**
     * 휴면 계정 일괄 전환 (배치용) - 마지막 로그인 또는 가입일이 cutoff 이전인 활성 사용자를 휴면 처리
     *
     * @return 업데이트된 행 수
     */
    int markDormantUsers(LocalDateTime cutoff, LocalDateTime now);
```

- [ ] **Step 3: `SpringDataJpaUsersRepository`에 벌크 UPDATE 쿼리 추가**

`backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java`의 `incrementWarningCount` 메서드 뒤에 추가:

```java
    /**
     * 휴면 계정 일괄 전환 (배치용)
     * lastLoginAt이 있으면 그 값을, 없으면(가입 후 미로그인) createdAt을 기준으로 판정
     *
     * @return 업데이트된 행 수
     */
    @RepositoryMethod("사용자: 휴면 계정 일괄 전환 (배치)")
    @Modifying
    @Query("UPDATE Users u SET u.isDormant = true, u.dormantAt = :now " +
           "WHERE u.isDormant = false AND u.isDeleted = false AND (" +
           "  (u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :cutoff) OR " +
           "  (u.lastLoginAt IS NULL AND u.createdAt < :cutoff)" +
           ")")
    int markDormantUsers(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
```

- [ ] **Step 4: `JpaUsersAdapter`에 위임 메서드 추가**

`backend/main/java/com/linkup/Petory/domain/user/repository/JpaUsersAdapter.java`의 `countByRoleAndCreatedAtBetween` 구현부 뒤에 추가:

```java
    @Override
    public int markDormantUsers(LocalDateTime cutoff, LocalDateTime now) {
        return jpaRepository.markDormantUsers(cutoff, now);
    }
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava -q`
Expected: 에러 없이 종료 (출력 없음)

- [ ] **Step 6: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/user/entity/Users.java \
  backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java \
  backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java \
  backend/main/java/com/linkup/Petory/domain/user/repository/JpaUsersAdapter.java
git commit -m "feat(user): 휴면 계정 필드 및 배치용 벌크 업데이트 쿼리 추가"
```

---

### Task 2: UserDormantService + UserDormantScheduler

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/user/service/UserDormantService.java`
- Create: `backend/main/java/com/linkup/Petory/domain/user/scheduler/UserDormantScheduler.java`
- Test: `backend/test/java/com/linkup/Petory/domain/user/service/UserDormantServiceTest.java`
- Test: `backend/test/java/com/linkup/Petory/domain/user/scheduler/UserDormantSchedulerTest.java`

**Interfaces:**
- Consumes: `UsersRepository.markDormantUsers(LocalDateTime cutoff, LocalDateTime now)` (Task 1에서 정의)
- Produces: `UserDormantService.markDormantUsers()` → `int` (업데이트된 행 수). Task 2 스케줄러와 향후 관리자 통계 기능이 사용할 수 있다.

- [ ] **Step 1: 실패하는 테스트 작성 (`UserDormantServiceTest`)**

`backend/test/java/com/linkup/Petory/domain/user/service/UserDormantServiceTest.java` 새로 생성:

```java
package com.linkup.Petory.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class UserDormantServiceTest {

    @InjectMocks
    private UserDormantService userDormantService;

    @Mock
    private UsersRepository usersRepository;

    @Test
    @DisplayName("정상: cutoff를 현재로부터 1년 전으로 계산해 리포지토리에 위임한다")
    void 정상_cutoff_1년전_계산() {
        when(usersRepository.markDormantUsers(any(), any())).thenReturn(3);

        int updated = userDormantService.markDormantUsers();

        assertThat(updated).isEqualTo(3);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usersRepository).markDormantUsers(cutoffCaptor.capture(), nowCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusYears(1);
        assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(5, ChronoUnit.SECONDS));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.service.UserDormantServiceTest" -q`
Expected: FAIL — `UserDormantService` 클래스가 존재하지 않아 컴파일 에러

- [ ] **Step 3: `UserDormantService` 최소 구현**

`backend/main/java/com/linkup/Petory/domain/user/service/UserDormantService.java` 새로 생성:

```java
package com.linkup.Petory.domain.user.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDormantService {

    private static final long DORMANT_AFTER_YEARS = 1;

    private final UsersRepository usersRepository;

    /**
     * 1년간 미로그인(또는 가입 후 미로그인 상태로 1년 경과)한 활성 사용자를 휴면 전환한다.
     *
     * @return 업데이트된 행 수
     */
    @Transactional
    public int markDormantUsers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusYears(DORMANT_AFTER_YEARS);
        int updated = usersRepository.markDormantUsers(cutoff, now);
        log.info("휴면 계정 전환: {}건, cutoff={}", updated, cutoff);
        return updated;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.service.UserDormantServiceTest" -q`
Expected: PASS

- [ ] **Step 5: 스케줄러 실패하는 테스트 작성 (`UserDormantSchedulerTest`)**

`backend/test/java/com/linkup/Petory/domain/user/scheduler/UserDormantSchedulerTest.java` 새로 생성:

```java
package com.linkup.Petory.domain.user.scheduler;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.user.service.UserDormantService;

@ExtendWith(MockitoExtension.class)
class UserDormantSchedulerTest {

    @InjectMocks
    private UserDormantScheduler userDormantScheduler;

    @Mock
    private UserDormantService userDormantService;

    @Test
    @DisplayName("정상: 스케줄러 실행 시 UserDormantService.markDormantUsers()를 호출한다")
    void 정상_배치실행_서비스호출() {
        userDormantScheduler.markDormantUsers();

        verify(userDormantService).markDormantUsers();
    }
}
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.scheduler.UserDormantSchedulerTest" -q`
Expected: FAIL — `UserDormantScheduler` 클래스가 존재하지 않아 컴파일 에러

- [ ] **Step 7: `UserDormantScheduler` 구현**

`backend/main/java/com/linkup/Petory/domain/user/scheduler/UserDormantScheduler.java` 새로 생성 (`UserSanctionScheduler`와 동일 패턴):

```java
package com.linkup.Petory.domain.user.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.service.UserDormantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDormantScheduler {

    private final UserDormantService userDormantService;

    /**
     * 매일 자정에 1년 미로그인 사용자를 휴면 전환
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void markDormantUsers() {
        log.info("휴면 계정 전환 배치 시작");
        try {
            userDormantService.markDormantUsers();
            log.info("휴면 계정 전환 배치 완료");
        } catch (Exception e) {
            log.error("휴면 계정 전환 배치 실패", e);
        }
    }
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.scheduler.UserDormantSchedulerTest" -q`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/user/service/UserDormantService.java \
  backend/main/java/com/linkup/Petory/domain/user/scheduler/UserDormantScheduler.java \
  backend/test/java/com/linkup/Petory/domain/user/service/UserDormantServiceTest.java \
  backend/test/java/com/linkup/Petory/domain/user/scheduler/UserDormantSchedulerTest.java
git commit -m "feat(user): 휴면 계정 자동 전환 배치 (매일 자정 스케줄러)"
```

---

### Task 3: 로그인 차단 + 본인 확인 재활성화

**Files:**
- Create: `backend/main/java/com/linkup/Petory/domain/user/exception/UserDormantException.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/dto/LoginRequest.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java:36-56`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java:46-65`
- Test: `backend/test/java/com/linkup/Petory/domain/user/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `Users.getIsDormant()`/`setIsDormant()`/`setDormantAt()` (Task 1에서 정의)
- Produces: `UserDormantException`(HTTP 403, errorCode `"USER_DORMANT"`) — Task 5 프론트엔드가 `error.response.data.errorCode`로 감지. `AuthService.login(String id, String password, boolean confirmReactivate)` — 시그니처 변경, `AuthController`가 호출.

- [ ] **Step 1: 실패하는 테스트 작성 (`AuthServiceTest`에 추가)**

`backend/test/java/com/linkup/Petory/domain/user/service/AuthServiceTest.java:26`(`import com.linkup.Petory.domain.user.exception.UserSuspendedException;`) 바로 뒤에 import 1개 추가:

```java
import com.linkup.Petory.domain.user.exception.UserDormantException;
```

파일 마지막 테스트(`정상_로그아웃_refresh제거`) 뒤, 클로징 `}` 앞에 추가:

```java

    @Test
    @DisplayName("예외: 휴면 계정은 confirmReactivate 없이 로그인할 수 없다")
    void 예외_휴면계정_재활성화확인없이_로그인차단() {
        Users user = Users.builder()
                .id("dormant-user")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDormant(true)
                .dormantAt(LocalDateTime.now().minusDays(1))
                .build();
        when(usersRepository.findActiveByIdString("dormant-user")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("dormant-user", "password", false))
                .isInstanceOf(UserDormantException.class);

        assertThat(user.getIsDormant()).isTrue();
    }

    @Test
    @DisplayName("정상: 휴면 계정은 confirmReactivate=true면 즉시 재활성화되며 로그인에 성공한다")
    void 정상_휴면계정_재활성화확인시_로그인성공() {
        Users user = Users.builder()
                .id("dormant-user")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDormant(true)
                .dormantAt(LocalDateTime.now().minusDays(1))
                .build();
        UsersDTO dto = UsersDTO.builder().id("dormant-user").build();
        when(usersRepository.findActiveByIdString("dormant-user")).thenReturn(Optional.of(user));
        when(jwtUtil.createAccessToken("dormant-user")).thenReturn("access-token");
        when(jwtUtil.createRefreshToken()).thenReturn("refresh-token");
        when(usersConverter.toDTO(user)).thenReturn(dto);

        TokenResponse response = authService.login("dormant-user", "password", true);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getIsDormant()).isFalse();
        assertThat(user.getDormantAt()).isNull();
        verify(usersRepository).save(user);
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.service.AuthServiceTest" -q`
Expected: FAIL — `authService.login(id, password, boolean)` 3-인자 오버로드가 없어 컴파일 에러, `UserDormantException` 클래스 없음

- [ ] **Step 3: `UserDormantException` 생성**

`backend/main/java/com/linkup/Petory/domain/user/exception/UserDormantException.java` 새로 생성:

```java
package com.linkup.Petory.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.linkup.Petory.global.exception.ApiException;

/**
 * 휴면 처리된 계정이 재활성화 확인 없이 로그인을 시도할 때 발생하는 예외.
 * HTTP 403 Forbidden
 */
public class UserDormantException extends ApiException {

    public static final String ERROR_CODE = "USER_DORMANT";

    public UserDormantException() {
        super("장기간 미접속으로 휴면 처리된 계정입니다. 재활성화 확인이 필요합니다.", HttpStatus.FORBIDDEN, ERROR_CODE);
    }
}
```

- [ ] **Step 4: `LoginRequest`에 `confirmReactivate` 필드 추가**

`backend/main/java/com/linkup/Petory/domain/user/dto/LoginRequest.java` 전체 교체:

```java
package com.linkup.Petory.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO (record)
 * - 불변 객체로 요청 데이터의 의도치 않은 변경 방지
 * - confirmReactivate: 휴면 계정 로그인 시 본인이 재활성화에 동의했는지 여부 (기본 false)
 */
public record LoginRequest(
    @NotBlank String id,
    @NotBlank String password,
    boolean confirmReactivate
) {}
```

- [ ] **Step 5: `AuthService.login()`에 휴면 체크 추가**

`backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java:36-56`의 기존 코드:

```java
    @Transactional
    public TokenResponse login(String id, String password) {
        Users user = usersRepository.findActiveByIdString(id)
                .orElseThrow(UserNotFoundException::new);

        // 제재 상태 확인
        if (user.getStatus() == UserStatus.BANNED) {
            throw new UserBannedException();
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                throw new UserSuspendedException(user.getSuspendedUntil());
            } else {
                // 만료된 이용제한 자동 해제
                user.setStatus(UserStatus.ACTIVE);
                user.setSuspendedUntil(null);
                usersRepository.save(user);
                log.info("만료된 이용제한 자동 해제: {}", id);
            }
        }
```

다음으로 교체:

```java
    @Transactional
    public TokenResponse login(String id, String password, boolean confirmReactivate) {
        Users user = usersRepository.findActiveByIdString(id)
                .orElseThrow(UserNotFoundException::new);

        // 제재 상태 확인
        if (user.getStatus() == UserStatus.BANNED) {
            throw new UserBannedException();
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                throw new UserSuspendedException(user.getSuspendedUntil());
            } else {
                // 만료된 이용제한 자동 해제
                user.setStatus(UserStatus.ACTIVE);
                user.setSuspendedUntil(null);
                usersRepository.save(user);
                log.info("만료된 이용제한 자동 해제: {}", id);
            }
        }

        // 휴면 상태 확인 - 본인 확인(confirmReactivate) 없이는 로그인 차단
        if (Boolean.TRUE.equals(user.getIsDormant())) {
            if (!confirmReactivate) {
                throw new UserDormantException();
            }
            user.setIsDormant(false);
            user.setDormantAt(null);
            log.info("휴면 계정 재활성화: {}", id);
        }
```

`AuthService.java` 상단 import 목록에 추가:

```java
import com.linkup.Petory.domain.user.exception.UserDormantException;
```

- [ ] **Step 6: `AuthController.login()`에서 새 필드 전달**

`backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java:54-55`의 기존 코드:

```java
        TokenResponse tokenResponse = authService.login(loginRequest.id(), loginRequest.password());
```

다음으로 교체:

```java
        TokenResponse tokenResponse = authService.login(
                loginRequest.id(), loginRequest.password(), loginRequest.confirmReactivate());
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests "com.linkup.Petory.domain.user.service.AuthServiceTest" -q`
Expected: PASS (기존 테스트 포함 전체)

- [ ] **Step 8: 전체 컴파일 확인 (다른 호출부 누락 방지)**

Run: `./gradlew compileJava compileTestJava -q`
Expected: 에러 없이 종료 — `authService.login(id, password)` 2-인자 호출부가 남아있으면 여기서 컴파일 에러로 드러난다.

- [ ] **Step 9: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/user/exception/UserDormantException.java \
  backend/main/java/com/linkup/Petory/domain/user/dto/LoginRequest.java \
  backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java \
  backend/main/java/com/linkup/Petory/domain/user/controller/AuthController.java \
  backend/test/java/com/linkup/Petory/domain/user/service/AuthServiceTest.java
git commit -m "feat(user): 휴면 계정 로그인 차단 및 본인 확인 재활성화"
```

---

### Task 4: UsersDTO / UsersConverter에 휴면 필드 노출

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/user/dto/UsersDTO.java`
- Modify: `backend/main/java/com/linkup/Petory/domain/user/converter/UsersConverter.java:43-44`

**Interfaces:**
- Consumes: `Users.getIsDormant()`, `Users.getDormantAt()` (Task 1)
- Produces: `UsersDTO.isDormant`(`Boolean`), `UsersDTO.dormantAt`(`LocalDateTime`) — Task 5(프론트 로그인 응답), Task 6(관리자 목록)이 사용.

이 프로젝트에는 `UsersConverter` 전용 단위 테스트가 없다(기존 컨벤션). Task 3의 `AuthServiceTest`에서 이미 `usersConverter.toDTO(user)`를 모킹해 검증하므로, 여기서는 컴파일 확인으로 충분하다.

- [ ] **Step 1: `UsersDTO`에 필드 추가**

`backend/main/java/com/linkup/Petory/domain/user/dto/UsersDTO.java`의 마지막 필드(`deletedAt`) 뒤에 추가:

```java
    // 소프트 삭제 관련 필드
    private Boolean isDeleted;
    private LocalDateTime deletedAt;

    // 휴면 계정 관련 필드
    private Boolean isDormant;
    private LocalDateTime dormantAt;
```

- [ ] **Step 2: `UsersConverter.toDTO()`에 매핑 추가**

`backend/main/java/com/linkup/Petory/domain/user/converter/UsersConverter.java:43-44`의 기존 코드:

```java
                .isDeleted(user.getIsDeleted())
                .deletedAt(user.getDeletedAt())
                .build();
```

다음으로 교체:

```java
                .isDeleted(user.getIsDeleted())
                .deletedAt(user.getDeletedAt())
                .isDormant(user.getIsDormant())
                .dormantAt(user.getDormantAt())
                .build();
```

- [ ] **Step 3: 컴파일 및 전체 테스트 확인**

Run: `./gradlew compileJava compileTestJava test -q`
Expected: 에러 없이 종료, 모든 테스트 PASS

- [ ] **Step 4: Commit**

```bash
git add backend/main/java/com/linkup/Petory/domain/user/dto/UsersDTO.java \
  backend/main/java/com/linkup/Petory/domain/user/converter/UsersConverter.java
git commit -m "feat(user): UsersDTO에 휴면 계정 필드 노출"
```

---

### Task 5: 프론트엔드 - 로그인 재활성화 플로우

**Files:**
- Modify: `frontend/src/api/authApi.js:21-40`
- Modify: `frontend/src/contexts/AuthContext.js:145-155`
- Modify: `frontend/src/components/Auth/LoginForm.js:44-61`

**Interfaces:**
- Consumes: 백엔드 `POST /api/auth/login` 요청 바디에 `confirmReactivate`(boolean), 에러 응답의 `errorCode === "USER_DORMANT"` (Task 3)
- Produces: `authApi.login(id, password, confirmReactivate = false)`, `useAuth().login(id, password, confirmReactivate = false)` — 둘 다 시그니처에 3번째 선택 인자 추가(하위 호환 유지, 기존 호출부는 그대로 동작).

이 프로젝트는 `LoginForm`에 대한 프론트엔드 단위 테스트가 없다. CLAUDE.md 지침에 따라 이 태스크는 dev 서버를 띄워 브라우저에서 수동 검증한다.

- [ ] **Step 1: `authApi.login`에 `confirmReactivate` 파라미터 추가**

`frontend/src/api/authApi.js:21-40`의 기존 코드:

```javascript
  login: async (id, password) => {
    if (isDemoMode()) {
      setToken(DEMO_TOKEN);
      setRefreshToken(DEMO_REFRESH_TOKEN);
      return { accessToken: DEMO_TOKEN, refreshToken: DEMO_REFRESH_TOKEN, user: DEMO_USER };
    }
    try {
      const response = await api.post('/login', { id, password });
      const { accessToken, refreshToken } = response.data;

      if (accessToken) {
        setToken(accessToken);
      }
      if (refreshToken) {
        setRefreshToken(refreshToken);
      }

      return response.data;
    } catch (error) {
      throw error;
    }
  },
```

다음으로 교체:

```javascript
  login: async (id, password, confirmReactivate = false) => {
    if (isDemoMode()) {
      setToken(DEMO_TOKEN);
      setRefreshToken(DEMO_REFRESH_TOKEN);
      return { accessToken: DEMO_TOKEN, refreshToken: DEMO_REFRESH_TOKEN, user: DEMO_USER };
    }
    try {
      const response = await api.post('/login', { id, password, confirmReactivate });
      const { accessToken, refreshToken } = response.data;

      if (accessToken) {
        setToken(accessToken);
      }
      if (refreshToken) {
        setRefreshToken(refreshToken);
      }

      return response.data;
    } catch (error) {
      throw error;
    }
  },
```

- [ ] **Step 2: `AuthContext.login`에 `confirmReactivate` 전달**

`frontend/src/contexts/AuthContext.js:145-155`의 기존 코드:

```javascript
  const login = async (username, password) => {
    try {
      const response = await authApi.login(username, password);
      // accessToken과 refreshToken은 authApi.login에서 자동 저장됨
      setUser(response.user);
      setIsAuthenticated(true);
      return response;
    } catch (error) {
      throw error;
    }
  };
```

다음으로 교체:

```javascript
  const login = async (username, password, confirmReactivate = false) => {
    try {
      const response = await authApi.login(username, password, confirmReactivate);
      // accessToken과 refreshToken은 authApi.login에서 자동 저장됨
      setUser(response.user);
      setIsAuthenticated(true);
      return response;
    } catch (error) {
      throw error;
    }
  };
```

- [ ] **Step 3: `LoginForm`에서 휴면 에러 감지 및 재활성화 confirm**

`frontend/src/components/Auth/LoginForm.js:44-61`의 기존 코드:

```javascript
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await login(formData.id, formData.password);

      setSuccess('로그인 성공!');
      
    } catch (error) {
      console.error('로그인 실패:', error);
      setError(error.response?.data?.error || '로그인 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };
```

다음으로 교체:

```javascript
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await login(formData.id, formData.password);

      setSuccess('로그인 성공!');

    } catch (error) {
      if (error.response?.data?.errorCode === 'USER_DORMANT') {
        const confirmed = window.confirm(
          '장기간 미접속으로 휴면 처리된 계정입니다. 재활성화하시겠습니까?'
        );
        if (confirmed) {
          try {
            await login(formData.id, formData.password, true);
            setSuccess('계정이 재활성화되었습니다. 로그인 성공!');
          } catch (reactivateError) {
            console.error('재활성화 실패:', reactivateError);
            setError(reactivateError.response?.data?.message || '재활성화 중 오류가 발생했습니다.');
          }
        }
        setLoading(false);
        return;
      }
      console.error('로그인 실패:', error);
      setError(error.response?.data?.message || error.response?.data?.error || '로그인 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };
```

(`error.response?.data?.message`를 `error`보다 먼저 확인하도록 바꿔, `ApiException` 기반 응답의 `message` 필드도 제대로 표시되게 함 — 기존엔 `data.error`만 읽어 BANNED/SUSPENDED 에러 메시지가 비어 보이는 기존 버그도 함께 해결됨)

- [ ] **Step 4: 수동 브라우저 검증**

```bash
./gradlew bootRun    # 터미널 1
cd frontend && npm start    # 터미널 2
```

MySQL에서 테스트 사용자 하나를 휴면 상태로 만들어 확인:

```sql
UPDATE users SET is_dormant = 1, dormant_at = NOW() WHERE id = '<테스트계정아이디>';
```

- 해당 계정으로 `/login`에서 로그인 시도 → "장기간 미접속으로 휴면 처리된 계정입니다. 재활성화하시겠습니까?" confirm 창이 뜨는지 확인
- 확인 클릭 → 로그인 성공하고 "재활성화되었습니다" 메시지 표시되는지 확인
- DB에서 `is_dormant = 0`, `dormant_at IS NULL`로 바뀌었는지 확인:

```sql
SELECT is_dormant, dormant_at FROM users WHERE id = '<테스트계정아이디>';
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/authApi.js frontend/src/contexts/AuthContext.js frontend/src/components/Auth/LoginForm.js
git commit -m "feat(frontend): 휴면 계정 로그인 차단 및 재활성화 확인 플로우"
```

---

### Task 6: 관리자 UserList에 휴면 컬럼 표시

**Files:**
- Modify: `frontend/src/components/User/UserList.js`

**Interfaces:**
- Consumes: `GET /api/admin/users/paging` 응답의 `user.isDormant`(`Boolean`) (Task 4에서 `UsersDTO`에 이미 노출됨 — 백엔드 추가 변경 불필요)

- [ ] **Step 1: 테이블 헤더에 "휴면" 컬럼 추가**

`frontend/src/components/User/UserList.js`의 `<th>삭제됨</th>` 바로 뒤에 추가:

```jsx
                <th>삭제됨</th>
                <th>휴면</th>
```

- [ ] **Step 2: 테이블 바디에 값 렌더링 추가**

`<td>{user.isDeleted ? 'Y' : 'N'}</td>` 바로 뒤에 추가:

```jsx
                  <td>{user.isDeleted ? 'Y' : 'N'}</td>
                  <td>{user.isDormant ? 'Y' : 'N'}</td>
```

- [ ] **Step 3: ESLint 확인**

Run: `cd frontend && npx eslint src/components/User/UserList.js`
Expected: 에러 없음

- [ ] **Step 4: 수동 브라우저 검증**

관리자 계정으로 로그인 후 `/admin` → 사용자 관리 탭에서 Task 5 Step 4에서 휴면 처리한 테스트 계정의 "휴면" 컬럼이 "Y"로 표시되는지 확인 (재활성화 후 재확인하면 "N"으로 바뀌어야 함).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/User/UserList.js
git commit -m "feat(admin): 사용자 목록에 휴면 여부 컬럼 추가"
```

---

### Task 7: 문서 업데이트

**Files:**
- Modify: `docs/domains/user.md`
- Modify: `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md`
- Modify: `docs/domain-page-drafts/user-domain-v2-content.md` (Petory)
- Modify: `/Users/maknkkong/project/makkong1-github.io/docs/domain-page-drafts/user-domain-v2-content.md` (동기화)

**Interfaces:**
- Consumes: Task 1~6에서 구현된 실제 필드명/엔드포인트/동작 (문서는 코드를 따라간다 — 코드가 먼저, 문서가 나중)

- [ ] **Step 1: `docs/domains/user.md`에 휴면 계정 필드/API 설명 추가**

`docs/domains/user.md:75`의 기존 줄:

```
| `isDeleted`, `deletedAt`              | soft delete 상태                                    |
```

바로 뒤에 추가:

```
| `isDormant`, `dormantAt`              | 휴면 상태 (1년 미로그인 시 배치로 자동 전환)         |
```

`docs/domains/user.md:146`의 기존 줄:

```
| `POST /api/auth/login`           | 로그인, access/refresh token 발급        |
```

다음으로 교체:

```
| `POST /api/auth/login`           | 로그인, access/refresh token 발급. `confirmReactivate=true`로 휴면 계정 본인 확인 재활성화 |
```

- [ ] **Step 2: 아키텍처 문서에 로그인 시퀀스 + 정책 갱신**

`docs/architecture/user/사용자 인증 및 프로필 아키텍처.md:80`의 기존 줄:

```
  AS->>AS: BANNED/SUSPENDED 검사
```

다음으로 교체:

```
  AS->>AS: BANNED/SUSPENDED 검사
  AS->>AS: isDormant 검사 (confirmReactivate 없으면 UserDormantException)
```

같은 파일 305-308번째 줄의 기존 텍스트:

```
- 로그인/OAuth2 로그인 모두 `Users.status`를 검사한다.
- `BANNED`는 로그인 불가다.
- `SUSPENDED`는 만료 전이면 로그인 불가다.
- 만료된 정지는 로그인 시 또는 스케줄러에서 `ACTIVE`로 해제된다.
```

다음으로 교체:

```
- 로그인/OAuth2 로그인 모두 `Users.status`를 검사한다.
- `BANNED`는 로그인 불가다.
- `SUSPENDED`는 만료 전이면 로그인 불가다.
- 만료된 정지는 로그인 시 또는 스케줄러에서 `ACTIVE`로 해제된다.
- `isDormant`는 `Users.status`와 독립적인 별도 필드다. 일반 로그인에서만 검사하며(OAuth2 제외), `confirmReactivate=true` 없이는 `UserDormantException`(403, `USER_DORMANT`)을 던진다. 1년 미로그인 시 매일 자정 배치(`UserDormantScheduler`)가 자동으로 휴면 전환한다.
```

- [ ] **Step 3: `user-domain-v2-content.md` 갱신 (Petory)**

`docs/domain-page-drafts/user-domain-v2-content.md`에 휴면 계정 설계 요약 섹션 추가 (스펙 문서 `docs/superpowers/specs/2026-07-09-dormant-account-design.md` 내용을 근거로).

- [ ] **Step 4: `makkong1-github.io` 저장소에 동일 파일 동기화**

```bash
cp "/Users/maknkkong/project/Petory/docs/domain-page-drafts/user-domain-v2-content.md" \
   "/Users/maknkkong/project/makkong1-github.io/docs/domain-page-drafts/user-domain-v2-content.md"
```

Run (동기화 확인): `diff "/Users/maknkkong/project/Petory/docs/domain-page-drafts/user-domain-v2-content.md" "/Users/maknkkong/project/makkong1-github.io/docs/domain-page-drafts/user-domain-v2-content.md"`
Expected: 출력 없음 (완전히 동일)

- [ ] **Step 5: Petory 쪽 문서 Commit**

```bash
git add docs/domains/user.md "docs/architecture/user/사용자 인증 및 프로필 아키텍처.md" docs/domain-page-drafts/user-domain-v2-content.md
git commit -m "docs(user): 휴면 계정 기능 반영"
```

- [ ] **Step 6: makkong1-github.io 쪽은 사용자에게 커밋 여부 확인**

`makkong1-github.io`는 별도 저장소이며 이미 관련 없는 미커밋 변경사항이 있으므로, 자동으로 커밋/푸시하지 않고 사용자에게 커밋할지 확인한다.

---

## 최종 확인 (전체 태스크 완료 후)

```bash
./gradlew compileJava compileTestJava test -q
cd frontend && npx eslint src/components/User/UserList.js src/components/Auth/LoginForm.js src/api/authApi.js src/contexts/AuthContext.js
```

Expected: 백엔드 전체 테스트 PASS, 프론트 ESLint 에러 없음.
