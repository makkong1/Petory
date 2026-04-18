# 통계 도메인 재설계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** DailyStatistics 단일 테이블을 Daily/Weekly/Monthly 3단계 집계 구조로 재설계하고, 비율 지표·매출·사용자 세그먼트를 추가하여 운영 모니터링 + 비즈니스 의사결정을 지원한다.

**Architecture:** 엔티티 3개(DailyStatistics, WeeklyStatistics, MonthlyStatistics) + Repository 어댑터 패턴 유지. 배치(매일 18:00)로 daily 집계 후 주/월 말에 rollup. 매출은 PetCoinEscrowService에서 이벤트 즉시 반영. 통계 API 전체 MASTER 전용.

**Tech Stack:** Spring Boot 3.5.7 / Java 17 / JPA (Hibernate) / MySQL 8.0 / Redis (1분 캐시) / JUnit 5 + Mockito

---

## File Map

| 파일 | 변경 유형 |
|------|---------|
| `domain/statistics/entity/DailyStatistics.java` | 수정 (Integer→Long, 컬럼 추가) |
| `domain/statistics/entity/WeeklyStatistics.java` | 신규 |
| `domain/statistics/entity/MonthlyStatistics.java` | 신규 |
| `domain/statistics/repository/DailyStatisticsRepository.java` | 수정 (쿼리 추가) |
| `domain/statistics/repository/JpaDailyStatisticsAdapter.java` | 수정 |
| `domain/statistics/repository/SpringDataJpaDailyStatisticsRepository.java` | 수정 |
| `domain/statistics/repository/WeeklyStatisticsRepository.java` | 신규 |
| `domain/statistics/repository/JpaWeeklyStatisticsAdapter.java` | 신규 |
| `domain/statistics/repository/SpringDataJpaWeeklyStatisticsRepository.java` | 신규 |
| `domain/statistics/repository/MonthlyStatisticsRepository.java` | 신규 |
| `domain/statistics/repository/JpaMonthlyStatisticsAdapter.java` | 신규 |
| `domain/statistics/repository/SpringDataJpaMonthlyStatisticsRepository.java` | 신규 |
| `domain/statistics/dto/DailyStatisticsResponse.java` | 신규 |
| `domain/statistics/dto/WeeklyStatisticsResponse.java` | 신규 |
| `domain/statistics/dto/MonthlyStatisticsResponse.java` | 신규 |
| `domain/statistics/dto/TodaySnapshotResponse.java` | 신규 |
| `domain/statistics/service/StatisticsScheduler.java` | 수정 (롤업 + 누락 감지) |
| `domain/statistics/service/StatisticsService.java` | 수정 (주/월 조회 + recordPayment) |
| `domain/admin/controller/AdminStatisticsController.java` | 수정 (MASTER 통일, 엔드포인트 추가) |
| `domain/care/repository/CareRequestRepository.java` | 수정 (countByStatus 추가) |
| `domain/care/repository/JpaCareRequestAdapter.java` | 수정 |
| `domain/care/repository/SpringDataJpaCareRequestRepository.java` | 수정 |
| `domain/report/repository/ReportRepository.java` | 수정 (countByStatus 추가) |
| `domain/report/repository/JpaReportAdapter.java` | 수정 |
| `domain/report/repository/SpringDataJpaReportRepository.java` | 수정 |
| `domain/user/repository/UsersRepository.java` | 수정 (countByRole 추가) |
| `domain/user/repository/JpaUsersAdapter.java` | 수정 |
| `domain/user/repository/SpringDataJpaUsersRepository.java` | 수정 |
| `domain/payment/service/PetCoinEscrowService.java` | 수정 (recordPayment 호출) |

> 이하 모든 경로는 `backend/main/java/com/linkup/Petory/` 기준

---

## Task 1: Repository 쿼리 추가 (Care / Report / Users)

배치 집계에 필요한 신규 지표용 쿼리를 먼저 추가한다.

**Files:**
- Modify: `domain/care/repository/CareRequestRepository.java`
- Modify: `domain/care/repository/JpaCareRequestAdapter.java`
- Modify: `domain/care/repository/SpringDataJpaCareRequestRepository.java`
- Modify: `domain/report/repository/ReportRepository.java`
- Modify: `domain/report/repository/JpaReportAdapter.java`
- Modify: `domain/report/repository/SpringDataJpaReportRepository.java`
- Modify: `domain/user/repository/UsersRepository.java`
- Modify: `domain/user/repository/JpaUsersAdapter.java` (또는 동일 패턴의 어댑터)
- Modify: `domain/user/repository/SpringDataJpaUsersRepository.java`
- Test: `backend/test/java/com/linkup/Petory/domain/statistics/StatisticsRepositoryQueryTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// backend/test/java/com/linkup/Petory/domain/statistics/StatisticsRepositoryQueryTest.java
package com.linkup.Petory.domain.statistics;

import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StatisticsRepositoryQueryTest {

    @Autowired CareRequestRepository careRequestRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired UsersRepository usersRepository;

    @Test
    void countCancelledCares_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = careRequestRepository.countByStatusAndUpdatedAtBetween(
                CareRequestStatus.CANCELLED, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countResolvedReports_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = reportRepository.countByStatusAndUpdatedAtBetween(
                ReportStatus.RESOLVED, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    void countNewProviders_shouldReturnLong() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        long count = usersRepository.countByRoleAndCreatedAtBetween(
                Role.SERVICE_PROVIDER, start, end);
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsRepositoryQueryTest" 2>&1 | tail -20
```
Expected: FAIL (메서드 미존재)

- [ ] **Step 3: CareRequestRepository에 메서드 추가**

`domain/care/repository/CareRequestRepository.java` — 기존 메서드 아래에 추가:
```java
long countByStatusAndUpdatedAtBetween(CareRequestStatus status, LocalDateTime start, LocalDateTime end);
```

`domain/care/repository/SpringDataJpaCareRequestRepository.java` — 추가:
```java
long countByStatusAndUpdatedAtBetween(CareRequestStatus status, LocalDateTime start, LocalDateTime end);
```

