# Statistics 도메인 리뷰 — 2026-06-28

> 대상 파일: `StatisticsScheduler.java`, `StatisticsService.java`, `DailyStatistics.java`
> 리뷰 관점: 기술적 결함 + 도메인 설계 정합성

---

## 1. 현재 코드 사실

> 기준 커밋: `537c54ca fix(statistics): harden aggregation and payment updates`

| 사실 | 위치 |
| --- | --- |
| 스케줄러 실행 시각: 00:05 (`0 5 0 * * ?`) | `StatisticsScheduler.java:49` |
| 집계 대상: yesterday = `now().minusDays(1)` | `StatisticsScheduler.java:52` |
| 일별 activeUsers 집계: `loginEventRepository.countDistinctUsersBetween(start, end)` | `StatisticsAggregator.java:60` |
| 주간 activeUsers 집계: 해당 주 `login_events` DISTINCT 사용자 수 | `StatisticsScheduler.java:87-88` |
| 월간 activeUsers 집계: 해당 월 `login_events` DISTINCT 사용자 수 | `StatisticsScheduler.java:134-135` |
| 일별 집계: 기존 row가 있어도 활동 지표를 merge | `StatisticsAggregator.java:51-79` |
| 날짜별 집계 트랜잭션: `REQUIRES_NEW` | `StatisticsAggregator.java:46` |
| `recordPayment`: `ON DUPLICATE KEY UPDATE` native upsert 호출 | `StatisticsService.java:82-85` |
| `recordPayment`: `todayStats` 캐시 evict | `StatisticsService.java:83` |
| payment upsert SQL: 매출/건수/평균 거래액을 DB 단일 문장으로 갱신 | `SpringDataJpaDailyStatisticsRepository.java:31-41` |
| weekly retention: 전년도 마지막 ISO 주차를 `12/28` 기준으로 동적 계산 | `StatisticsScheduler.java:225-231` |

### 1.0 처리 결과 요약

| 항목 | 상태 | 처리 |
| --- | --- | --- |
| C0: `lastLoginAt` 기반 DAU 원천 오류 | 수정됨 | `login_events` append-only + DISTINCT 집계 |
| C1: 결제-배치 충돌 | 수정됨 | skip 제거, existing row merge |
| C2: self-invocation 트랜잭션 무력화 | 수정됨 | `StatisticsAggregator` 별도 빈 + `REQUIRES_NEW` |
| C3/W3: `recordPayment` lost update / duplicate key race | 수정됨 | native upsert |
| W1: WAU/MAU = DAU 합산 | 수정됨 | 주/월 기간 DISTINCT 집계 |
| W2: ISO 53주차 | 수정됨 | 전년도 마지막 ISO 주차 동적 계산 |
| W4: daily `total_revenue` nullable drift | 완화 | `sumRevenue()` null-safe 처리. DDL `NOT NULL` 보정은 별도 migration 필요 |

### 1.1 확인된 실제 테이블 사실

아래 내용은 로컬 MySQL에서 `SHOW CREATE TABLE ...`, `SHOW INDEX FROM dailystatistics`로 확인한 실제 스키마 기준이다.

