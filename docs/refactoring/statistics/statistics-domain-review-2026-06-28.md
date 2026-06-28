# Statistics 도메인 리뷰 — 2026-06-28

> 대상 파일: `StatisticsScheduler.java`, `StatisticsService.java`, `DailyStatistics.java`
> 리뷰 관점: 기술적 결함 + 도메인 설계 정합성

---

## 1. 확인된 코드 사실

| 사실 | 위치 |
|------|------|
| 스케줄러 실행 시각: 18:00 (`0 0 18 * * ?`) | `StatisticsScheduler.java:60` |
| 집계 대상: yesterday = `now().minusDays(1)` | `StatisticsScheduler.java:63` |
| activeUsers 집계 쿼리: `countByLastLoginAtBetween(start, end)` | `StatisticsScheduler.java:99` |
| `lastLoginAt`: 로그인마다 현재 시각으로 단순 덮어쓰기 | `AuthService.java:64` |
| 중복 방지 조건: `findByStatDate(date).isPresent()` → skip | `StatisticsScheduler.java:84` |
| `recordPayment`: 오늘 날짜 DailyStatistics에 결제 데이터 누적 | `StatisticsService.java:83` |
| `aggregateStatisticsForDate`: revenue 3개 필드 전부 `BigDecimal.ZERO` 하드코딩 | `StatisticsScheduler.java:105-107` |
| `backfill` 안 `aggregateStatisticsForDate` 호출: self-invocation | `StatisticsScheduler.java:220` |
| `detectAndBackfillMissing` 안 호출: self-invocation | `StatisticsScheduler.java:237` |
| weekly retention: `prevWeek = 52` 하드코딩 | `StatisticsScheduler.java:278` |
| WAU = `sumLong(days, DailyStatistics::getActiveUsers)` — DAU 합산 | `StatisticsScheduler.java:138` |
| MAU = `sumLong(days, DailyStatistics::getActiveUsers)` — DAU 합산 | `StatisticsScheduler.java:184` |

### 1.1 확인된 실제 테이블 사실

아래 내용은 로컬 MySQL에서 `SHOW CREATE TABLE ...`, `SHOW INDEX FROM dailystatistics`로 확인한 실제 스키마 기준이다.

| 사실 | 실제 DDL 근거 |
|------|---------------|
| `dailystatistics.stat_date`는 unique | `UNIQUE KEY stat_date (stat_date)` |
| daily 활동 지표는 대부분 `NOT NULL DEFAULT 0` | `new_users`, `active_users`, `new_care_requests` 등 |
| daily `active_users` 컬럼 comment는 `DAU` | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'DAU'` |
| weekly `active_users` 컬럼 comment는 `WAU` | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'WAU'` |
| monthly `active_users` 컬럼 comment는 `MAU` | `active_users bigint NOT NULL DEFAULT '0' COMMENT 'MAU'` |
| daily 인덱스는 PK와 `stat_date` unique뿐 | `PRIMARY`, `stat_date` |
| daily `total_revenue`만 `NOT NULL`이 없음 | `total_revenue decimal(15,2) DEFAULT '0.00'` |

이 테이블 구조상 결제가 만든 daily row와 배치가 만든 daily row를 구분할 컬럼이 없다.
활동 지표가 전부 0이어도 DB 레벨에서는 정상 row로 보이므로 C1이 실제 스키마에서도 그대로 재현된다.

---

## 2. 발견된 문제

### 🔴 Critical

#### [C0] DAU 원천 데이터 자체가 틀림 — lastLoginAt 단일 컬럼 덮어쓰기

**문제**: 로그인할 때마다 `users.last_login_at`이 현재 시각으로 덮어 쓰인다.
배치가 18:00에 실행되므로, 어제 로그인 후 오늘 18:00 전에 다시 로그인한 사용자는
어제 DAU 쿼리 범위(`lastLoginAt BETWEEN yesterday 00:00 AND yesterday 23:59`)에서 이탈한다.

**재현 흐름**:

