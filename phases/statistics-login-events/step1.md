# Step 1 — LoginEvent 엔티티 + 레포지토리 3종

## 목표
`Users.lastLoginAt` 단일 컬럼 대신 로그인 이벤트를 append 방식으로 저장하는 `login_events` 테이블과 Port-Adapter 레포지토리 계층을 생성한다.

## 생성 파일

### 1. `domain/user/entity/LoginEvent.java`

```java
package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 로그인 이벤트 기록. append-only — 삭제·수정 없이 쌓기만 한다. */
@Entity
@Table(name = "login_events", indexes = {
        @Index(name = "idx_login_events_user_login_at", columnList = "user_id, login_at"),
        @Index(name = "idx_login_events_login_at", columnList = "login_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    /** LOCAL / GOOGLE / NAVER / KAKAO */
    @Column(name = "login_method", length = 16, nullable = false)
    private String loginMethod;
}
```

### 2. `domain/user/repository/LoginEventRepository.java` (Port)

```java
package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.LoginEvent;

public interface LoginEventRepository {
    LoginEvent save(LoginEvent loginEvent);
    long countDistinctUsersBetween(LocalDateTime start, LocalDateTime end);
}
```

### 3. `domain/user/repository/SpringDataJpaLoginEventRepository.java`

```java
package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.user.entity.LoginEvent;
import com.linkup.Petory.global.annotation.RepositoryMethod;

public interface SpringDataJpaLoginEventRepository extends JpaRepository<LoginEvent, Long> {

    @RepositoryMethod("로그인 이벤트: 기간 내 DISTINCT 사용자 수 (DAU 집계용)")
    @Query("SELECT COUNT(DISTINCT l.user.id) FROM LoginEvent l WHERE l.loginAt BETWEEN :start AND :end")
    long countDistinctUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
```

### 4. `domain/user/repository/JpaLoginEventAdapter.java`

```java
package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.LoginEvent;

import lombok.RequiredArgsConstructor;

@Repository
@Primary
@RequiredArgsConstructor
public class JpaLoginEventAdapter implements LoginEventRepository {

    private final SpringDataJpaLoginEventRepository jpaRepository;

    @Override
    public LoginEvent save(LoginEvent loginEvent) {
        return jpaRepository.save(loginEvent);
    }

    @Override
    public long countDistinctUsersBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countDistinctUsersBetween(start, end);
    }
}
```

## Acceptance Criteria
- `./gradlew compileJava` 성공
- `login_events` 테이블이 두 인덱스(`user_id+login_at`, `login_at`)와 함께 생성되어야 한다.