| 사실                                          | 실제 DDL 근거                                            |
| --------------------------------------------- | -------------------------------------------------------- |
| `dailystatistics.stat_date`는 unique          | `UNIQUE KEY stat_date (stat_date)`                       |
| daily 활동 지표는 대부분 `NOT NULL DEFAULT 0` | `new_users`, `active_users`, `new_care_requests` 등      |
| daily `active_users` 컬럼 comment는 `DAU`     | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'DAU'` |
| weekly `active_users` 컬럼 comment는 `WAU`    | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'WAU'` |
| monthly `active_users` 컬럼 comment는 `MAU`   | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'MAU'` |
| daily 인덱스는 PK와 `stat_date` unique뿐      | `PRIMARY`, `stat_date`                                   |
| daily `total_revenue`만 `NOT NULL`이 없음     | `total_revenue decimal(15,2) DEFAULT '0.00'`             |

이 테이블 구조상 결제가 만든 daily row와 배치가 만든 daily row를 구분할 컬럼이 없다.
이 문제는 배치 집계 merge 전환으로 코드 레벨에서 처리했다.

---

## 2. 발견된 문제 및 처리 상태

### 🔴 Critical

#### [C0] DAU 원천 데이터 자체가 틀림 — lastLoginAt 단일 컬럼 덮어쓰기 ✅ 수정됨

**발견 당시 문제**: 로그인할 때마다 `users.last_login_at`이 현재 시각으로 덮어 쓰였다.
당시 배치가 18:00에 실행되므로, 어제 로그인 후 오늘 18:00 전에 다시 로그인한 사용자는
어제 DAU 쿼리 범위(`lastLoginAt BETWEEN yesterday 00:00 AND yesterday 23:59`)에서 이탈한다.

**발견 당시 재현 흐름**:

```
Day N  10:00 — 사용자 A 로그인 → lastLoginAt = Day N 10:00
Day N+1 09:00 — 사용자 A 로그인 → lastLoginAt = Day N+1 09:00  ← 덮어쓰기
Day N+1 18:00 — 배치 실행 (yesterday = Day N, 발견 당시 cron)
  → countByLastLoginAtBetween(Day N 00:00, Day N 23:59)
  → 사용자 A: lastLoginAt = Day N+1 09:00 → 범위 밖 → DAU 미집계
```

**영향**: 매일 접속하는 활성 사용자일수록 DAU에서 누락될 가능성이 높다.
이 오류가 WAU/MAU(W1)의 기반 데이터이므로 오염이 두 단계로 쌓인다.

**근본 원인**: DAU는 "해당 날 로그인한 이벤트 수"를 세야 하는데,
단일 컬럼 `lastLoginAt`은 **마지막 로그인 시각만 보존**해 이벤트 이력이 없다.

**처리 결과**: 로그인 이벤트를 별도 테이블에 append한다.

```sql
CREATE TABLE login_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_at DATETIME NOT NULL,
    INDEX idx_login_at (login_at)
);
```

```sql
// DAU 집계
SELECT COUNT(DISTINCT user_id) FROM login_events
WHERE login_at BETWEEN :start AND :end
```

배치 실행 시각도 00:05로 변경해 도입 전/예외 상황의 race window를 줄였다.

---

#### [C1] 결제 발생 시 해당 날 활동 지표 영구 누락 ✅ 수정됨

**문제**: 결제가 하루라도 발생하면 그날의 `newUsers`, `activeUsers`, `newCareRequests` 등 모든 활동 지표가 영원히 0으로 남는다.

**재현 시나리오**:

```
Day N — 결제 발생
  → recordPayment()
  → DailyStatistics(statDate=N) INSERT
    (totalRevenue=50000, 나머지 필드 전부 0)

Day N+1, 18:00 — 배치 실행 (yesterday = Day N, 발견 당시 cron)
  → aggregateStatisticsForDate(N)
  → findByStatDate(N).isPresent() == true  ← 결제가 만든 row
  → "이미 존재합니다. 건너뜁니다."

결과: Day N은 payment 데이터만 있고
      newUsers, activeUsers, newCareRequests 등 영구 0
```

**근본 원인**: `DailyStatistics` 하나가 두 역할을 맡는다.

- `recordPayment`: 실시간 결제 누적기 (오늘)
- `aggregateStatisticsForDate`: 배치 스냅샷 (어제, 발견 당시 메서드명)

같은 "존재 여부" 가드가 두 흐름을 충돌시켰다.
현재는 `StatisticsAggregator.aggregateForDate()`가 기존 row를 찾아 활동 지표를 항상 덮어쓴다.
`transactionCount > 0`인 row는 payment 필드를 보존한다.

---

#### [C2] Self-invocation으로 @Transactional 무력화 (B5) ✅ 수정됨

발견 당시 `StatisticsScheduler` 내에서 `aggregateStatisticsForDate`를 `this.method()` 형태로 3곳 호출했다.
Spring AOP 프록시를 타지 않아 `aggregateStatisticsForDate`의 `@Transactional`이 실제로 동작하지 않았다.

| 호출 위치                    | 경로                           |
| ---------------------------- | ------------------------------ |
| `aggregateDailyStatistics()` | 직접 호출 (line 66)            |
| `detectAndBackfillMissing()` | private → 내부 호출 (line 237) |
| `backfill()`                 | forEach 내부 호출 (line 220)   |

**처리 결과**: `StatisticsAggregator` 별도 빈을 만들고 모든 일별 집계 호출을 크로스-빈 호출로 변경했다.
`aggregateForDate()`는 `REQUIRES_NEW`로 실행되어 날짜별 집계 트랜잭션이 독립된다.

---

#### [C3] recordPayment 동시성 — 레이스 컨디션 (B3) ✅ 수정됨

동시 결제 다수 진입 시 read-then-write 패턴에 lock이 없어 `totalRevenue`, `transactionCount` 유실 발생.

```java
// StatisticsService.java:85 — lock 없는 일반 SELECT
DailyStatistics stats = dailyStatisticsRepository.findByStatDate(today)
        .orElse(DailyStatistics.builder().statDate(today).build());