`domain/care/repository/JpaCareRequestAdapter.java` — 추가:
```java
@Override
public long countByStatusAndUpdatedAtBetween(CareRequestStatus status, LocalDateTime start, LocalDateTime end) {
    return jpaRepository.countByStatusAndUpdatedAtBetween(status, start, end);
}
```

- [ ] **Step 4: ReportRepository에 메서드 추가**

`domain/report/repository/ReportRepository.java` — 추가:
```java
long countByStatusAndUpdatedAtBetween(ReportStatus status, LocalDateTime start, LocalDateTime end);
```

`domain/report/repository/SpringDataJpaReportRepository.java` — 추가:
```java
long countByStatusAndUpdatedAtBetween(ReportStatus status, LocalDateTime start, LocalDateTime end);
```

`domain/report/repository/JpaReportAdapter.java` — 추가:
```java
@Override
public long countByStatusAndUpdatedAtBetween(ReportStatus status, LocalDateTime start, LocalDateTime end) {
    return jpaRepository.countByStatusAndUpdatedAtBetween(status, start, end);
}
```

- [ ] **Step 5: UsersRepository에 메서드 추가**

`domain/user/repository/UsersRepository.java` — 추가:
```java
long countByRoleAndCreatedAtBetween(Role role, LocalDateTime start, LocalDateTime end);
```

`domain/user/repository/SpringDataJpaUsersRepository.java` — 추가:
```java
long countByRoleAndCreatedAtBetween(Role role, LocalDateTime start, LocalDateTime end);
```

`domain/user/repository/JpaUsersAdapter.java` (또는 동일 패턴 어댑터) — 추가:
```java
@Override
public long countByRoleAndCreatedAtBetween(Role role, LocalDateTime start, LocalDateTime end) {
    return jpaRepository.countByRoleAndCreatedAtBetween(role, start, end);
}
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsRepositoryQueryTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 7: 컴파일 확인 후 커밋**

```bash
./gradlew compileJava 2>&1 | tail -10
git add backend/main/java/com/linkup/Petory/domain/care/repository/ \
        backend/main/java/com/linkup/Petory/domain/report/repository/ \
        backend/main/java/com/linkup/Petory/domain/user/repository/ \
        backend/test/java/com/linkup/Petory/domain/statistics/
git commit -m "feat(statistics): 집계용 쿼리 추가 (취소 케어, 처리 신고, 신규 제공자)"
```

---

## Task 2: DailyStatistics 엔티티 재설계

Integer → Long, 신규 컬럼 추가.

**Files:**
- Modify: `domain/statistics/entity/DailyStatistics.java`
- Modify: `domain/statistics/repository/DailyStatisticsRepository.java`
- Modify: `domain/statistics/repository/JpaDailyStatisticsAdapter.java`
- Modify: `domain/statistics/repository/SpringDataJpaDailyStatisticsRepository.java`

- [ ] **Step 1: DailyStatistics 엔티티 전체 교체**

`domain/statistics/entity/DailyStatistics.java` 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.statistics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_statistics")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DailyStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", unique = true, nullable = false)
    private LocalDate statDate;

    // 사용자
    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;

    // 케어
    @Builder.Default @Column(name = "new_care_requests") private Long newCareRequests = 0L;
    @Builder.Default @Column(name = "completed_cares") private Long completedCares = 0L;
    @Builder.Default @Column(name = "cancelled_cares") private Long cancelledCares = 0L;
    @Builder.Default @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    // 결제 (이벤트 즉시 반영)
    @Builder.Default @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default @Column(name = "transaction_count") private Long transactionCount = 0L;
    @Builder.Default @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    // 커뮤니티
    @Builder.Default @Column(name = "new_posts") private Long newPosts = 0L;
    @Builder.Default @Column(name = "new_meetups") private Long newMeetups = 0L;
    @Builder.Default @Column(name = "meetup_participants") private Long meetupParticipants = 0L;

    // 운영
    @Builder.Default @Column(name = "new_reports") private Long newReports = 0L;
    @Builder.Default @Column(name = "resolved_reports") private Long resolvedReports = 0L;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 2: DailyStatisticsRepository에 누락 감지 + 만료 삭제 쿼리 추가**

`domain/statistics/repository/DailyStatisticsRepository.java`:
```java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatisticsRepository {
    DailyStatistics save(DailyStatistics dailyStatistics);
    Optional<DailyStatistics> findById(Long id);
    void delete(DailyStatistics dailyStatistics);
    void deleteById(Long id);
    Optional<DailyStatistics> findByStatDate(LocalDate statDate);
    List<DailyStatistics> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
    List<LocalDate> findStatDatesByDateRange(LocalDate startDate, LocalDate endDate);
    void deleteByStatDateBefore(LocalDate cutoffDate);
}
```

- [ ] **Step 3: SpringDataJpaDailyStatisticsRepository 업데이트**

기존 파일에 아래 두 메서드 추가:
```java
@Query("SELECT d.statDate FROM DailyStatistics d WHERE d.statDate BETWEEN :startDate AND :endDate")
List<LocalDate> findStatDatesByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

void deleteByStatDateBefore(LocalDate cutoffDate);
```
파일 상단에 import 추가:
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
```

- [ ] **Step 4: JpaDailyStatisticsAdapter 업데이트**

기존 파일에 추가:
```java
@Override
public List<LocalDate> findStatDatesByDateRange(LocalDate startDate, LocalDate endDate) {
    return jpaRepository.findStatDatesByDateRange(startDate, endDate);
}

@Override
public void deleteByStatDateBefore(LocalDate cutoffDate) {
    jpaRepository.deleteByStatDateBefore(cutoffDate);
}
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/statistics/entity/DailyStatistics.java \
        backend/main/java/com/linkup/Petory/domain/statistics/repository/
git commit -m "feat(statistics): DailyStatistics 엔티티 재설계 (Integer→Long, 비율/매출 컬럼 추가)"
```

---

## Task 3: WeeklyStatistics 엔티티 + Repository

**Files:**
- Create: `domain/statistics/entity/WeeklyStatistics.java`
- Create: `domain/statistics/repository/WeeklyStatisticsRepository.java`
- Create: `domain/statistics/repository/SpringDataJpaWeeklyStatisticsRepository.java`
- Create: `domain/statistics/repository/JpaWeeklyStatisticsAdapter.java`

