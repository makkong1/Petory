# Auth 로그인/Refresh 토큰 - 시퀀스 다이어그램

## 개요
AuthService의 `login()` 및 `refreshAccessToken()`에서 발생하던 중복 DB 조회를 제거한 리팩토링 전후 시퀀스를 비교합니다.

---

## 1. login() - Before (리팩토링 전)

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthService
    participant UsersService
    participant UsersRepository
    participant UsersConverter
    participant DB

    Client->>AuthController: POST /login (id, password)
    AuthController->>AuthService: login(id, password)

    rect rgb(255, 240, 240)
        Note over AuthService,DB: 🔴 중복 조회 구간
        AuthService->>UsersRepository: findByIdString(id)
        UsersRepository->>DB: SELECT * FROM users WHERE id = ?
        DB-->>UsersRepository: User
        UsersRepository-->>AuthService: Users
    end

    AuthService->>AuthService: 제재 상태 확인
    AuthService->>AuthService: JWT 토큰 생성
    AuthService->>AuthService: refreshToken, lastLoginAt 설정

    AuthService->>UsersRepository: save(user)
    UsersRepository->>DB: UPDATE users ...
    DB-->>UsersRepository: OK
    UsersRepository-->>AuthService: Users

    rect rgb(255, 240, 240)
        Note over AuthService,DB: 🔴 동일 User 재조회
        AuthService->>UsersService: getUserById(id)
        UsersService->>UsersRepository: findByIdString(id)
        UsersRepository->>DB: SELECT * FROM users WHERE id = ?
        DB-->>UsersRepository: User
        UsersRepository-->>UsersService: Users
        UsersService->>UsersConverter: toDTO(user)
        UsersConverter-->>UsersService: UsersDTO
        UsersService-->>AuthService: UsersDTO
    end

    AuthService->>AuthService: new TokenResponse(...)
    AuthService-->>AuthController: TokenResponse
    AuthController-->>Client: 200 OK + TokenResponse
```

**문제점**: `findByIdString` 2회 호출 → DB 쿼리 2회

---

## 2. login() - After (리팩토링 후)

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthService
    participant UsersRepository
    participant UsersConverter
    participant DB

    Client->>AuthController: POST /login (id, password)
    AuthController->>AuthService: login(id, password)

    rect rgb(230, 255, 230)
        Note over AuthService,DB: ✅ 1회만 조회
        AuthService->>UsersRepository: findByIdString(id)
        UsersRepository->>DB: SELECT * FROM users WHERE id = ?
        DB-->>UsersRepository: User
        UsersRepository-->>AuthService: Users
    end

    AuthService->>AuthService: 제재 상태 확인
    AuthService->>AuthService: JWT 토큰 생성
    AuthService->>AuthService: refreshToken, lastLoginAt 설정

    AuthService->>UsersRepository: save(user)
    UsersRepository->>DB: UPDATE users ...
    DB-->>UsersRepository: OK
    UsersRepository-->>AuthService: Users

    rect rgb(230, 255, 230)
        Note over AuthService,UsersConverter: ✅ 이미 로드한 엔티티 활용
        AuthService->>UsersConverter: toDTO(user)
        UsersConverter-->>AuthService: UsersDTO
    end

    AuthService->>AuthService: new TokenResponse(...)
    AuthService-->>AuthController: TokenResponse
    AuthController-->>Client: 200 OK + TokenResponse
```

**개선점**: `findByIdString` 1회 → DB 쿼리 1회, UsersService 의존성 제거

---

## 3. refreshAccessToken() - Before

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthService
    participant UsersService
    participant UsersRepository
    participant DB

    Client->>AuthController: POST /refresh (refreshToken)
    AuthController->>AuthService: refreshAccessToken(refreshToken)

    rect rgb(255, 240, 240)
        AuthService->>UsersRepository: findByRefreshToken(token)
        UsersRepository->>DB: SELECT * FROM users WHERE refresh_token = ?
        DB-->>UsersRepository: User
        UsersRepository-->>AuthService: Users
    end

    AuthService->>AuthService: 만료 시간 확인
    AuthService->>AuthService: createAccessToken(user.getId())

    rect rgb(255, 240, 240)
        Note over AuthService,DB: 🔴 User 재조회 (findByIdString)
        AuthService->>UsersService: getUserById(user.getId())
        UsersService->>UsersRepository: findByIdString(id)
        UsersRepository->>DB: SELECT * FROM users WHERE id = ?
        DB-->>UsersRepository: User
        UsersRepository-->>UsersService: Users
        UsersService-->>AuthService: UsersDTO
    end

    AuthService-->>AuthController: TokenResponse
    AuthController-->>Client: 200 OK + TokenResponse
```

**문제점**: User 조회 2회 (findByRefreshToken + findByIdString)

---

## 4. refreshAccessToken() - After

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthService
    participant UsersRepository
    participant UsersConverter
    participant DB

    Client->>AuthController: POST /refresh (refreshToken)
    AuthController->>AuthService: refreshAccessToken(refreshToken)

    rect rgb(230, 255, 230)
        Note over AuthService,DB: ✅ 1회만 조회
        AuthService->>UsersRepository: findByRefreshToken(token)
        UsersRepository->>DB: SELECT * FROM users WHERE refresh_token = ?
        DB-->>UsersRepository: User
        UsersRepository-->>AuthService: Users
    end

    AuthService->>AuthService: 만료 시간 확인
    AuthService->>AuthService: createAccessToken(user.getId())

    rect rgb(230, 255, 230)
        Note over AuthService,UsersConverter: ✅ 이미 로드한 엔티티 활용
        AuthService->>UsersConverter: toDTO(user)
        UsersConverter-->>AuthService: UsersDTO
    end

    AuthService-->>AuthController: TokenResponse
    AuthController-->>Client: 200 OK + TokenResponse
```

**개선점**: User 조회 1회만 수행

---

## 5. 요약

| 시나리오 | Before (DB 쿼리) | After (DB 쿼리) | 감소 |
|----------|------------------|-----------------|------|
| login() | findByIdString 2회 + save 1~2회 | findByIdString 1회 + save 1~2회 | **1회 감소** |
| refreshAccessToken() | findByRefreshToken 1회 + findByIdString 1회 | findByRefreshToken 1회 | **1회 감소** |

**추가 개선**: AuthService에서 UsersService 의존성 제거 → 결합도 감소