```
Day N  10:00 — 사용자 A 로그인 → lastLoginAt = Day N 10:00
Day N+1 09:00 — 사용자 A 로그인 → lastLoginAt = Day N+1 09:00  ← 덮어쓰기
Day N+1 18:00 — 배치 실행 (yesterday = Day N)
  → countByLastLoginAtBetween(Day N 00:00, Day N 23:59)
  → 사용자 A: lastLoginAt = Day N+1 09:00 → 범위 밖 → DAU 미집계
```

**영향**: 매일 접속하는 활성 사용자일수록 DAU에서 누락될 가능성이 높다.
이 오류가 WAU/MAU(W1)의 기반 데이터이므로 오염이 두 단계로 쌓인다.

**근본 원인**: DAU는 "해당 날 로그인한 이벤트 수"를 세야 하는데,
단일 컬럼 `lastLoginAt`은 **마지막 로그인 시각만 보존**해 이벤트 이력이 없다.

**해결 방향**: 로그인 이벤트를 별도 테이블에 append:

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

또는 단기 대안: 배치 실행 시각을 자정(00:05)으로 옮겨 당일 재로그인 전에 집계.
단, 근본 해결이 아니라 race window만 줄임.

---

#### [C1] 결제 발생 시 해당 날 활동 지표 영구 누락

**문제**: 결제가 하루라도 발생하면 그날의 `newUsers`, `activeUsers`, `newCareRequests` 등 모든 활동 지표가 영원히 0으로 남는다.

**재현 시나리오**:
```
Day N — 결제 발생
  → recordPayment()
  → DailyStatistics(statDate=N) INSERT
    (totalRevenue=50000, 나머지 필드 전부 0)

Day N+1, 18:00 — 배치 실행 (yesterday = Day N)
  → aggregateStatisticsForDate(N)
  → findByStatDate(N).isPresent() == true  ← 결제가 만든 row
  → "이미 존재합니다. 건너뜁니다."

결과: Day N은 payment 데이터만 있고
      newUsers, activeUsers, newCareRequests 등 영구 0
```

**근본 원인**: `DailyStatistics` 하나가 두 역할을 맡는다.
- `recordPayment`: 실시간 결제 누적기 (오늘)
- `aggregateStatisticsForDate`: 배치 스냅샷 (어제)

같은 "존재 여부" 가드가 두 흐름을 충돌시킨다.
실제 테이블도 `stat_date` unique 하나로 하루 row를 식별하고, 집계 완료 여부를 나타내는 상태 컬럼이 없어
payment-only row와 batch snapshot row를 구분할 수 없다.

---

#### [C2] Self-invocation으로 @Transactional 무력화 (B5)

`StatisticsScheduler` 내에서 `aggregateStatisticsForDate`를 `this.method()` 형태로 3곳 호출.
Spring AOP 프록시를 타지 않아 `aggregateStatisticsForDate`의 `@Transactional`이 실제로 동작하지 않는다.

| 호출 위치 | 경로 |
|-----------|------|
| `aggregateDailyStatistics()` | 직접 호출 (line 66) |
| `detectAndBackfillMissing()` | private → 내부 호출 (line 237) |
| `backfill()` | forEach 내부 호출 (line 220) |

**결과**: backfill 30일치가 하나의 트랜잭션에 묶임. 중간 날짜 실패 시 전체 롤백.
또한 이미 존재하는 잘못된 daily row를 보정하지 못하므로 C1로 오염된 row는 수동 backfill로도 복구되지 않는다.

---

#### [C3] recordPayment 동시성 — 레이스 컨디션 (B3)

동시 결제 다수 진입 시 read-then-write 패턴에 lock이 없어 `totalRevenue`, `transactionCount` 유실 발생.

```java
// StatisticsService.java:85 — lock 없는 일반 SELECT
DailyStatistics stats = dailyStatisticsRepository.findByStatDate(today)
        .orElse(DailyStatistics.builder().statDate(today).build());
```

실제 `dailystatistics` 인덱스는 `PRIMARY`, `stat_date UNIQUE`뿐이다.
동시 insert 경합은 duplicate key 예외로, 동시 update 경합은 lost update로 이어질 수 있다.

---

### 🟡 Warning

#### [W1] WAU / MAU = DAU 합산 → Retention 지표 왜곡

