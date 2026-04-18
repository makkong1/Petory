# Statistics 도메인

## 1. 개요

Statistics 도메인은 **일별 / 주별 / 월별 3단계 집계 구조**로 운영 모니터링과 비즈니스 의사결정을 지원합니다. 스케줄러가 매일 daily 집계를 수행하고, 주말·월말에 rollup 집계를 자동 실행합니다. 매출은 에스크로 완료 이벤트로 즉시 반영됩니다.

**주요 기능**:
- 일별 통계 자동 집계 (매일 18:00 배치)
- 주간 / 월간 rollup 집계
- 누락 날짜 자동 감지 및 backfill
- 매출 이벤트 즉시 반영 (`PetCoinEscrowService` 연동)
- 1년 경과 daily 데이터 자동 삭제
- Redis 1분 캐시 기반 오늘 스냅샷 조회

---

## 2. 엔티티 구조

### 2.1 DailyStatistics (`daily_statistics`, 1년 보관)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| statDate | LocalDate | 집계 날짜 (UNIQUE) |
| newUsers | Long | 신규 가입자 |
| activeUsers | Long | DAU |
| newProviders | Long | 신규 서비스 제공자 |
| newCareRequests | Long | 케어 요청 수 |
| completedCares | Long | 케어 완료 수 |
| cancelledCares | Long | 케어 취소 수 |
| careCompletionRate | BigDecimal(5,2) | 완료/(완료+취소)×100 |
| totalRevenue | BigDecimal(15,2) | 일 매출 (이벤트 즉시 반영) |
| transactionCount | Long | 결제 건수 |
| avgTransaction | BigDecimal(15,2) | 평균 거래금액 |
| newPosts | Long | 신규 게시글 |
| newMeetups | Long | 신규 모임 |
| meetupParticipants | Long | 모임 참여자 수 |
| newReports | Long | 신고 접수 |
| resolvedReports | Long | 신고 처리 수 |

### 2.2 WeeklyStatistics (`weekly_statistics`, 무기한)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| year | Integer | ISO 연도 |
| weekNumber | Integer | ISO 주차 (1~53) |
| startDate | LocalDate | 해당 주 월요일 |
| endDate | LocalDate | 해당 주 일요일 |
| weeklyRetentionRate | BigDecimal(5,2) | 주간 재방문율 |
| (daily와 동일한 집계 컬럼들) | | daily 합산 |
| UNIQUE | (year, weekNumber) | |

### 2.3 MonthlyStatistics (`monthly_statistics`, 무기한)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| year | Integer | 연도 |
| month | Integer | 월 (1~12) |
| monthlyRetentionRate | BigDecimal(5,2) | 월간 재방문율 |
| churnRate | BigDecimal(5,2) | 이탈율 (100 - retention) |
| (daily와 동일한 집계 컬럼들) | | daily 합산 |
| UNIQUE | (year, month) | |

### 2.4 도메인 구조
```
domain/statistics/
  ├── entity/
  │   ├── DailyStatistics.java
  │   ├── WeeklyStatistics.java
  │   └── MonthlyStatistics.java
  ├── repository/
  │   ├── DailyStatisticsRepository.java (인터페이스)
  │   ├── JpaDailyStatisticsAdapter.java
  │   ├── SpringDataJpaDailyStatisticsRepository.java
  │   ├── WeeklyStatisticsRepository.java
  │   ├── JpaWeeklyStatisticsAdapter.java
  │   ├── SpringDataJpaWeeklyStatisticsRepository.java
  │   ├── MonthlyStatisticsRepository.java
  │   ├── JpaMonthlyStatisticsAdapter.java
  │   └── SpringDataJpaMonthlyStatisticsRepository.java
  ├── service/
  │   ├── StatisticsService.java
  │   └── StatisticsScheduler.java
  └── dto/
      ├── DailyStatisticsResponse.java
      ├── WeeklyStatisticsResponse.java
      ├── MonthlyStatisticsResponse.java
      └── TodaySnapshotResponse.java
```

---

## 3. 비즈니스 로직

### 3.1 StatisticsScheduler

#### 일별 집계 (`aggregateStatisticsForDate`)
- 중복 방지: `findByStatDate` 선확인 → 이미 존재하면 skip
- 집계 항목: 신규가입/DAU/신규제공자/케어요청/완료케어/취소케어/완료율/게시글/모임/참여자/신고/처리신고
- careCompletionRate = `completed / (completed + cancelled) × 100`
- totalRevenue는 배치에서 0으로 초기화 (에스크로 이벤트로 실시간 누적됨)