- [ ] **Step 1: WeeklyStatistics 엔티티 생성**

```java
// domain/statistics/entity/WeeklyStatistics.java
package com.linkup.Petory.domain.statistics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_statistics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"year", "week_number"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class WeeklyStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false) private Integer year;
    @Column(name = "week_number", nullable = false) private Integer weekNumber;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;

    // 사용자
    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;
    @Builder.Default @Column(name = "weekly_retention_rate", precision = 5, scale = 2)
    private BigDecimal weeklyRetentionRate = BigDecimal.ZERO;

    // 케어
    @Builder.Default @Column(name = "new_care_requests") private Long newCareRequests = 0L;
    @Builder.Default @Column(name = "completed_cares") private Long completedCares = 0L;
    @Builder.Default @Column(name = "cancelled_cares") private Long cancelledCares = 0L;
    @Builder.Default @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    // 결제
    @Builder.Default @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default @Column(name = "transaction_count") private Long transactionCount = 0L;
    @Builder.Default @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    // 커뮤니티
    @Builder.Default @Column(name = "new_posts") private Long newPosts = 0L;
    @Builder.Default @Column(name = "new_meetups") private Long newMeetups = 0L;
    @Builder.Default @Column(name = "meetup_participants") private Long meetupParticipants = 0L;

    // 운영
    @Builder.Default @Column(name = "new_reports") private Long newReports = 0L;
    @Builder.Default @Column(name = "resolved_reports") private Long resolvedReports = 0L;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 2: WeeklyStatisticsRepository 인터페이스 생성**

```java
// domain/statistics/repository/WeeklyStatisticsRepository.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import java.util.List;
import java.util.Optional;

public interface WeeklyStatisticsRepository {
    WeeklyStatistics save(WeeklyStatistics weeklyStatistics);
    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);
    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);
    List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(int startYear, int startWeek, int endYear, int endWeek);
}
```

- [ ] **Step 3: SpringDataJpaWeeklyStatisticsRepository 생성**

```java
// domain/statistics/repository/SpringDataJpaWeeklyStatisticsRepository.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SpringDataJpaWeeklyStatisticsRepository extends JpaRepository<WeeklyStatistics, Long> {
    Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber);
    List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year);

    @Query("SELECT w FROM WeeklyStatistics w WHERE (w.year > :startYear OR (w.year = :startYear AND w.weekNumber >= :startWeek)) AND (w.year < :endYear OR (w.year = :endYear AND w.weekNumber <= :endWeek)) ORDER BY w.year ASC, w.weekNumber ASC")
    List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(
            @Param("startYear") int startYear, @Param("startWeek") int startWeek,
            @Param("endYear") int endYear, @Param("endWeek") int endWeek);
}
```

- [ ] **Step 4: JpaWeeklyStatisticsAdapter 생성**

```java
// domain/statistics/repository/JpaWeeklyStatisticsAdapter.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository @Primary @RequiredArgsConstructor
public class JpaWeeklyStatisticsAdapter implements WeeklyStatisticsRepository {
    private final SpringDataJpaWeeklyStatisticsRepository jpaRepository;

    @Override
    public WeeklyStatistics save(WeeklyStatistics w) { return jpaRepository.save(w); }

    @Override
    public Optional<WeeklyStatistics> findByYearAndWeekNumber(int year, int weekNumber) {
        return jpaRepository.findByYearAndWeekNumber(year, weekNumber);
    }

    @Override
    public List<WeeklyStatistics> findByYearOrderByWeekNumberAsc(int year) {
        return jpaRepository.findByYearOrderByWeekNumberAsc(year);
    }

    @Override
    public List<WeeklyStatistics> findByYearBetweenAndWeekNumberBetween(int startYear, int startWeek, int endYear, int endWeek) {
        return jpaRepository.findByYearBetweenAndWeekNumberBetween(startYear, startWeek, endYear, endWeek);
    }
}
```

- [ ] **Step 5: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/statistics/entity/WeeklyStatistics.java \
        backend/main/java/com/linkup/Petory/domain/statistics/repository/WeeklyStatistics*.java \
        backend/main/java/com/linkup/Petory/domain/statistics/repository/JpaWeeklyStatisticsAdapter.java
git commit -m "feat(statistics): WeeklyStatistics 엔티티 및 Repository 생성"
```

---

## Task 4: MonthlyStatistics 엔티티 + Repository

**Files:**
- Create: `domain/statistics/entity/MonthlyStatistics.java`
- Create: `domain/statistics/repository/MonthlyStatisticsRepository.java`
- Create: `domain/statistics/repository/SpringDataJpaMonthlyStatisticsRepository.java`
- Create: `domain/statistics/repository/JpaMonthlyStatisticsAdapter.java`

- [ ] **Step 1: MonthlyStatistics 엔티티 생성**

```java
// domain/statistics/entity/MonthlyStatistics.java
package com.linkup.Petory.domain.statistics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_statistics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class MonthlyStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false) private Integer year;
    @Column(name = "month", nullable = false) private Integer month;

    // 사용자
    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;
    @Builder.Default @Column(name = "monthly_retention_rate", precision = 5, scale = 2)
    private BigDecimal monthlyRetentionRate = BigDecimal.ZERO;
    @Builder.Default @Column(name = "churn_rate", precision = 5, scale = 2)
    private BigDecimal churnRate = BigDecimal.ZERO;

    // 케어
    @Builder.Default @Column(name = "new_care_requests") private Long newCareRequests = 0L;
    @Builder.Default @Column(name = "completed_cares") private Long completedCares = 0L;
    @Builder.Default @Column(name = "cancelled_cares") private Long cancelledCares = 0L;
    @Builder.Default @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    // 결제
    @Builder.Default @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default @Column(name = "transaction_count") private Long transactionCount = 0L;
    @Builder.Default @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    // 커뮤니티
    @Builder.Default @Column(name = "new_posts") private Long newPosts = 0L;
    @Builder.Default @Column(name = "new_meetups") private Long newMeetups = 0L;
    @Builder.Default @Column(name = "meetup_participants") private Long meetupParticipants = 0L;

    // 운영
    @Builder.Default @Column(name = "new_reports") private Long newReports = 0L;
    @Builder.Default @Column(name = "resolved_reports") private Long resolvedReports = 0L;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

- [ ] **Step 2: MonthlyStatisticsRepository 인터페이스 생성**

```java
// domain/statistics/repository/MonthlyStatisticsRepository.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import java.util.List;
import java.util.Optional;