```
월요일~일요일 7일 매일 접속한 사용자 1명 → WAU = 7
weeklyRetentionRate = 현재WAU / 이전WAU = 의미 없는 비율
```

"Retention Rate"라는 이름과 달리 실제로는 **DAU 평균 빈도 비율**이다.
진짜 WAU = 해당 주에 한 번이라도 로그인한 DISTINCT user 수여야 한다.

실제 테이블 comment는 weekly `active_users`를 `WAU`, monthly `active_users`를 `MAU`라고 명시한다.
따라서 단순 네이밍 문제가 아니라 **스키마 의미와 구현 의미가 충돌**한다.

---

#### [W2] ISO 53주차 미처리

```java
// StatisticsScheduler.java:278
if (prevWeek == 0) {
    prevYear--;
    prevWeek = 52; // 2020, 2026 등 53주차 연도에서 오류
}
```

53주차가 있는 연도(ISO 8601: 목요일이 포함된 12월 28일~1월 3일 기준)에서 52주차 데이터를 조회해 retention 계산이 잘못된다.

---

#### [W3] 동시 INSERT 시 DataIntegrityViolationException 미처리

`stat_date`는 unique 제약이 있으나 `findByStatDate` 체크 → `save` 사이에 동시 실행 시 DB 레벨에서 Duplicate key 예외가 발생하고 상위로 전파된다.

#### [W4] daily `total_revenue` nullable 스키마 드리프트

실제 DDL에서 daily `total_revenue`는 `DEFAULT '0.00'`만 있고 `NOT NULL`이 없다.
weekly/monthly의 `total_revenue`는 `NOT NULL DEFAULT '0.00'`로 되어 있어 일관성이 깨져 있다.

현재 builder default 때문에 일반 경로에서는 0이 들어가지만, DB 직접 보정이나 과거 migration 경로에서 null이 들어가면
`sumRevenue()`나 DTO 변환에서 null 취급 문제가 생길 수 있다.

---

### 🟢 Info

#### [I1] Today Snapshot 신뢰성

`getTodaySnapshot()`이 반환하는 "오늘" 데이터:
- 결제 없는 날: 레코드 없음 → 전부 0
- 결제 있는 날: payment 필드만 있고 user/care 지표 0
- 배치 이후: "오늘" 아닌 "어제"까지의 데이터

오늘 실시간 활동 지표(DAU, 신규 게시글 등)를 볼 수 없는 구조다.

#### [I2] Cron 시각과 문서 불일치

`@Scheduled(cron = "0 0 18 * * ?")` — 18:00 실행.
`DailyStatistics.java` Javadoc: "매일 자정 스케줄러가 전날 데이터를 생성한다."

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

## 4. 개선 방향

### Option A — 결제 집계를 배치로 이관 (권장)

`recordPayment` 제거. `aggregateStatisticsForDate`에서 Payment 도메인 레포지토리를 직접 쿼리.

```java
// StatisticsScheduler에 PaymentRepository 주입 추가
private final PetCoinTransactionRepository petCoinTransactionRepository;

// aggregateStatisticsForDate 내부
.totalRevenue(petCoinTransactionRepository.sumCompletedPayoutAmountByCreatedAtBetween(start, end)
        .orElse(BigDecimal.ZERO))
.transactionCount(petCoinTransactionRepository.countCompletedPayoutByCreatedAtBetween(start, end))
```

**장점**: DailyStatistics가 배치 스냅샷 단일 역할. C1 버그 근본 해결.  
**비용**: Payment 도메인에 집계 쿼리 추가 필요. 기존 `recordPayment` 호출 제거.  
**주의**: 현재 호출 의미 기준으로는 `TransactionType.PAYOUT`, `relatedType = CARE_REQUEST`,
`status = COMPLETED` 등 도메인 조건을 명확히 해야 한다.

---

### DAU 원천 수정 (C0)

`Users.lastLoginAt`은 최신 로그인 시각만 보존하므로 과거 일자 DAU의 원천으로 부적합하다.
정확한 DAU/WAU/MAU를 위해 로그인 이벤트를 append-only로 남겨야 한다.

