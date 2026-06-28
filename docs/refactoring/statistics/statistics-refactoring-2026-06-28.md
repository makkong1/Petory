# 통계 도메인 리팩토링 기록 (2026-06-28)

리뷰 문서(`statistics-domain-review-2026-06-28.md`)에서 발견한 Critical/Warning 버그 5종을 수정한 작업 기록.  
브랜치: `feat-statistics-bug-fix`

---

## 1. C2 — Self-invocation으로 @Transactional 무력화

### 문제
`StatisticsScheduler.aggregateStatisticsForDate()`를 같은 클래스 내 `backfill()`, `detectAndBackfillMissing()`에서 `this.method()` 형태로 호출.  
Spring AOP 프록시를 타지 않아 `@Transactional`이 적용되지 않음 → 날짜별 트랜잭션 격리 없음.

### Before
```java
// StatisticsScheduler.java
private void detectAndBackfillMissing(LocalDate yesterday) {
    // ...
    aggregateStatisticsForDate(date); // self-invocation
}
```

### After
```java
// StatisticsAggregator.java (신규 빈)
@Transactional
public void aggregateForDate(LocalDate date) { ... }

// StatisticsScheduler.java
private final StatisticsAggregator statisticsAggregator;

private void detectAndBackfillMissing(LocalDate yesterday) {
    statisticsAggregator.aggregateForDate(date); // 크로스-빈 호출
}
```

---

## 2. C1 — 결제-배치 충돌로 활동 지표 영구 유실

### 문제
`recordPayment()`가 결제일 당일 `DailyStatistics` row를 생성 → 다음날 배치가 "이미 row 있음"으로 판단해 skip → 활동 지표(`newUsers`, `activeUsers` 등) 영구 0.

### Before (skip 방식)
```java
// aggregateStatisticsForDate
if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
    return; // skip
}
```

### After (merge 방식)
```java
// StatisticsAggregator.aggregateForDate
DailyStatistics stats = dailyStatisticsRepository.findByStatDate(date)
        .orElse(DailyStatistics.builder().statDate(date).build());

boolean hasPaymentData = stats.getTransactionCount() != null && stats.getTransactionCount() > 0;

// 활동 지표는 항상 덮어쓰기
stats.setNewUsers(...);
stats.setActiveUsers(...);
// ...

// 결제 데이터가 없을 때만 매출 필드 초기화
if (!hasPaymentData) {
    stats.setTotalRevenue(BigDecimal.ZERO);
    stats.setTransactionCount(0L);
    stats.setAvgTransaction(BigDecimal.ZERO);
}
dailyStatisticsRepository.save(stats);
```

---

## 3. C3 — recordPayment 레이스 컨디션

### 문제
동시 결제 요청이 같은 날짜 row를 동시에 읽어 `totalRevenue`를 누적하면, 나중에 저장된 쪽이 앞선 결과를 덮어써서 매출이 유실됨.

### Before
```java
DailyStatistics stats = dailyStatisticsRepository.findByStatDate(today)
        .orElse(DailyStatistics.builder().statDate(today).build());
// 락 없이 read-modify-write
```

### After
```java
// 비관적 락으로 조회 (SELECT ... FOR UPDATE)
DailyStatistics stats = dailyStatisticsRepository.findByStatDateForUpdate(date)
        .orElse(DailyStatistics.builder().statDate(date).build());

// 동시 INSERT 경합 시 재시도
try {
    applyPayment(today, amount);
} catch (DataIntegrityViolationException e) {
    applyPayment(today, amount); // 1회 재시도
}
```

추가한 Repository 메서드:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM DailyStatistics d WHERE d.statDate = :date")
Optional<DailyStatistics> findByStatDateForUpdate(@Param("date") LocalDate date);
```

---

## 4. W2 — ISO 53주차 하드코딩

### 문제
`calcWeeklyRetention`에서 연초(1주차) retention 계산 시 직전 주를 `prevWeek = 52`로 하드코딩.  
2020, 2026년 등 53주차가 있는 연도에서 마지막 주 retention 오동작.

### Before
```java
if (prevWeek == 0) {
    prevYear--;
    prevWeek = 52; // 하드코딩
}
```

### After
```java
if (prevWeek == 0) {
    prevYear--;
    // 해당 연도 12월 28일은 항상 마지막 ISO 주차에 속한다 (ISO 8601 정의)
    prevWeek = LocalDate.of(prevYear, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
}
```

---

## 5. C0 임시 완화 — cron 00:05 변경

### 문제
배치가 18:00에 실행되므로 Day N에 로그인한 사용자가 Day N+1 00:00~18:00 사이에 다시 로그인하면 `lastLoginAt`이 덮어써져 Day N DAU에서 누락됨.

### After (임시 완화)
- cron `0 0 18 * * ?` → `0 5 0 * * ?` (자정 직후 실행)
- 재로그인 race window: 18시간 → 약 0분(자정 이후 첫 로그인 시 이미 다음날 집계됨)

**근본 수정**: `statistics-login-events` 태스크 (아래 섹션 6).

---

## 6. C0 근본 수정 — login_events 테이블 도입

### 문제 (근본 원인)
`Users.lastLoginAt`은 로그인마다 덮어쓰이는 단일 컬럼. append 이력이 없어 특정 날짜에 접속했는지 역산 불가.

### 해결

**신규 엔티티** `LoginEvent` (`login_events` 테이블):

| 컬럼 | 설명 |
|---|---|
| `id` | PK |
| `user_id` | FK → Users |
| `login_at` | 로그인 시각 |
| `login_method` | LOCAL / GOOGLE / NAVER / KAKAO |

인덱스: `(user_id, login_at)`, `(login_at)`

**로그인 진입점 2곳에 append 추가**:

```java
// AuthService.login()
loginEventRepository.save(LoginEvent.builder()
        .user(user).loginAt(LocalDateTime.now()).loginMethod("LOCAL").build());

// OAuth2Service.processOAuth2Login()
loginEventRepository.save(LoginEvent.builder()
        .user(user).loginAt(LocalDateTime.now()).loginMethod(provider.name()).build());
```

**DAU 집계 전환**:

```java
// Before
stats.setActiveUsers(usersRepository.countByLastLoginAtBetween(start, end));

// After
stats.setActiveUsers(loginEventRepository.countDistinctUsersBetween(start, end));
// → SELECT COUNT(DISTINCT user_id) FROM login_events WHERE login_at BETWEEN :start AND :end
```

### 보정 불가 범위
도입 이전(~ 2026-06-27) `daily_statistics.active_users`는 보정 불가.  
`lastLoginAt`은 마지막 로그인 시각만 남아 이전 로그인 이력 역산 불가.

---

## 개선 효과 요약

| 항목 | Before | After |
|---|---|---|
| 결제일 활동 지표 | 영구 0 (skip) | 정상 집계 (merge) |
| 트랜잭션 격리 | 무력화 (self-invocation) | 정상 적용 (별도 빈) |
| 동시 결제 안전성 | 매출 유실 가능 | 비관적 락 보호 |
| DAU 정확도 | 재로그인 사용자 누락 | DISTINCT 이벤트 집계 |
| ISO 53주차 retention | 오동작 | 동적 계산 |
| 배치 실행 시각 | 18:00 (race window 18h) | 00:05 (window 최소화) |

## 잔존 과제

- WAU/MAU `activeUsers`는 여전히 DAU 합산 (DISTINCT 아님) → `statistics-wau-mau-distinct` 태스크
- 과거 통계 `active_users` 과소 집계 보정 불가 → 관리자 UI "추정값" 레이블 권장