public interface MonthlyStatisticsRepository {
    MonthlyStatistics save(MonthlyStatistics monthlyStatistics);
    Optional<MonthlyStatistics> findByYearAndMonth(int year, int month);
    List<MonthlyStatistics> findByYearOrderByMonthAsc(int year);
    Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc();
}
```

- [ ] **Step 3: SpringDataJpaMonthlyStatisticsRepository 생성**

```java
// domain/statistics/repository/SpringDataJpaMonthlyStatisticsRepository.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SpringDataJpaMonthlyStatisticsRepository extends JpaRepository<MonthlyStatistics, Long> {
    Optional<MonthlyStatistics> findByYearAndMonth(int year, int month);
    List<MonthlyStatistics> findByYearOrderByMonthAsc(int year);
    Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc();
}
```

- [ ] **Step 4: JpaMonthlyStatisticsAdapter 생성**

```java
// domain/statistics/repository/JpaMonthlyStatisticsAdapter.java
package com.linkup.Petory.domain.statistics.repository;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository @Primary @RequiredArgsConstructor
public class JpaMonthlyStatisticsAdapter implements MonthlyStatisticsRepository {
    private final SpringDataJpaMonthlyStatisticsRepository jpaRepository;

    @Override
    public MonthlyStatistics save(MonthlyStatistics m) { return jpaRepository.save(m); }

    @Override
    public Optional<MonthlyStatistics> findByYearAndMonth(int year, int month) {
        return jpaRepository.findByYearAndMonth(year, month);
    }

    @Override
    public List<MonthlyStatistics> findByYearOrderByMonthAsc(int year) {
        return jpaRepository.findByYearOrderByMonthAsc(year);
    }

    @Override
    public Optional<MonthlyStatistics> findTopByOrderByYearDescMonthDesc() {
        return jpaRepository.findTopByOrderByYearDescMonthDesc();
    }
}
```

- [ ] **Step 5: 컴파일 + 커밋**

```bash
./gradlew compileJava 2>&1 | tail -10
git add backend/main/java/com/linkup/Petory/domain/statistics/entity/MonthlyStatistics.java \
        backend/main/java/com/linkup/Petory/domain/statistics/repository/MonthlyStatistics*.java \
        backend/main/java/com/linkup/Petory/domain/statistics/repository/JpaMonthlyStatisticsAdapter.java
git commit -m "feat(statistics): MonthlyStatistics 엔티티 및 Repository 생성"
```

---

## Task 5: Response DTO 생성

**Files:**
- Create: `domain/statistics/dto/DailyStatisticsResponse.java`
- Create: `domain/statistics/dto/WeeklyStatisticsResponse.java`
- Create: `domain/statistics/dto/MonthlyStatisticsResponse.java`
- Create: `domain/statistics/dto/TodaySnapshotResponse.java`

- [ ] **Step 1: DailyStatisticsResponse 생성**

```java
// domain/statistics/dto/DailyStatisticsResponse.java
package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
public class DailyStatisticsResponse {

    private LocalDate statDate;
    private UserStats users;
    private CareStats care;
    private RevenueStats revenue;
    private CommunityStats community;
    private ModerationStats moderation;

    @Getter @Builder
    public static class UserStats {
        private Long newUsers;
        private Long activeUsers;
        private Long newProviders;
    }

    @Getter @Builder
    public static class CareStats {
        private Long newRequests;
        private Long completed;
        private Long cancelled;
        private BigDecimal completionRate;
    }

    @Getter @Builder
    public static class RevenueStats {
        private BigDecimal totalRevenue;
        private Long transactionCount;
        private BigDecimal avgTransaction;
    }

    @Getter @Builder
    public static class CommunityStats {
        private Long newPosts;
        private Long newMeetups;
        private Long meetupParticipants;
    }

    @Getter @Builder
    public static class ModerationStats {
        private Long newReports;
        private Long resolvedReports;
    }

    public static DailyStatisticsResponse from(DailyStatistics s) {
        return DailyStatisticsResponse.builder()
                .statDate(s.getStatDate())
                .users(UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .care(CareStats.builder()
                        .newRequests(s.getNewCareRequests())
                        .completed(s.getCompletedCares())
                        .cancelled(s.getCancelledCares())
                        .completionRate(s.getCareCompletionRate())
                        .build())
                .revenue(RevenueStats.builder()
                        .totalRevenue(s.getTotalRevenue())
                        .transactionCount(s.getTransactionCount())
                        .avgTransaction(s.getAvgTransaction())
                        .build())
                .community(CommunityStats.builder()
                        .newPosts(s.getNewPosts())
                        .newMeetups(s.getNewMeetups())
                        .meetupParticipants(s.getMeetupParticipants())
                        .build())
                .moderation(ModerationStats.builder()
                        .newReports(s.getNewReports())
                        .resolvedReports(s.getResolvedReports())
                        .build())
                .build();
    }
}
```

- [ ] **Step 2: WeeklyStatisticsResponse 생성**

```java
// domain/statistics/dto/WeeklyStatisticsResponse.java
package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
public class WeeklyStatisticsResponse {