```sql
CREATE TABLE login_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_at DATETIME NOT NULL,
    INDEX idx_login_events_login_at_user (login_at, user_id)
);
```

```sql
-- DAU
SELECT COUNT(DISTINCT user_id)
FROM login_events
WHERE login_at BETWEEN :start AND :end;

-- WAU / MAU
SELECT COUNT(DISTINCT user_id)
FROM login_events
WHERE login_at BETWEEN :periodStart AND :periodEnd;
```

단기 대안으로 배치 시각을 00:05로 당길 수는 있지만, 재로그인 race window만 줄일 뿐 근본 해결은 아니다.

---

### Option B — 중복 방지 로직 수정 (최소 변경)

`aggregateStatisticsForDate`에서 "존재하면 skip" 대신 "존재하면 활동 지표만 UPDATE":

```java
public void aggregateStatisticsForDate(LocalDate date) {
    DailyStatistics stats = dailyStatisticsRepository.findByStatDate(date)
            .orElse(DailyStatistics.builder().statDate(date).build());

    // 활동 지표는 항상 집계
    stats.setNewUsers(usersRepository.countByCreatedAtBetween(start, end));
    stats.setActiveUsers(usersRepository.countByLastLoginAtBetween(start, end));
    // ... 나머지 활동 지표

    // revenue 필드는 이미 값이 있으면 보존 (recordPayment가 채운 것)
    if (stats.getTotalRevenue().compareTo(BigDecimal.ZERO) == 0) {
        // 결제 데이터 없는 경우만 0 유지 (현재와 동일)
    }

    dailyStatisticsRepository.save(stats);
}
```

**장점**: 최소 변경. 즉시 C1 버그 차단.  
**단점**: 도메인 경계 문제와 C0 DAU 원천 문제는 그대로. 장기적으로 Option A와 로그인 이벤트 테이블로 이행 필요.

---

### Self-invocation 수정 (C2)

`aggregateStatisticsForDate`를 별도 빈으로 분리:

```java
@Service
@RequiredArgsConstructor
public class StatisticsAggregator {
    @Transactional
    public void aggregateForDate(LocalDate date) { ... }
}

// StatisticsScheduler
private final StatisticsAggregator aggregator;

// 이제 크로스-빈 호출 → 프록시 정상 동작
aggregator.aggregateForDate(yesterday);
```

---

### WAU / MAU 수정 (W1)

진짜 WAU/MAU를 구하려면 로그인 이벤트 원천에서 기간 내 DISTINCT user를 세야 한다.
현재 구조상 단기간에 수정이 어렵다면 **필드명/문서/테이블 comment라도 실제 의미를 정확히 반영**해야 한다:

```java
// 현재: activeUsers (WAU처럼 보임)
// 실제: DAU 합산값
// 권장 임시 조치: Javadoc에 "DAU 합산 (DISTINCT WAU 아님)" 명시
```

---

## 5. 우선순위

| 순서 | 문제 | 난이도 | 영향 |
|------|------|--------|------|
| 1 | C1: 결제-배치 충돌 (Option B 단기 / A 장기) | 낮음 / 중간 | 매일 데이터 유실 |
| 2 | C0: `lastLoginAt` 기반 DAU 원천 오류 | 중간 | 모든 활성 사용자 지표 오염 |
| 3 | C2: Self-invocation 빈 분리 | 낮음 | 트랜잭션 격리 |
| 4 | C3: recordPayment lost update / duplicate key race | 낮음 | 동시 결제 유실 |
| 5 | W1: WAU/MAU가 DAU 합산인데 테이블 comment는 WAU/MAU | 높음 | 지표 의미 불일치 |
| 6 | W2: ISO 53주차 | 매우 낮음 | 연말 retention 오류 |
| 7 | W4: daily `total_revenue` nullable drift | 낮음 | 스키마 정합성 |

---

## 6. 관련 문서

- `docs/domains/statistics.md` — Statistics 도메인 현행 스펙
- `docs/analysis/admin-statistics-domain-analysis.md` — 도메인 커버리지 분석 (구현 전 작성)
- `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- `docs/troubleshooting/payment/payment-troubleshooting-analysis.md`
