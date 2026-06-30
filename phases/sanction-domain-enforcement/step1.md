# Step 1 — SUSPENDED 사용자 POST /api/reports 인증 예외 처리

## 목적

현재 `JwtAuthenticationFilter`는 SUSPENDED 사용자를 `isEnabled() = false`로 전면 차단한다. 정책상 SUSPENDED 사용자는 유효한 access token이 있는 동안 `POST /api/reports`만 예외적으로 허용해야 한다. 이 step에서는 필터와 `CustomUserDetails`를 최소한으로 수정해 이 예외를 구현한다.

## 배경 정책

- `BANNED`: `isAccountNonLocked() = false` → 항상 인증 거부
- `ACTIVE`: `isEnabled() = true` → 항상 인증 허용
- `SUSPENDED` (suspendedUntil이 미래): 기본은 인증 거부. 단 `POST /api/reports`는 예외 허용
- 만료된 SUSPENDED (suspendedUntil이 과거): 필터 수준에서도 차단. 스케줄러가 ACTIVE로 복구하면 자동 해소
- BANNED 사용자의 신고 생성은 차단한다

## 변경 파일

### 1. `CustomUserDetails.java`
경로: `backend/main/java/com/linkup/Petory/global/security/CustomUserDetails.java`

현재 필드에 `suspendedUntil` 추가, `from(Users)` 팩토리 업데이트, `isCurrentlySuspended()` 추가.

```java
// 추가할 import
import java.time.LocalDateTime;

// 추가할 필드 (기존 status 필드 바로 아래)
private final LocalDateTime suspendedUntil;

// 생성자 시그니처 변경 (private)
private CustomUserDetails(Long idx, String loginId, String password,
        Role role, Boolean emailVerified, UserStatus status, LocalDateTime suspendedUntil) {
    this.idx = idx;
    this.loginId = loginId;
    this.password = password;
    this.role = role;
    this.emailVerified = emailVerified;
    this.status = status;
    this.suspendedUntil = suspendedUntil;
    this.authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role.name()));
}

// from() 팩토리 수정
public static CustomUserDetails from(Users user) {
    return new CustomUserDetails(
            user.getIdx(),
            user.getId(),
            user.getPassword(),
            user.getRole(),
            user.getEmailVerified(),
            user.getStatus(),
            user.getSuspendedUntil());
}

// 추가할 메서드 (isAccountNonLocked() 바로 아래)
/** status=SUSPENDED이고 suspendedUntil이 현재 시각 이후인 경우 true */
public boolean isCurrentlySuspended() {
    return status == UserStatus.SUSPENDED
            && suspendedUntil != null
            && LocalDateTime.now().isBefore(suspendedUntil);
}
```

### 2. `JwtAuthenticationFilter.java`
경로: `backend/main/java/com/linkup/Petory/filter/JwtAuthenticationFilter.java`

`isUsableAccount(userDetails)` 호출부를 `isUsableAccount(userDetails, request)`로 변경하고, 메서드를 재작성한다.

```java
// doFilterInternal 내부 — 기존 호출부 수정
if (!isUsableAccount(userDetails, request)) {
    log.warn("JWT 인증 거부: 제재 또는 비활성 계정 userId={}", id);
    SecurityContextHolder.clearContext();
    filterChain.doFilter(request, response);
    return;
}

// 기존 isUsableAccount 메서드 교체
private boolean isUsableAccount(UserDetails userDetails, HttpServletRequest request) {
    if (userDetails instanceof CustomUserDetails cud) {
        if (!cud.isAccountNonLocked()) return false;   // BANNED: 항상 거부
        if (cud.isEnabled()) return true;              // ACTIVE: 항상 허용
        // SUSPENDED인 경우: POST /api/reports만 예외 허용
        if (cud.isCurrentlySuspended()) {
            return isSuspendedReportException(request);
        }
        return false;
    }
    return userDetails.isEnabled() && userDetails.isAccountNonLocked();
}

// 추가할 헬퍼 메서드
// POST /api/reports 예외: SUSPENDED 사용자가 신고를 생성할 수 있는 유일한 경로
private boolean isSuspendedReportException(HttpServletRequest request) {
    return "POST".equalsIgnoreCase(request.getMethod())
            && "/api/reports".equals(request.getServletPath());
}
```

## 주의사항

- `isCurrentlySuspended()`는 `isSanctioned()`(Users 엔티티)와 동일한 로직. 양쪽이 항상 일치하도록 유지할 것.
- `getServletPath()`는 컨텍스트 경로를 제외한 경로를 반환하므로 `/api/reports`가 정확하다. `getRequestURI()`는 쿼리스트링을 포함할 수 있으므로 사용하지 않는다.
- ReportService에서 BANNED 사용자의 신고 생성 시 별도 서비스 레벨 차단이 있어야 완전한 보호가 된다. 이 step은 필터 레벨 예외만 담당한다.

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# 수동 동작 확인 포인트 (테스트 DB 환경 있을 때):
# 1. SUSPENDED 사용자 토큰으로 GET /api/care/requests → 401 (SecurityContext 없음)
# 2. SUSPENDED 사용자 토큰으로 POST /api/reports → 인증 통과 (403은 서비스 레벨에서 별도 결정)
# 3. BANNED 사용자 토큰으로 POST /api/reports → 401
# 4. ACTIVE 사용자 토큰으로 모든 API → 기존과 동일하게 동작
```
