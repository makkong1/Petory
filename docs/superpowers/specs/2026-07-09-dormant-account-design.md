# 휴면 계정(Dormant Account) 설계

**날짜:** 2026-07-09
**범위:** 1년 미로그인 사용자 자동 휴면 전환 + 본인 재활성화
**대상:** Petory 백엔드 (Spring Boot 3.5.7, Java 17) + 프론트엔드 (React 19)

---

## 배경

- 탈퇴(WITHDRAWN, `isDeleted`)와 별개로, 그냥 오래 로그인하지 않은 사용자를 위한 "휴면" 상태가 없었음.
- `Users.lastLoginAt`은 `AuthService.login()` / `OAuth2Service`에서 이미 매 로그인마다 갱신되고 있음 (죽은 필드 아님 — 설계 전 확인 완료).
- `login_events` 테이블(append-only)에 로그인 이력이 이미 쌓이고 있어 별도 로그 테이블 불필요.
- 기존 `UserStatus`(ACTIVE/SUSPENDED/BANNED)는 제재 전용이라 휴면과 섞으면 "정지 중이면서 휴면"을 표현 못 함 → 별도 필드로 분리.

## 목표

- 1년간 로그인하지 않은 사용자를 매일 자정 배치로 자동 휴면 전환
- 휴면 계정은 일반 로그인(아이디/비밀번호) 시도 시 차단하고, 본인이 그 자리에서 확인만 하면 즉시 재활성화
- 관리자는 휴면 여부를 조회만 가능 (관리자가 대신 재활성화 불가 — 본인 로그인으로만 해제)

## 비범위

- OAuth2(Google/Naver) 로그인의 휴면 차단/재활성화 — 추후 별도 확장
- 휴면 계정 콘텐츠 필터링 — 탈퇴/밴과 달리 휴면은 잘못한 게 없으므로 콘텐츠는 그대로 노출 (기존 `status='ACTIVE'` 조건에 영향 없음, 별도 작업 불필요)
- 관리자의 강제 재활성화 기능

---

## 데이터 모델

`Users` 엔티티에 필드 2개 추가 (`isDeleted`/`deletedAt`과 동일한 패턴):

```java
@Column(name = "is_dormant")
@Builder.Default
private Boolean isDormant = false;

@Column(name = "dormant_at")
private LocalDateTime dormantAt;
```

DB 마이그레이션 (schema 변경, `application.properties`의 `ddl-auto` 정책에 따름):

```sql
ALTER TABLE users
ADD COLUMN is_dormant TINYINT(1) DEFAULT 0 AFTER deleted_at,
ADD COLUMN dormant_at DATETIME NULL AFTER is_dormant;
```

## 배치: 휴면 전환

`domain/user/scheduler/UserDormantScheduler.java` 신설 (`UserSanctionScheduler`와 동일 패턴):

```java
@Scheduled(cron = "0 0 0 * * *") // 매일 자정
public void markDormantUsers() {
    userDormantService.markDormantUsers();
}
```

서비스는 벌크 UPDATE 1방으로 처리 (엔티티 로드 없이 원자적 업데이트):

```java
@Modifying
@Query("UPDATE Users u SET u.isDormant = true, u.dormantAt = :now " +
       "WHERE u.isDormant = false AND u.isDeleted = false AND (" +
       "  (u.lastLoginAt IS NOT NULL AND u.lastLoginAt < :cutoff) OR " +
       "  (u.lastLoginAt IS NULL AND u.createdAt < :cutoff)" +
       ")")
int markDormantUsers(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
```

- `cutoff = now.minusYears(1)`
- 가입 후 한 번도 로그인 안 한 계정은 `createdAt` 기준으로 판정 (`lastLoginAt`이 null이므로).
- 탈퇴(`isDeleted=true`) 계정은 대상에서 제외.

## 로그인 차단 + 재활성화

새 엔드포인트를 만들지 않고 기존 `POST /api/auth/login`에 필드 하나만 추가:

```java
public record LoginRequest(
    @NotBlank String id,
    @NotBlank String password,
    boolean confirmReactivate  // 기본값 false
) {}
```

`AuthService.login()` 흐름 (BANNED/SUSPENDED 체크 다음에 삽입):

```java
if (Boolean.TRUE.equals(user.getIsDormant())) {
    if (!confirmReactivate) {
        throw new UserDormantException();
    }
    user.setIsDormant(false);
    user.setDormantAt(null);
    log.info("휴면 계정 재활성화: {}", id);
}
// 이후 기존 토큰 발급 로직 그대로 진행
```

- `UserDormantException extends ApiException` (HTTP 403, `errorCode = "USER_DORMANT"`) — `UserBannedException`/`UserSuspendedException`과 동일 패턴, `GlobalExceptionHandler`가 이미 `ApiException`을 일괄 처리하므로 핸들러 추가 불필요.
- 비밀번호는 컨트롤러의 `authenticationManager.authenticate()`에서 이미 검증되므로, 재활성화 시 추가 인증 절차 없이 바로 진행.
- 범위: `AuthService.login()`만 수정. `OAuth2Service`, `refreshAccessToken()`은 대상 아님 (refresh token은 1일 TTL이라 1년 휴면 시점엔 이미 만료돼 있어 사실상 도달 불가).

## 프론트엔드

- `authApi.login(id, password, confirmReactivate = false)` — 요청 바디에 `confirmReactivate` 포함
- `LoginForm.js`: `catch` 블록에서 `error.response?.data?.errorCode === 'USER_DORMANT'` 감지 → `window.confirm('장기간 미접속으로 휴면 처리된 계정입니다. 재활성화하시겠습니까?')` → 확인 시 `login(id, password, true)` 재호출
- `UsersDTO`에 `isDormant`, `dormantAt` 추가
- `UserList.js`: "휴면" 컬럼 추가 (표시 전용, 액션 버튼 없음)

## 테스트 계획

- `UserDormantServiceTest`: 벌크 업데이트 조건 검증 (1년 경과/미경과, `lastLoginAt` null/not-null, 이미 삭제된 계정 제외)
- `AuthServiceTest` (신규 또는 기존 로그인 테스트 확장): 휴면 계정 로그인 시 `UserDormantException` 발생, `confirmReactivate=true` 시 로그인 성공 + `isDormant` 해제 검증
- `UserDormantSchedulerTest`: 스케줄러가 서비스 메서드를 호출하는지 (Mockito)

## 문서 업데이트 (구현 완료 후)

- `docs/domains/user.md` — 엔티티 필드, API(`/api/auth/login` 요청 필드 추가), 휴면 배치 설명
- `docs/architecture/user/사용자 인증 및 프로필 아키텍처.md` — 로그인 시퀀스에 휴면 체크 단계 추가
- `docs/domain-page-drafts/user-domain-v2-content.md` (Petory) 및 동일 파일을 `makkong1-github.io` 저장소에도 동기화
