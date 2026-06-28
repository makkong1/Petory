# Step 3 — StatisticsAggregator DAU DISTINCT 집계 전환

## 목표
`StatisticsAggregator.aggregateForDate()`의 `activeUsers` 집계를 `Users.lastLoginAt`(덮어쓰기, 부정확) 기반에서 `login_events.login_at`(DISTINCT userId, 정확) 기반으로 전환한다.

## 수정 파일: `StatisticsAggregator.java`

### 변경 내용

1. `LoginEventRepository` 필드 추가 (`UsersRepository` 는 그대로 유지):
```java
private final LoginEventRepository loginEventRepository;
```

2. `aggregateForDate()` 내 `activeUsers` 집계 변경:
```java
// Before
stats.setActiveUsers(usersRepository.countByLastLoginAtBetween(start, end));

// After
stats.setActiveUsers(loginEventRepository.countDistinctUsersBetween(start, end));
```

필요한 import 추가:
```java
import com.linkup.Petory.domain.user.repository.LoginEventRepository;
```

### 변경하지 않는 것
- `usersRepository.countByLastLoginAtBetween` — 삭제. `usersRepository`는 `newUsers`, `newProviders` 집계에서 계속 사용.
- 나머지 `StatisticsAggregator` 로직은 그대로.

## WAU/MAU activeUsers 주의사항
`StatisticsScheduler.rollupWeekly()` / `rollupMonthly()`에서 `activeUsers = sum(daily.activeUsers)` 방식은 **DISTINCT 일별 합산** 으로 바뀐다.
- 진정한 주간/월간 DISTINCT는 `login_events` 를 주/월 단위로 직접 쿼리해야 하지만, 그건 별도 태스크 범위다.
- 이번 Step에서는 DAU만 수정하고, WAU/MAU는 "개선된 DAU 합산" 으로 표기를 변경하는 docs 업데이트(Step 4)로 처리한다.

## Acceptance Criteria
- `./gradlew compileJava` 성공
- 배치 실행 후 `daily_statistics.active_users`가 해당 일자의 `login_events` DISTINCT user_id 수와 일치