```

**처리 결과**: read-modify-write를 제거하고 `INSERT ... ON DUPLICATE KEY UPDATE` native upsert로 전환했다.
동시 insert/update 경합은 DB 단일 문장에서 처리한다.

---

### 🟡 Warning

#### [W1] WAU / MAU = DAU 합산 → Retention 지표 왜곡 ✅ 수정됨

```
월요일~일요일 7일 매일 접속한 사용자 1명 → WAU = 7
weeklyRetentionRate = 현재WAU / 이전WAU = 의미 없는 비율
```

"Retention Rate"라는 이름과 달리 실제로는 **DAU 평균 빈도 비율**이었다.
현재는 weekly/monthly `activeUsers`를 `login_events` 기간 내 DISTINCT 사용자 수로 직접 집계한다.

---

#### [W2] ISO 53주차 미처리 ✅ 수정됨

```java
// 발견 당시 StatisticsScheduler.java:278
if (prevWeek == 0) {
    prevYear--;
    prevWeek = 52; // 2020, 2026 등 53주차 연도에서 오류
}
```

현재는 `LocalDate.of(prevYear, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)`로 전년도 마지막 ISO 주차를 계산한다.

---

#### [W3] 동시 INSERT 시 DataIntegrityViolationException 미처리 ✅ 수정됨

`stat_date`는 unique 제약이 있으나 `findByStatDate` 체크 → `save` 사이에 동시 실행 시 DB 레벨에서 Duplicate key 예외가 발생하고 상위로 전파된다.
현재는 payment 반영 경로를 native upsert로 바꿔 해당 경합을 단일 SQL로 처리한다.

#### [W4] daily `total_revenue` nullable 스키마 드리프트 ⚠️ 부분 완화

실제 DDL에서 daily `total_revenue`는 `DEFAULT '0.00'`만 있고 `NOT NULL`이 없다.
weekly/monthly의 `total_revenue`는 `NOT NULL DEFAULT '0.00'`로 되어 있어 일관성이 깨져 있다.

현재 builder default와 payment upsert의 `COALESCE(total_revenue, 0)` 때문에 일반 경로는 보호된다.
주/월 rollup의 `sumRevenue()`도 null-safe 처리됐다.
다만 실제 DDL의 `NOT NULL` 보정은 별도 migration이 필요하다.

---

### 🟢 Info

#### [I1] Today Snapshot 신뢰성

`getTodaySnapshot()`이 반환하는 "오늘" 데이터:

- 결제 없는 날: 레코드 없음 → 전부 0
- 결제 있는 날: payment 필드는 즉시 반영되고, user/care 지표는 다음 배치 전까지 0일 수 있음
- 배치 이후: "오늘" 아닌 "어제"까지의 데이터

오늘 실시간 활동 지표(DAU, 신규 게시글 등)를 볼 수 없는 구조다.

#### [I2] Cron 시각과 문서 불일치

현재 `@Scheduled(cron = "0 5 0 * * ?")` — 00:05 실행.
`DailyStatistics.java` Javadoc도 "매일 00:05"로 정정됐다.

---

## 3. 도메인 설계 문제

### 결제-통계 도메인 경계 위반

`recordPayment`는 Payment 도메인(에스크로 지급)에서 호출되지만, Statistics 도메인 엔티티(`DailyStatistics`)에 직접 결제 데이터를 누적한다. 이로 인해:

- Payment 도메인이 Statistics 내부 저장 구조에 의존
- Statistics 배치가 Payment 도메인 원천 데이터를 조회하지 않고 누적된 값을 신뢰
- C1 버그의 근본 원인

올바른 도메인 경계:

```
[Payment 도메인]  PetCoinTransaction / PetCoinEscrow 테이블에 결제 원천 기록 소유
[Statistics 배치] 배치 시 Payment 원천 테이블을 조건부 집계
```

현재 `recordPayment()` 호출은 코인 충전 전체가 아니라 `PetCoinEscrowService.releaseToProvider()`의
에스크로 지급 시점에서 발생한다. 따라서 "매출"의 의미가 에스크로 지급액이라면,
집계 쿼리는 단순 전체 transaction sum이 아니라 `PAYOUT`, `CARE_REQUEST`, `COMPLETED` 같은 조건을 명시해야 한다.

---

## 4. 적용된 개선

### C0 — login_events 기반 활성 사용자 집계

- `login_events` 테이블을 append-only 원천으로 도입.
- `AuthService.login()`, `OAuth2Service.processOAuth2Login()`에서 로그인 이벤트 저장.
- 일별 DAU, 주간 WAU, 월간 MAU 모두 `LoginEventRepository.countDistinctUsersBetween()`로 집계.
- DISTINCT 기준은 `Users.id` 문자열이 아니라 FK 대상 PK인 `Users.idx`.

```sql
SELECT COUNT(DISTINCT user_id)
FROM login_events
WHERE login_at BETWEEN :start AND :end;
```

### C1 — 배치 merge 전환

`StatisticsAggregator.aggregateForDate(date)`는 기존 daily row가 있어도 skip하지 않는다.
활동 지표는 항상 새로 집계해 덮어쓰고, 이미 결제 데이터가 있으면 revenue 필드는 보존한다.

### C2 — Aggregator 빈 분리 및 날짜별 트랜잭션 독립

`StatisticsScheduler`는 orchestration만 담당하고, 실제 일별 집계는 `StatisticsAggregator`가 수행한다.
`aggregateForDate()`는 `REQUIRES_NEW`라 backfill 중 한 날짜 실패가 다른 날짜 트랜잭션에 섞이지 않는다.

### C3/W3 — 결제 반영 native upsert

`recordPayment()`는 read-modify-write를 하지 않고 repository의 native upsert를 호출한다.

```sql
INSERT INTO dailystatistics (stat_date, total_revenue, transaction_count, avg_transaction)
VALUES (:date, :amount, 1, :amount)
ON DUPLICATE KEY UPDATE
    avg_transaction = (COALESCE(total_revenue, 0) + :amount) / (transaction_count + 1),
    total_revenue = COALESCE(total_revenue, 0) + :amount,
    transaction_count = transaction_count + 1;
