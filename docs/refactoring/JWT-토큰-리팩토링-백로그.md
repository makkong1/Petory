# JWT·토큰 리팩토링 백로그

인증·JWT 경로를 손볼 때 참고할 **현재 문제 → 코드 팩트 → 리스크 → 검토 방향** 정리다. 아래 **진행 상태** 체크는 레포 기준으로 유지한다(코드 반영 시 함께 갱신).

## 진행 상태 요약

| 항목 | 코드 반영 |
|------|-----------|
| [ ] **1.** Access JWT ↔ `UserStatus` 매요청 재평가 | 미반영 |
| [x] **2.** Access TTL 단일화 (`jwt.access-token-expiration-ms`), `generateToken`·`jwt.expiration` 제거 | 반영 |
| [ ] **2a.** Refresh·이메일 JWT TTL 프로퍼티화 | 미반영 (§2 남은 과제) |
| [ ] **2b.** 세션용 vs 메일용 서명키 분리 | 미반영 (§2 남은 과제) |
| [ ] **3.** Refresh 회전·디바이스별 세션 등 | 미반영 |
| [ ] **4.** OAuth/SSE/WebSocket URL 토큰 축소·대체 | 미반영 |
| [ ] **5.** JWT 검증 실패 로그 레벨 정리 | 미반영 |

User 도메인 전체 개요·짧은 갭 목록은 **`user-domain-architecture.md` §8** 과 함께 읽는다.

---

## `user-domain-architecture.md` §8 항목과의 대응

| 본 문서 한 줄 주제 | §8 | 코드 반영 |
|-------------------|-----|-----------|
| Access JWT와 계정 상태 분리 | 1 | [ ] |
| 토큰이 URL로 전달(OAuth 쿼리 등) | 3 | [ ] |
| `JwtAuthenticationFilter`의 쿼리 `token`(SSE 등) | 4 | [ ] |
| Access TTL·레거시 `generateToken` 정리 | 8 | [x] |

§8의 나머지(HTTP·CORS·actuator·OAuth 로그 등)는 JWT 백로그 범위 밖이다.

---

## 백로그 (문제점 정리)

### 1. Access JWT와 계정 상태 분리 `[ ]`

- **코드·동작**: `JwtAuthenticationFilter`는 서명·만료 검증 후 `UserDetailsService.loadUserByUsername`만 호출한다. **`Users.status`(BANNED/SUSPENDED)는 매 요청 재평가하지 않으며**, 로그인·OAuth 시점 검사에 의존한다.
- **리스크**: 제재·차단 직후에도 Access 만료 전까지 API 호출 가능. 제재의 “즉시 반영”은 **TTL에 의존**한다.
- **검토 방향**: Access TTL을 유지할 거면 **블랙리스트(jti)·토큰 버전 클레임·더 짧은 TTL** 등을 검토하거나, **민감 엔드포인트만** DB 스팟 체크.

### 2. `JwtUtil` Access TTL · 레거시 `generateToken` `[x]`

**코드에 반영된 것**

- [x] 호출처 없던 **`generateToken` 삭제**
- [x] **`jwt.expiration`** 프로퍼티 의존 제거
- [x] Access 만료 **`jwt.access-token-expiration-ms`** (밀리초, 기본 **`900000`** = 15분), `JwtUtil#createAccessToken`

**아직 남음**

- [ ] Refresh·이메일 인증 JWT TTL — 여전히 `JwtUtil` **상수** (프로퍼티화 검토)
- [ ] **`jwt.secret` 하나로 세션용·메일용 JWT 서명** — 키 분리는 위협 모델 보고 별도 결정

### 3. Refresh 설계 `[ ]`

- **코드·동작**: DB에 사용자당 Refresh 문자열 1개. `/api/auth/refresh`는 **Access만 재발급**, Refresh **회전 없음**.
- **리스크**: Refresh 탈취 시 유효 기간 동안 반복 악용 가능성. 다중 디바이스는 후발 로그인이 이전 Refresh를 덮어쓴다.
- **검토 방향**: **회전·재사용 탐지·디바이스별 행** 등은 제품 요구와 트레이드오프 정리 후 적용.

### 4. 토큰이 URL에 실림 `[ ]`

- **코드·동작**: OAuth 성공 후 프론트로 **`accessToken`·`refreshToken` 쿼리**(§8 항 3). SSE는 `EventSource` 제약으로 **`/api/notifications/stream?token=`**(§8 항 4). WebSocket은 쿼리 `token`을 별도 처리(`WebSocketAuthenticationInterceptor` 등).
- **리스크**: 브라우저 기록·Referer·**중간 프록시·액세스 로그** 등으로 토큰 노출 표면이 넓다.
- **검토 방향**: OAuth는 **코드+백엔드 교환** 등으로 쿼리 장기 토큰 제거. SSE는 **짧은 수명 스트림 전용 토큰**·무효화·**Cookie**(same-site) 가능 여부 등 검토.

### 5. 검증 실패 로깅 `[ ]`

- **코드·동작**: `JwtUtil.validateToken` 실패 시 **`log.error`**. `JwtAuthenticationFilter`는 **`try/catch`** 로 예외 시 **`log.error`** + `SecurityContext` 클리어. 만료·형식 오류도 error로 쌓일 수 있다.
- **리스크**: 로그 노이즈·알람 피로.
- **검토 방향**: 만료/형식 등 **유형별 로그 레벨**(debug/warn)·메시지 축소.

---

## 관련 코드·프론트 (추적용)

| 구분 | 경로 |
|------|------|
| Access/Refresh·메일 JWT 헬퍼 | `backend/main/java/com/linkup/Petory/util/JwtUtil.java` |
| Bearer + 쿼리 `token` | `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java` |
| 로그인·Refresh | `backend/main/java/com/linkup/Petory/domain/user/service/AuthService.java` |
| OAuth Access 발급 | `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java` |
| SSE 클라이언트 | `frontend/src/components/Layout/Navigation.js` (`EventSource` + `token`) |

코드 변경 후에는 **`user-domain-architecture.md` §8** 과 본 문서를 함께 갱신한다.