    private Integer year;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private DailyStatisticsResponse.UserStats users;
    private BigDecimal weeklyRetentionRate;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static WeeklyStatisticsResponse from(WeeklyStatistics s) {
        return WeeklyStatisticsResponse.builder()
                .year(s.getYear())
                .weekNumber(s.getWeekNumber())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .users(DailyStatisticsResponse.UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .weeklyRetentionRate(s.getWeeklyRetentionRate())
                .care(DailyStatisticsResponse.CareStats.builder()
                        .newRequests(s.getNewCareRequests())
                        .completed(s.getCompletedCares())
                        .cancelled(s.getCancelledCares())
                        .completionRate(s.getCareCompletionRate())
                        .build())
                .revenue(DailyStatisticsResponse.RevenueStats.builder()
                        .totalRevenue(s.getTotalRevenue())
                        .transactionCount(s.getTransactionCount())
                        .avgTransaction(s.getAvgTransaction())
                        .build())
                .community(DailyStatisticsResponse.CommunityStats.builder()
                        .newPosts(s.getNewPosts())
                        .newMeetups(s.getNewMeetups())
                        .meetupParticipants(s.getMeetupParticipants())
                        .build())
                .moderation(DailyStatisticsResponse.ModerationStats.builder()
                        .newReports(s.getNewReports())
                        .resolvedReports(s.getResolvedReports())
                        .build())
                .build();
    }
}
```

- [ ] **Step 3: MonthlyStatisticsResponse 생성**

```java
// domain/statistics/dto/MonthlyStatisticsResponse.java
package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter @Builder
public class MonthlyStatisticsResponse {