```

동시에 `todayStats` 캐시를 evict해 `/summary` stale window를 줄였다.

### W2/W4 — 기타 보정

- ISO 53주차: `LocalDate.of(prevYear, 12, 28)` 기준으로 전년도 마지막 ISO 주차 계산.
- daily `total_revenue` nullable drift: rollup `sumRevenue()`를 null-safe 처리.

---

## 5. 남은 작업

| 순서 | 문제 | 난이도 | 영향 |
| ---- | ---- | ------ | ---- |
| 1 | 결제-통계 도메인 경계 정리: `recordPayment` 제거 후 Payment 원천 테이블 배치 집계 | 중간 | Statistics가 Payment 저장 구조와 느슨해짐 |
| 2 | daily `total_revenue`에 `NOT NULL DEFAULT 0.00` migration 적용 | 낮음 | 스키마 정합성 |
| 3 | 2026-06-28 이전 `active_users`는 정확한 보정 불가함을 관리자 화면/문서에 표시 | 낮음 | 과거 지표 해석 오류 방지 |
| 4 | 프론트 대시보드의 중첩 DTO/평면 필드 mismatch 수정 | 중간 | 관리자 화면 표시 정확성 |

---

## 6. 관련 문서

- `docs/domains/statistics.md` — Statistics 도메인 현행 스펙
- `docs/analysis/admin-statistics-domain-analysis.md` — 도메인 커버리지 분석 (구현 전 작성)
- `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- `docs/troubleshooting/payment/payment-troubleshooting-analysis.md`
