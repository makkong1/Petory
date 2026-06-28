# Step 2 — AuthService + OAuth2Service 로그인 이벤트 append

## 목표
로컬 로그인(`AuthService.login`)과 OAuth2 로그인(`OAuth2Service.processOAuth2Login`) 두 진입점에서 `LoginEvent`를 append 저장한다. `Users.lastLoginAt`은 하위 호환성(기존 UI 표시 등)을 위해 그대로 유지한다.

## 수정 파일

### 1. `AuthService.java`

`loginEventRepository` 필드 추가 + `login()` 내 append 호출:

```java
// 기존 필드 아래에 추가
private final LoginEventRepository loginEventRepository;

// login() 메서드 내 user.setLastLoginAt(LocalDateTime.now()) 바로 아래에 추가:
loginEventRepository.save(LoginEvent.builder()
        .user(user)
        .loginAt(LocalDateTime.now())
        .loginMethod("LOCAL")
        .build());
```

필요한 import:
```java
import com.linkup.Petory.domain.user.entity.LoginEvent;
import com.linkup.Petory.domain.user.repository.LoginEventRepository;
```

### 2. `OAuth2Service.java`

`loginEventRepository` 필드 추가 + `processOAuth2Login()` 내 append 호출:

```java
// 기존 필드 아래에 추가
private final LoginEventRepository loginEventRepository;

// processOAuth2Login() 내 user.setLastLoginAt(LocalDateTime.now()) 바로 아래에 추가:
loginEventRepository.save(LoginEvent.builder()
        .user(user)
        .loginAt(LocalDateTime.now())
        .loginMethod(provider.name())
        .build());
```

필요한 import:
```java
import com.linkup.Petory.domain.user.entity.LoginEvent;
import com.linkup.Petory.domain.user.repository.LoginEventRepository;
```

## 주의사항
- `Users.lastLoginAt` 업데이트 코드(`user.setLastLoginAt(...)`)는 삭제하지 않는다. 이 컬럼은 UI에서 "마지막 로그인" 표시 용도로 사용될 수 있다.
- `loginEventRepository.save()`는 `@Transactional` 범위 내에서 호출되므로 별도 트랜잭션 불필요.

## Acceptance Criteria
- `./gradlew compileJava` 성공
- 로컬 로그인 시 `login_events` 테이블에 `login_method='LOCAL'` 행 삽입 확인
- OAuth2 로그인 시 `login_method='GOOGLE'`(또는 NAVER/KAKAO) 행 삽입 확인