    private Integer year;
    private Integer month;
    private DailyStatisticsResponse.UserStats users;
    private BigDecimal monthlyRetentionRate;
    private BigDecimal churnRate;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static MonthlyStatisticsResponse from(MonthlyStatistics s) {
        return MonthlyStatisticsResponse.builder()
                .year(s.getYear())
                .month(s.getMonth())
                .users(DailyStatisticsResponse.UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .monthlyRetentionRate(s.getMonthlyRetentionRate())
                .churnRate(s.getChurnRate())
                .care(DailyStatisticsResponse.CareStats.builder()
                        .newRequests(s.getNewCareRequests())
                        .completed(s.getCompletedCares())
                        .cancelled(s.getCancelledCares())
                        .completionRate(s.getCareCompletionRate())
                        .build())
                .revenue(DailyStatisticsResponse.RevenueStats.builder()
                        .totalRevenue(s.getTotalRevenue())
                        .transactionCount(s.getTransactionCount())
                        .avgTransaction(s.getAvgTransaction())
                        .build())
                .community(DailyStatisticsResponse.CommunityStats.builder()
                        .newPosts(s.getNewPosts())
                        .newMeetups(s.getNewMeetups())
                        .meetupParticipants(s.getMeetupParticipants())
                        .build())
                .moderation(DailyStatisticsResponse.ModerationStats.builder()
                        .newReports(s.getNewReports())
                        .resolvedReports(s.getResolvedReports())
                        .build())
                .build();
    }
}
```

- [ ] **Step 4: TodaySnapshotResponse 생성**

```java
// domain/statistics/dto/TodaySnapshotResponse.java
package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Builder
public class TodaySnapshotResponse {
    private LocalDate statDate;
    private LocalDateTime asOf;
    private DailyStatisticsResponse.UserStats users;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static TodaySnapshotResponse from(DailyStatistics s) {
        DailyStatisticsResponse base = DailyStatisticsResponse.from(s);
        return TodaySnapshotResponse.builder()
                .statDate(s.getStatDate())
                .asOf(LocalDateTime.now())
                .users(base.getUsers())
                .care(base.getCare())
                .revenue(base.getRevenue())
                .community(base.getCommunity())
                .moderation(base.getModeration())
                .build();
    }
}
```

- [ ] **Step 5: 컴파일 + 커밋**

```bash
./gradlew compileJava 2>&1 | tail -10
git add backend/main/java/com/linkup/Petory/domain/statistics/dto/
git commit -m "feat(statistics): Response DTO 생성 (Daily/Weekly/Monthly/TodaySnapshot)"
```

---

## Task 6: StatisticsScheduler 재설계

**Files:**
- Modify: `domain/statistics/service/StatisticsScheduler.java`
- Test: `backend/test/java/com/linkup/Petory/domain/statistics/StatisticsSchedulerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// backend/test/java/com/linkup/Petory/domain/statistics/StatisticsSchedulerTest.java
package com.linkup.Petory.domain.statistics;

import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.service.StatisticsScheduler;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsSchedulerTest {

    @Mock DailyStatisticsRepository dailyStatisticsRepository;
    @Mock WeeklyStatisticsRepository weeklyStatisticsRepository;
    @Mock MonthlyStatisticsRepository monthlyStatisticsRepository;
    @Mock UsersRepository usersRepository;
    @Mock BoardRepository boardRepository;
    @Mock CareRequestRepository careRequestRepository;
    @Mock MeetupRepository meetupRepository;
    @Mock MeetupParticipantsRepository meetupParticipantsRepository;
    @Mock ReportRepository reportRepository;

    @InjectMocks StatisticsScheduler scheduler;

    @BeforeEach
    void setup() {
        when(usersRepository.countByCreatedAtBetween(any(), any())).thenReturn(5L);
        when(usersRepository.countByLastLoginAtBetween(any(), any())).thenReturn(100L);
        when(usersRepository.countByRoleAndCreatedAtBetween(eq(Role.SERVICE_PROVIDER), any(), any())).thenReturn(2L);
        when(boardRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(careRequestRepository.countByCreatedAtBetween(any(), any())).thenReturn(8L);
        when(careRequestRepository.countByCompletedAtBetween(any(), any())).thenReturn(6L);
        when(careRequestRepository.countByStatusAndUpdatedAtBetween(eq(CareRequestStatus.CANCELLED), any(), any())).thenReturn(1L);
        when(meetupRepository.countByCreatedAtBetween(any(), any())).thenReturn(3L);
        when(meetupParticipantsRepository.countByJoinedAtBetween(any(), any())).thenReturn(15L);
        when(reportRepository.countByCreatedAtBetween(any(), any())).thenReturn(2L);
        when(reportRepository.countByStatusAndUpdatedAtBetween(eq(ReportStatus.RESOLVED), any(), any())).thenReturn(1L);
        when(dailyStatisticsRepository.findByStatDate(any())).thenReturn(Optional.empty());
        when(dailyStatisticsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aggregateForDate_savesAllFields() {
        LocalDate date = LocalDate.of(2026, 4, 17);
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository).save(argThat(s ->
                s.getNewUsers() == 5L &&
                s.getCompletedCares() == 6L &&
                s.getCancelledCares() == 1L &&
                s.getNewProviders() == 2L &&
                s.getResolvedReports() == 1L
        ));
    }

    @Test
    void aggregateForDate_skipIfAlreadyExists() {
        LocalDate date = LocalDate.of(2026, 4, 17);
        when(dailyStatisticsRepository.findByStatDate(date)).thenReturn(Optional.of(new DailyStatistics()));
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void careCompletionRate_calculatedCorrectly() {
        // completed=6, cancelled=1 → rate = 6/(6+1)*100 = 85.71
        LocalDate date = LocalDate.of(2026, 4, 17);
        scheduler.aggregateStatisticsForDate(date);
        verify(dailyStatisticsRepository).save(argThat(s ->
                s.getCareCompletionRate().compareTo(new BigDecimal("85.71")) == 0
        ));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsSchedulerTest" 2>&1 | tail -20
```
Expected: FAIL (StatisticsScheduler 미수정)

- [ ] **Step 3: StatisticsScheduler 전체 교체**

`domain/statistics/service/StatisticsScheduler.java` 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsScheduler {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CareRequestRepository careRequestRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final ReportRepository reportRepository;

    @Scheduled(cron = "0 0 18 * * ?")
    @Transactional
    public void aggregateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            detectAndBackfillMissing(yesterday);
            aggregateStatisticsForDate(yesterday);
            if (yesterday.getDayOfWeek() == DayOfWeek.SUNDAY) {
                rollupWeekly(yesterday);
            }
            if (yesterday.equals(yesterday.withDayOfMonth(yesterday.lengthOfMonth()))) {
                rollupMonthly(yesterday.getYear(), yesterday.getMonthValue());
            }
            deleteExpiredDaily();
        } catch (Exception e) {
            log.error("통계 집계 실패 — 날짜: {}, 원인: {}", yesterday, e.getMessage(), e);
        }
    }

    @Transactional
    public void aggregateStatisticsForDate(LocalDate date) {
        if (dailyStatisticsRepository.findByStatDate(date).isPresent()) {
            log.warn("이미 {}의 통계가 존재합니다. 건너뜁니다.", date);
            return;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        long completed = careRequestRepository.countByCompletedAtBetween(start, end);
        long cancelled = careRequestRepository.countByStatusAndUpdatedAtBetween(CareRequestStatus.CANCELLED, start, end);
        BigDecimal completionRate = calcRate(completed, completed + cancelled);

        DailyStatistics stats = DailyStatistics.builder()
                .statDate(date)
                .newUsers(usersRepository.countByCreatedAtBetween(start, end))
                .activeUsers(usersRepository.countByLastLoginAtBetween(start, end))
                .newProviders(usersRepository.countByRoleAndCreatedAtBetween(Role.SERVICE_PROVIDER, start, end))
                .newCareRequests(careRequestRepository.countByCreatedAtBetween(start, end))
                .completedCares(completed)
                .cancelledCares(cancelled)
                .careCompletionRate(completionRate)
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

    @Transactional
    public void rollupWeekly(LocalDate sunday) {
        LocalDate monday = sunday.minusDays(6);
        int year = sunday.get(IsoFields.WEEK_BASED_YEAR);
        int weekNumber = sunday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        if (weeklyStatisticsRepository.findByYearAndWeekNumber(year, weekNumber).isPresent()) {
            log.warn("이미 {}년 {}주차 통계가 존재합니다.", year, weekNumber);
            return;
        }

        List<DailyStatistics> days = dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(monday, sunday);

        long completed = sum(days, d -> d.getCompletedCares());
        long cancelled = sum(days, d -> d.getCancelledCares());
        long currentWau = sum(days, d -> d.getActiveUsers());
        BigDecimal retentionRate = calcWeeklyRetention(year, weekNumber, currentWau);

        WeeklyStatistics weekly = WeeklyStatistics.builder()
                .year(year).weekNumber(weekNumber)
                .startDate(monday).endDate(sunday)
                .newUsers(sum(days, d -> d.getNewUsers()))
                .activeUsers(currentWau)
                .newProviders(sum(days, d -> d.getNewProviders()))
                .weeklyRetentionRate(retentionRate)
                .newCareRequests(sum(days, d -> d.getNewCareRequests()))
                .completedCares(completed).cancelledCares(cancelled)
                .careCompletionRate(calcRate(completed, completed + cancelled))
                .totalRevenue(sumRevenue(days))
                .transactionCount(sum(days, d -> d.getTransactionCount()))
                .avgTransaction(calcAvgTransaction(sumRevenue(days), sum(days, d -> d.getTransactionCount())))
                .newPosts(sum(days, d -> d.getNewPosts()))
                .newMeetups(sum(days, d -> d.getNewMeetups()))
                .meetupParticipants(sum(days, d -> d.getMeetupParticipants()))
                .newReports(sum(days, d -> d.getNewReports()))
                .resolvedReports(sum(days, d -> d.getResolvedReports()))
                .build();

        weeklyStatisticsRepository.save(weekly);
        log.info("주간 통계 롤업 완료: {}년 {}주차", year, weekNumber);
    }

    @Transactional
    public void rollupMonthly(int year, int month) {
        if (monthlyStatisticsRepository.findByYearAndMonth(year, month).isPresent()) {
            log.warn("이미 {}년 {}월 통계가 존재합니다.", year, month);
            return;
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<DailyStatistics> days = dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(start, end);

        long completed = sum(days, d -> d.getCompletedCares());
        long cancelled = sum(days, d -> d.getCancelledCares());
        long currentMau = sum(days, d -> d.getActiveUsers());
        BigDecimal retention = calcMonthlyRetention(year, month, currentMau);
        BigDecimal churn = BigDecimal.valueOf(100).subtract(retention).max(BigDecimal.ZERO);

        MonthlyStatistics monthly = MonthlyStatistics.builder()
                .year(year).month(month)
                .newUsers(sum(days, d -> d.getNewUsers()))
                .activeUsers(currentMau)
                .newProviders(sum(days, d -> d.getNewProviders()))
                .monthlyRetentionRate(retention).churnRate(churn)
                .newCareRequests(sum(days, d -> d.getNewCareRequests()))
                .completedCares(completed).cancelledCares(cancelled)
                .careCompletionRate(calcRate(completed, completed + cancelled))
                .totalRevenue(sumRevenue(days))
                .transactionCount(sum(days, d -> d.getTransactionCount()))
                .avgTransaction(calcAvgTransaction(sumRevenue(days), sum(days, d -> d.getTransactionCount())))
                .newPosts(sum(days, d -> d.getNewPosts()))
                .newMeetups(sum(days, d -> d.getNewMeetups()))
                .meetupParticipants(sum(days, d -> d.getMeetupParticipants()))
                .newReports(sum(days, d -> d.getNewReports()))
                .resolvedReports(sum(days, d -> d.getResolvedReports()))
                .build();

        monthlyStatisticsRepository.save(monthly);
        log.info("월간 통계 롤업 완료: {}년 {}월", year, month);
    }

    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        startDate.datesUntil(endDate.plusDays(1)).forEach(date -> {
            try {
                aggregateStatisticsForDate(date);
            } catch (Exception e) {
                log.error("Backfill 실패 — 날짜: {}, 원인: {}", date, e.getMessage());
            }
        });
    }

    private void detectAndBackfillMissing(LocalDate yesterday) {
        LocalDate checkStart = yesterday.minusDays(6);
        Set<LocalDate> existing = dailyStatisticsRepository
                .findStatDatesByDateRange(checkStart, yesterday.minusDays(1))
                .stream().collect(Collectors.toSet());

        checkStart.datesUntil(yesterday).forEach(date -> {
            if (!existing.contains(date)) {
                log.warn("누락된 통계 감지: {} — 자동 backfill 실행", date);
                try { aggregateStatisticsForDate(date); }
                catch (Exception e) { log.error("자동 backfill 실패: {}", date); }
            }
        });
    }

    private void deleteExpiredDaily() {
        LocalDate cutoff = LocalDate.now().minusYears(1);
        dailyStatisticsRepository.deleteByStatDateBefore(cutoff);
    }

    // --- 계산 헬퍼 ---

    private long sum(List<DailyStatistics> days, java.util.function.Function<DailyStatistics, Long> getter) {
        return days.stream().mapToLong(getter::apply).sum();
    }

    private BigDecimal sumRevenue(List<DailyStatistics> days) {
        return days.stream().map(DailyStatistics::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcRate(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator * 100.0 / denominator).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcAvgTransaction(BigDecimal total, long count) {
        if (count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcWeeklyRetention(int year, int weekNumber, long currentWau) {
        int prevWeek = weekNumber - 1;
        int prevYear = year;
        if (prevWeek == 0) { prevYear--; prevWeek = 52; }
        return weeklyStatisticsRepository.findByYearAndWeekNumber(prevYear, prevWeek)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                        : calcRate(currentWau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calcMonthlyRetention(int year, int month, long currentMau) {
        int prevMonth = month - 1;
        int prevYear = year;
        if (prevMonth == 0) { prevYear--; prevMonth = 12; }
        return monthlyStatisticsRepository.findByYearAndMonth(prevYear, prevMonth)
                .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
                        : calcRate(currentMau, prev.getActiveUsers()))
                .orElse(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsSchedulerTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsScheduler.java \
        backend/test/java/com/linkup/Petory/domain/statistics/StatisticsSchedulerTest.java
git commit -m "feat(statistics): StatisticsScheduler 재설계 (롤업, 누락 감지, 만료 삭제)"
```

---

## Task 7: StatisticsService 재설계

**Files:**
- Modify: `domain/statistics/service/StatisticsService.java`
- Test: `backend/test/java/com/linkup/Petory/domain/statistics/StatisticsServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// backend/test/java/com/linkup/Petory/domain/statistics/StatisticsServiceTest.java
package com.linkup.Petory.domain.statistics;

import com.linkup.Petory.domain.statistics.dto.*;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import com.linkup.Petory.domain.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock DailyStatisticsRepository dailyStatisticsRepository;
    @Mock WeeklyStatisticsRepository weeklyStatisticsRepository;
    @Mock MonthlyStatisticsRepository monthlyStatisticsRepository;

    @InjectMocks StatisticsService statisticsService;

    @Test
    void getDailyStatistics_returnsMappedResponse() {
        DailyStatistics entity = DailyStatistics.builder()
                .statDate(LocalDate.of(2026, 4, 17))
                .newUsers(10L).activeUsers(200L).newProviders(2L)
                .newCareRequests(5L).completedCares(4L).cancelledCares(1L)
                .careCompletionRate(new BigDecimal("80.00"))
                .totalRevenue(new BigDecimal("50000")).transactionCount(4L)
                .avgTransaction(new BigDecimal("12500"))
                .newPosts(20L).newMeetups(3L).meetupParticipants(12L)
                .newReports(1L).resolvedReports(1L)
                .build();
        when(dailyStatisticsRepository.findByStatDateBetweenOrderByStatDateAsc(any(), any()))
                .thenReturn(List.of(entity));

        List<DailyStatisticsResponse> result = statisticsService.getDailyStatistics(
                LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 17));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsers().getNewUsers()).isEqualTo(10L);
        assertThat(result.get(0).getCare().getCompletionRate()).isEqualTo(new BigDecimal("80.00"));
        assertThat(result.get(0).getRevenue().getTotalRevenue()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    void recordPayment_updatesRevenue() {
        DailyStatistics today = DailyStatistics.builder()
                .statDate(LocalDate.now())
                .totalRevenue(BigDecimal.ZERO)
                .transactionCount(0L)
                .avgTransaction(BigDecimal.ZERO)
                .build();
        when(dailyStatisticsRepository.findByStatDate(LocalDate.now())).thenReturn(Optional.of(today));
        when(dailyStatisticsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        statisticsService.recordPayment(new BigDecimal("30000"));

        assertThat(today.getTotalRevenue()).isEqualTo(new BigDecimal("30000"));
        assertThat(today.getTransactionCount()).isEqualTo(1L);
        assertThat(today.getAvgTransaction()).isEqualTo(new BigDecimal("30000.00"));
    }

    @Test
    void getDailyStatistics_throwsWhenStartAfterEnd() {
        assertThatThrownBy(() -> statisticsService.getDailyStatistics(
                LocalDate.of(2026, 4, 18), LocalDate.of(2026, 4, 17)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsServiceTest" 2>&1 | tail -20
```
Expected: FAIL

- [ ] **Step 3: StatisticsService 전체 교체**

`domain/statistics/service/StatisticsService.java` 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.statistics.service;

import com.linkup.Petory.domain.statistics.dto.*;
import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;
import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import com.linkup.Petory.domain.statistics.repository.DailyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.MonthlyStatisticsRepository;
import com.linkup.Petory.domain.statistics.repository.WeeklyStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final WeeklyStatisticsRepository weeklyStatisticsRepository;
    private final MonthlyStatisticsRepository monthlyStatisticsRepository;
    private final StatisticsScheduler statisticsScheduler;
    private final ApplicationContext applicationContext;

    private StatisticsService getThis() {
        return applicationContext.getBean(StatisticsService.class);
    }

    public List<DailyStatisticsResponse> getDailyStatistics(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate가 endDate보다 클 수 없습니다.");
        }
        return dailyStatisticsRepository
                .findByStatDateBetweenOrderByStatDateAsc(startDate, endDate)
                .stream().map(DailyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    public List<WeeklyStatisticsResponse> getWeeklyStatistics(int year) {
        return weeklyStatisticsRepository.findByYearOrderByWeekNumberAsc(year)
                .stream().map(WeeklyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    public List<MonthlyStatisticsResponse> getMonthlyStatistics(int year) {
        return monthlyStatisticsRepository.findByYearOrderByMonthAsc(year)
                .stream().map(MonthlyStatisticsResponse::from)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "todayStats", key = "'today'")
    public TodaySnapshotResponse getTodaySnapshot() {
        LocalDate today = LocalDate.now();
        DailyStatistics snapshot = dailyStatisticsRepository.findByStatDate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());
        return TodaySnapshotResponse.from(snapshot);
    }

    @Transactional
    public void recordPayment(BigDecimal amount) {
        LocalDate today = LocalDate.now();
        DailyStatistics stats = dailyStatisticsRepository.findByStatDate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());

        BigDecimal newRevenue = stats.getTotalRevenue().add(amount);
        long newCount = stats.getTransactionCount() + 1;
        BigDecimal newAvg = newRevenue.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        stats.setTotalRevenue(newRevenue);
        stats.setTransactionCount(newCount);
        stats.setAvgTransaction(newAvg);
        dailyStatisticsRepository.save(stats);
    }

    @Transactional
    public void backfill(LocalDate startDate, LocalDate endDate) {
        statisticsScheduler.backfill(startDate, endDate);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.linkup.Petory.domain.statistics.StatisticsServiceTest" 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, 3 tests passed

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsService.java \
        backend/test/java/com/linkup/Petory/domain/statistics/StatisticsServiceTest.java
git commit -m "feat(statistics): StatisticsService 재설계 (주/월 조회, recordPayment, backfill)"
```

---

## Task 8: AdminStatisticsController 재설계

**Files:**
- Modify: `domain/admin/controller/AdminStatisticsController.java`

- [ ] **Step 1: AdminStatisticsController 전체 교체**

`domain/admin/controller/AdminStatisticsController.java` 전체를 아래로 교체:

```java
package com.linkup.Petory.domain.admin.controller;

import com.linkup.Petory.domain.statistics.dto.*;
import com.linkup.Petory.domain.statistics.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/daily")
    public ResponseEntity<List<DailyStatisticsResponse>> getDailyStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusDays(29);
        return ResponseEntity.ok(statisticsService.getDailyStatistics(startDate, endDate));
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyStatisticsResponse>> getWeeklyStatistics(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        return ResponseEntity.ok(statisticsService.getWeeklyStatistics(year));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyStatisticsResponse>> getMonthlyStatistics(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        return ResponseEntity.ok(statisticsService.getMonthlyStatistics(year));
    }

    @GetMapping("/summary")
    public ResponseEntity<TodaySnapshotResponse> getTodaySnapshot() {
        return ResponseEntity.ok(statisticsService.getTodaySnapshot());
    }

    @PostMapping("/backfill")
    public ResponseEntity<String> backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        statisticsService.backfill(startDate, endDate);
        return ResponseEntity.ok(startDate + " ~ " + endDate + " 통계 집계가 완료되었습니다.");
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/admin/controller/AdminStatisticsController.java
git commit -m "feat(statistics): AdminStatisticsController 재설계 (MASTER 전용, 주/월 엔드포인트 추가)"
```

---

## Task 9: PetCoinEscrowService 결제 이벤트 연동

에스크로 완료(제공자에게 지급) 시점에 일별 통계에 매출 반영.

**Files:**
- Modify: `domain/payment/service/PetCoinEscrowService.java`

- [ ] **Step 1: PetCoinEscrowService에서 releaseEscrow 메서드 확인**

```bash
grep -n "release\|RELEASED\|providerPay\|complete" \
  backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java
```

에스크로가 제공자에게 지급되는 메서드명을 확인한다.

- [ ] **Step 2: StatisticsService 의존성 주입 추가**

`PetCoinEscrowService.java` 클래스 필드에 추가:
```java
private final StatisticsService statisticsService;
```

- [ ] **Step 3: 에스크로 지급 완료 시점에 recordPayment 호출**

에스크로 release(제공자 지급) 메서드에서, 실제 코인이 제공자에게 전달되는 라인 바로 다음에 추가:
```java
statisticsService.recordPayment(BigDecimal.valueOf(escrow.getAmount()));
```

`BigDecimal`이 import되지 않은 경우 파일 상단에 추가:
```java
import java.math.BigDecimal;
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java
git commit -m "feat(statistics): 에스크로 완료 시 매출 통계 즉시 반영"
```

---

## Task 10: 전체 테스트 통과 확인

- [ ] **Step 1: 전체 테스트 실행**

```bash
./gradlew test 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 실패 테스트가 있으면 원인 확인**

```bash
./gradlew test 2>&1 | grep -A 5 "FAILED\|ERROR"
```

- [ ] **Step 3: 서버 기동 확인 (MySQL + Redis 실행 상태 필요)**

```bash
./gradlew bootRun 2>&1 | grep -E "Started|ERROR" | head -5
```
Expected: `Started PetoryApplication`