#### 배치 트리거 (`aggregateDailyStatistics`, 매일 18:00)
1. 최근 7일 누락 날짜 감지 → 자동 backfill
2. 어제 daily 집계
3. 어제가 일요일이면 → `rollupWeekly()` 실행
4. 어제가 월 마지막 날이면 → `rollupMonthly()` 실행
5. 1년 이전 daily 데이터 삭제

#### 주간 rollup (`rollupWeekly`)
- 해당 주 월요일~일요일의 daily 데이터 합산
- weeklyRetentionRate = 이번 주 WAU / 지난 주 WAU × 100

#### 월간 rollup (`rollupMonthly`)
- 해당 월의 daily 데이터 합산
- monthlyRetentionRate = 이번 달 MAU / 지난 달 MAU × 100
- churnRate = 100 - monthlyRetentionRate (음수 방지: max 0)

#### backfill (`backfill(startDate, endDate)`)
- 지정 기간의 날짜를 순회하며 `aggregateStatisticsForDate` 호출
- 각 날짜별로 예외 격리 (한 날짜 실패가 전체를 중단하지 않음)

### 3.2 StatisticsService

| 메서드 | 설명 |
|--------|------|
| `getDailyStatistics(startDate, endDate)` | 기간별 daily 통계 조회. startDate > endDate 시 IllegalArgumentException |
| `getWeeklyStatistics(year)` | 연도별 주간 통계 전체 조회 |
| `getMonthlyStatistics(year)` | 연도별 월간 통계 전체 조회 |
| `getTodaySnapshot()` | 오늘 daily 행 조회 (없으면 빈 엔티티). Redis 1분 캐시 |
| `recordPayment(amount)` | 당일 totalRevenue/transactionCount/avgTransaction 업데이트. @Transactional |
| `backfill(startDate, endDate)` | StatisticsScheduler.backfill() 위임 |

### 3.3 매출 이벤트 연동 (PetCoinEscrowService)
`PetCoinEscrowService.releaseToProvider()` — 제공자에게 코인 지급 완료 직후 `statisticsService.recordPayment(BigDecimal.valueOf(escrow.getAmount()))` 호출.

### 3.4 트랜잭션
- `StatisticsService`: 클래스 `@Transactional(readOnly = true)`, `recordPayment`·`backfill`만 `@Transactional`
- `StatisticsScheduler`: 집계 메서드 전부 `@Transactional`

---

## 4. API

`AdminStatisticsController` (`/api/admin/statistics`), **전체 `@PreAuthorize("hasRole('MASTER')")`**

| Method | URL | 설명 | Query Params |
|--------|-----|------|------|
| GET | `/daily` | 기간별 daily 통계 | `startDate`, `endDate` (기본: 최근 30일) |
| GET | `/weekly` | 연도별 주간 통계 | `year` (기본: 올해) |
| GET | `/monthly` | 연도별 월간 통계 | `year` (기본: 올해) |
| GET | `/summary` | 오늘 실시간 스냅샷 (Redis 1분 캐시) | - |
| POST | `/backfill` | 기간 backfill | `startDate`, `endDate` (required) |

### 응답 구조 (DailyStatisticsResponse)
```json
{
  "statDate": "2026-04-17",
  "users": { "newUsers": 12, "activeUsers": 340, "newProviders": 3 },
  "care": { "newRequests": 25, "completed": 18, "cancelled": 4, "completionRate": 81.82 },
  "revenue": { "totalRevenue": 450000, "transactionCount": 18, "avgTransaction": 25000 },
  "community": { "newPosts": 47, "newMeetups": 5, "meetupParticipants": 32 },
  "moderation": { "newReports": 2, "resolvedReports": 1 }
}
```

---

## 5. 데이터 보관 정책

| 테이블 | 보관 기간 | 삭제 방식 |
|--------|---------|---------|
| daily_statistics | 1년 | 배치 실행 시 `deleteByStatDateBefore(now - 1년)` |
| weekly_statistics | 무기한 | - |
| monthly_statistics | 무기한 | - |

---

## 6. 캐시 전략

| 캐시 키 | TTL | 대상 |
|---------|-----|------|
| `todayStats::today` | 1분 | `getTodaySnapshot()` |

---

## 7. 성능 최적화

- **배치 집계**: 실시간 집계 쿼리 부하 없음. 대시보드 조회 = 단순 SELECT
- **Integer → Long**: 오버플로우 방지
- **careCompletionRate 사전 계산**: 조회 시 연산 없이 저장된 값 바로 사용
- **누락 감지**: 최근 7일만 체크하여 backfill 범위 최소화

---

## 8. 관련 문서

- 아키텍처: `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- 마이그레이션: `backend/main/resources/sql/migration/statistics-redesign.sql`
