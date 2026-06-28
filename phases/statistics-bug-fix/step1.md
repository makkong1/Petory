# Step 1 — C2: StatisticsAggregator 빈 분리 (Self-invocation 제거)

## 목표

`StatisticsScheduler` 내에서 `aggregateStatisticsForDate`를 `this.method()` 형태로 3곳 호출하는 자기호출(self-invocation) 구조를 제거한다.
별도 스프링 빈 `StatisticsAggregator`로 분리하면, `@Transactional`이 프록시를 통해 정상 동작하고
backfill 시 날짜별 트랜잭션 독립성이 보장된다.

이 Step은 Step 2(skip→merge)의 전제 조건이다.

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|------|------|--------|
| Self-invocation C2 | `StatisticsScheduler.java:66, 220, 237` | `@Transactional` 무력화. backfill 30일치가 하나의 트랜잭션에 묶임 |
| `aggregateDailyStatistics` 직접 호출 | line 66 | 프록시 미경유 |
| `backfill` forEach 내부 호출 | line 220 | 날짜별 격리 없음 |
| `detectAndBackfillMissing` 내부 호출 | line 237 | 위와 동일 |

---

## 변경 상세

### 1. `StatisticsAggregator.java` 신규 생성

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsAggregator.java`

```java
package com.linkup.Petory.domain.statistics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 날짜별 일일 통계 집계 담당 빈. StatisticsScheduler에서 self-invocation 없이 호출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsAggregator {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;

    /**
     * 특정 날짜의 일별 통계를 집계해 저장한다. 이미 존재하면 건너뛴다.
     * (Step 2에서 skip → merge로 교체 예정)
     */
    @Transactional
    public void aggregateForDate(LocalDate date) {
        if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
            log.warn("이미 {}의 통계가 존재합니다. 건너뜁니다.", date);
            return;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long completed = careRequestRepository.countByCompletedAtBetween(start, end);
        long cancelled = careRequestRepository.countByStatusAndUpdatedAtBetween(CareRequestStatus.CANCELLED, start, end);

        DailyStatistics stats = DailyStatistics.builder()
                .statDate(date)
                .newUsers(usersRepository.countByCreatedAtBetween(start, end))
                .activeUsers(usersRepository.countByLastLoginAtBetween(start, end))
                .newProviders(usersRepository.countByRoleAndCreatedAtBetween(Role.SERVICE_PROVIDER, start, end))
                .newCareRequests(careRequestRepository.countByCreatedAtBetween(start, end))
                .completedCares(completed)
                .cancelledCares(cancelled)
                .careCompletionRate(calcRate(completed, completed + cancelled))
                .totalRevenue(BigDecimal.ZERO)
                .transactionCount(0L)
                .avgTransaction(BigDecimal.ZERO)
                .newPosts(boardRepository.countByCreatedAtBetween(start, end))
                .newMeetups(meetupRepository.countByCreatedAtBetween(start, end))
                .meetupParticipants(meetupParticipantsRepository.countByJoinedAtBetween(start, end))
                .newReports(reportRepository.countByCreatedAtBetween(start, end))
                .resolvedReports(reportRepository.countByStatusAndUpdatedAtBetween(ReportStatus.RESOLVED, start, end))
                .build();

        dailyStatisticsRepository.save(stats);
        log.info("일일 통계 집계 완료: {}", date);
    }

    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }
}
```

---

### 2. `StatisticsScheduler.java` 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsScheduler.java`

#### 2-1. 필드 교체

제거할 필드 (6개):
```java
private final UsersRepository usersRepository;
private final BoardRepository boardRepository;
private final CareRequestRepository careRequestRepository;
private final MeetupRepository meetupRepository;
private final MeetupParticipantsRepository meetupParticipantsRepository;
private final ReportRepository reportRepository;
```

추가할 필드 (1개):
```java
private final StatisticsAggregator statisticsAggregator;
```

#### 2-2. `aggregateStatisticsForDate` 메서드 전체 삭제

삭제 범위: `StatisticsScheduler.java` 의 `aggregateStatisticsForDate` 메서드 전체 (lines 79–117).

#### 2-3. `aggregateStatisticsForDate` 호출 → `statisticsAggregator.aggregateForDate` 교체

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| `aggregateDailyStatistics():66` | `aggregateStatisticsForDate(yesterday);` | `statisticsAggregator.aggregateForDate(yesterday);` |
| `backfill():220` | `aggregateStatisticsForDate(date);` | `statisticsAggregator.aggregateForDate(date);` |
| `detectAndBackfillMissing():237` | `aggregateStatisticsForDate(date);` | `statisticsAggregator.aggregateForDate(date);` |

#### 2-4. `public void aggregateStatisticsForDate` 시그니처 제거 후 import 정리

제거할 import:
```java
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import java.time.LocalTime;
```

> `java.time.LocalTime`은 `aggregateStatisticsForDate` 에서만 사용하므로 함께 제거.

---

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# StatisticsAggregator 빈이 주입되는지 확인 (애플리케이션 기동 테스트)
./gradlew bootRun --args='--spring.profiles.active=dev' &
sleep 15
curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"' && echo "OK" || echo "FAIL"
kill %1
```

---

## 주의

- `calcRate`, `calcAvgTransaction`은 `StatisticsScheduler`의 `rollupWeekly`, `rollupMonthly`에서도 사용하므로 `StatisticsScheduler`에서 **삭제하지 않는다**.
- `StatisticsAggregator`는 자체 `calcRate`를 private으로 보유한다 (2행 중복, 허용).
- Step 2에서 `aggregateForDate`의 skip 로직이 merge로 교체되므로, 이 Step은 로직 이동만 수행한다.
