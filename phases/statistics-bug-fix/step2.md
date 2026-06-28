# Step 2 — C1: aggregateForDate skip→merge 전환 (결제-배치 충돌 수정)

## 전제

Step 1 완료 후 실행한다. `StatisticsAggregator.aggregateForDate`가 존재해야 한다.

---

## 목표

`recordPayment()`가 먼저 `DailyStatistics` row를 생성한 날, 다음날 배치가 해당 날짜를 skip해서
활동 지표(newUsers, activeUsers, newCareRequests 등)가 영구 0으로 남는 데이터 유실 버그를 수정한다.

**수정 전 흐름 (버그)**:
```
Day N 결제 발생 → recordPayment() → DailyStatistics(N) INSERT (payment 데이터만)
Day N+1 18:00 → aggregateForDate(N) → findByStatDate 있음 → skip
→ Day N: newUsers=0, activeUsers=0 ... 영구 유실
```

**수정 후 흐름**:
```
Day N 결제 발생 → recordPayment() → DailyStatistics(N) INSERT (payment 데이터만)
Day N+1 18:00 → aggregateForDate(N) → findByStatDate 있음 → 활동 지표만 UPDATE, revenue 보존
→ Day N: newUsers=3, activeUsers=12 ... 정상 + totalRevenue 보존
```

---

## 변경 상세

### `StatisticsAggregator.java` — `aggregateForDate` 메서드 교체

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsAggregator.java`

#### 변경 전

```java
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
```

#### 변경 후

```java
@Transactional
public void aggregateForDate(LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.atTime(LocalTime.MAX);

    // 기존 row가 있으면 가져와서 merge, 없으면 새로 빌드
    DailyStatistics stats = dailyStatisticsRepository.findByStatDate(date)
            .orElse(DailyStatistics.builder().statDate(date).build());

    // recordPayment()가 먼저 채운 결제 데이터는 보존
    boolean hasPaymentData = stats.getTransactionCount() != null && stats.getTransactionCount() > 0;

    long completed = careRequestRepository.countByCompletedAtBetween(start, end);
    long cancelled = careRequestRepository.countByStatusAndUpdatedAtBetween(CareRequestStatus.CANCELLED, start, end);

    // 활동 지표는 항상 배치 결과로 덮어쓴다
    stats.setNewUsers(usersRepository.countByCreatedAtBetween(start, end));
    stats.setActiveUsers(usersRepository.countByLastLoginAtBetween(start, end));
    stats.setNewProviders(usersRepository.countByRoleAndCreatedAtBetween(Role.SERVICE_PROVIDER, start, end));
    stats.setNewCareRequests(careRequestRepository.countByCreatedAtBetween(start, end));
    stats.setCompletedCares(completed);
    stats.setCancelledCares(cancelled);
    stats.setCareCompletionRate(calcRate(completed, completed + cancelled));
    stats.setNewPosts(boardRepository.countByCreatedAtBetween(start, end));
    stats.setNewMeetups(meetupRepository.countByCreatedAtBetween(start, end));
    stats.setMeetupParticipants(meetupParticipantsRepository.countByJoinedAtBetween(start, end));
    stats.setNewReports(reportRepository.countByCreatedAtBetween(start, end));
    stats.setResolvedReports(reportRepository.countByStatusAndUpdatedAtBetween(ReportStatus.RESOLVED, start, end));

    // 결제 데이터가 없을 때만 0으로 초기화 (recordPayment 미호출 날짜)
    if (!hasPaymentData) {
        stats.setTotalRevenue(BigDecimal.ZERO);
        stats.setTransactionCount(0L);
        stats.setAvgTransaction(BigDecimal.ZERO);
    }

    dailyStatisticsRepository.save(stats);
    log.info("일일 통계 집계 완료 (merge): {}", date);
}
```

---

## 동작 검증 시나리오

| 시나리오 | 기대 결과 |
|---------|----------|
| Day N에 결제 없음, 배치 실행 | 모든 활동 지표 정상 집계, revenue=0 |
| Day N에 결제 발생, 배치 실행 | 모든 활동 지표 정상 집계, revenue=recordPayment가 쌓은 값 유지 |
| backfill 대상 날짜에 결제 데이터 있음 | 활동 지표 채워짐, revenue 보존 |
| 배치 재실행 (동일 날짜) | 활동 지표 재계산으로 덮어쓰기, revenue 보존 (idempotent) |

---

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
```

수동 검증 (로컬 DB 필요):
```sql
-- 테스트용: 오늘 날짜 DailyStatistics를 payment-only row로 만들기
INSERT INTO dailystatistics (stat_date, total_revenue, transaction_count, avg_transaction,
  new_users, active_users, new_providers, new_care_requests, completed_cares, cancelled_cares,
  care_completion_rate, new_posts, new_meetups, meetup_participants, new_reports, resolved_reports)
VALUES (CURDATE(), 50000.00, 2, 25000.00, 0, 0, 0, 0, 0, 0, 0.00, 0, 0, 0, 0, 0);

-- 배치 수동 트리거 후 확인: newUsers 등이 채워지고 total_revenue는 50000.00 유지돼야 함
```

---

## 주의

- `DailyStatistics`에 `@Setter`가 선언돼 있어 필드별 set이 가능하다 (`entity/DailyStatistics.java` 확인).
- `Builder`를 새로 짜지 않고 setter로 머지하는 이유: 기존 id(PK)와 연관 필드를 유지해야 UPDATE가 동작하기 때문.
- 이 수정 이후 수동 backfill(`POST /api/admin/statistics/backfill`)을 실행하면 기존 payment-only row에 활동 지표를 채울 수 있다.
