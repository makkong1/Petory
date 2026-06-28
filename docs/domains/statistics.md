# Statistics 도메인

> 기준: 현재 `domain/statistics`, `AdminStatisticsController`, `SystemDashboardSection`, `PetCoinEscrowService`, `RedisConfig` 코드.

Statistics 도메인은 Petory 운영 지표를 **일별, 주별, 월별 스냅샷**으로 저장하고 관리자 통계 API에 제공한다. 집계 원천은 User, Care, Board, Meetup, Report, Payment 도메인이고, 조회 API는 `AdminStatisticsController`를 통해 MASTER 권한 관리자에게만 열린다.

---

## 1. 책임과 범위

| 구분 | 내용 |
| --- | --- |
| 일별 집계 | 날짜별 신규 가입, 활성 사용자, 케어, 게시글, 모임, 신고, 매출 스냅샷 저장 |
| 주간 rollup | 일별 통계를 ISO 주차 기준으로 합산 |
| 월간 rollup | 일별 통계를 월 기준으로 합산 |
| 수동 backfill | 지정 기간의 누락 통계를 소급 집계 |
| 오늘 스냅샷 | 오늘 날짜의 `DailyStatistics`를 Redis 1분 캐시로 조회 |
| 결제 반영 | 케어 에스크로 지급 완료 시 당일 매출, 거래 수, 평균 거래액 반영 |

범위 밖:

- 실시간 분석 쿼리 엔진은 아니다. 운영 조회는 저장된 통계 row를 읽는다.
- MissingPet, Comment, File, Location review 지표는 현재 통계 모델에 포함되지 않는다.
- 관리자 감사 로그는 Admin 도메인 책임이고, 통계 API에는 감사 로그가 없다.

---

## 2. 코드 위치

```text
backend/main/java/com/linkup/Petory/domain/statistics/
  entity/
    DailyStatistics.java
    WeeklyStatistics.java
    MonthlyStatistics.java
  dto/
    DailyStatisticsResponse.java
    WeeklyStatisticsResponse.java
    MonthlyStatisticsResponse.java
    TodaySnapshotResponse.java
  repository/
    DailyStatisticsRepository.java
    JpaDailyStatisticsAdapter.java
    SpringDataJpaDailyStatisticsRepository.java
    WeeklyStatisticsRepository.java
    JpaWeeklyStatisticsAdapter.java
    SpringDataJpaWeeklyStatisticsRepository.java
    MonthlyStatisticsRepository.java
    JpaMonthlyStatisticsAdapter.java
    SpringDataJpaMonthlyStatisticsRepository.java
  service/
    StatisticsService.java
    StatisticsScheduler.java
```

관리자 API:

- `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminStatisticsController.java`

프론트 화면:

- `frontend/src/components/Admin/sections/SystemDashboardSection.js`
- `frontend/src/api/adminApi.js`

---

## 3. 엔티티

### DailyStatistics

실제 테이블명은 코드 기준 `dailystatistics`다.

| 필드 | 의미 |
| --- | --- |
| `statDate` | 집계 날짜, unique |
| `newUsers` | 해당 날짜 신규 가입자 수 |
| `activeUsers` | `lastLoginAt` 기준 활성 사용자 수 |
| `newProviders` | 신규 `SERVICE_PROVIDER` 수 |
| `newCareRequests` | 신규 케어 요청 수 |
| `completedCares` | `completedAt` 기준 완료 케어 수 |
| `cancelledCares` | `CANCELLED` 상태와 `updatedAt` 기준 취소 케어 수 |
| `careCompletionRate` | `completed / (completed + cancelled) * 100` |
| `totalRevenue` | 케어 에스크로 지급 완료 금액 합계 |
| `transactionCount` | 지급 완료 거래 수 |
| `avgTransaction` | 평균 거래 금액 |
| `newPosts` | 신규 커뮤니티 게시글 수 |
| `newMeetups` | 신규 모임 수 |
| `meetupParticipants` | 모임 참여 수 |
| `newReports` | 신규 신고 수 |
| `resolvedReports` | `RESOLVED` 상태와 `updatedAt` 기준 처리 신고 수 |

### WeeklyStatistics

실제 테이블명은 `weekly_statistics`이며 `(year, week_number)` 조합이 unique다. 일요일 기준으로 해당 주 월요일부터 일요일까지의 daily row를 합산한다.

추가 필드:

- `weeklyRetentionRate`: 현재 주 activeUsers 합계 / 이전 주 activeUsers 합계
- `startDate`, `endDate`: 해당 주 범위

### MonthlyStatistics

실제 테이블명은 `monthly_statistics`이며 `(year, month)` 조합이 unique다. 해당 월의 daily row를 합산한다.

추가 필드:

- `monthlyRetentionRate`: 현재 월 activeUsers 합계 / 이전 월 activeUsers 합계
- `churnRate`: `max(0, 100 - monthlyRetentionRate)`

---

## 4. 집계 로직

### 자동 배치

`StatisticsScheduler.aggregateDailyStatistics()`는 매일 18:00에 실행된다.

```text
매일 18:00
  -> yesterday = LocalDate.now().minusDays(1)
  -> 최근 7일 누락 daily 감지 및 backfill
  -> yesterday daily 집계
  -> yesterday가 일요일이면 weekly rollup
  -> yesterday가 월 마지막 날이면 monthly rollup
  -> 1년 초과 daily 삭제
```

집계 대상 repository:

| 지표 | Repository 메서드 |
| --- | --- |
| 신규 가입 | `UsersRepository.countByCreatedAtBetween` |
| 활성 사용자 | `UsersRepository.countByLastLoginAtBetween` |
| 신규 제공자 | `UsersRepository.countByRoleAndCreatedAtBetween` |
| 신규 케어 요청 | `CareRequestRepository.countByCreatedAtBetween` |
| 완료 케어 | `CareRequestRepository.countByCompletedAtBetween` |
| 취소 케어 | `CareRequestRepository.countByStatusAndUpdatedAtBetween(CANCELLED)` |
| 신규 게시글 | `BoardRepository.countByCreatedAtBetween` |
| 신규 모임 | `MeetupRepository.countByCreatedAtBetween` |
| 모임 참여 | `MeetupParticipantsRepository.countByJoinedAtBetween` |
| 신규 신고 | `ReportRepository.countByCreatedAtBetween` |
| 처리 신고 | `ReportRepository.countByStatusAndUpdatedAtBetween(RESOLVED)` |

### 중복 방지

`aggregateStatisticsForDate(date)`는 먼저 `findByStatDate(date)`를 확인한다. 이미 row가 있으면 집계를 건너뛴다.

이 정책은 같은 날짜를 두 번 집계하지 않는 장점이 있지만, `recordPayment()`나 수동 backfill이 먼저 빈 daily row를 만들면 이후 자동 배치가 해당 날짜의 활동 지표를 채우지 못할 수 있다.

---

## 5. 조회/쓰기 서비스

`StatisticsService`는 클래스 레벨 `@Transactional(readOnly = true)`이고, `recordPayment`, `backfill`만 쓰기 트랜잭션이다.

| 메서드 | 설명 |
| --- | --- |
| `getDailyStatistics(startDate, endDate)` | 날짜 범위의 daily row를 오름차순 조회. `startDate > endDate`면 `IllegalArgumentException` |
| `getWeeklyStatistics(year)` | 특정 연도의 weekly row를 주차 오름차순 조회 |
| `getMonthlyStatistics(year)` | 특정 연도의 monthly row를 월 오름차순 조회 |
| `getTodaySnapshot()` | 오늘 daily row를 조회하고 없으면 빈 `DailyStatistics`로 응답. Redis `todayStats::today` 1분 캐시 |
| `recordPayment(amount)` | 당일 매출, 거래 수, 평균 거래액 갱신 |
| `backfill(startDate, endDate)` | `StatisticsScheduler.backfill()`로 위임 |

결제 연동:

- `PetCoinEscrowService.releaseToProvider()`가 제공자 지급 후 `statisticsService.recordPayment(BigDecimal.valueOf(escrow.getAmount()))`를 호출한다.
- 환불 흐름은 `recordPayment()`를 호출하지 않는다.

---

## 6. API

`AdminStatisticsController`의 base path는 `/api/admin/statistics`이며 클래스 전체에 `@PreAuthorize("hasRole('MASTER')")`가 걸려 있다.

| Method | URL | 설명 | 파라미터 |
| --- | --- | --- | --- |
| GET | `/daily` | 일별 통계 조회 | `startDate`, `endDate` 선택. 기본은 최근 30일 |
| GET | `/weekly` | 연도별 주간 통계 조회 | `year` 선택. 기본은 현재 연도 |
| GET | `/monthly` | 연도별 월간 통계 조회 | `year` 선택. 기본은 현재 연도 |
| GET | `/summary` | 오늘 스냅샷 조회 | 없음 |
| POST | `/backfill` | 지정 기간 수동 집계 | `startDate`, `endDate` 필수 |

일별 응답은 평면 필드가 아니라 섹션별 중첩 구조다.

```json
{
  "statDate": "2026-06-19",
  "users": {
    "newUsers": 3,
    "activeUsers": 12,
    "newProviders": 1
  },
  "care": {
    "newRequests": 4,
    "completed": 2,
    "cancelled": 1,
    "completionRate": 66.67
  },
  "revenue": {
    "totalRevenue": 50000,
    "transactionCount": 2,
    "avgTransaction": 25000
  },
  "community": {
    "newPosts": 8,
    "newMeetups": 1,
    "meetupParticipants": 5
  },
  "moderation": {
    "newReports": 2,
    "resolvedReports": 1
  }
}
```

---

## 7. 프론트엔드 연결

현재 `SystemDashboardSection`은 다음 API만 직접 사용한다.

| 화면 동작 | 프론트 호출 | 백엔드 API |
| --- | --- | --- |
| 대시보드 초기 로드 | `adminApi.fetchDailyStatistics()` | `GET /api/admin/statistics/daily` |
| 통계 수동 집계 버튼 | `adminApi.initStatistics(30)` | `POST /api/admin/statistics/backfill?startDate=...&endDate=...` |

현재 프론트 코드에는 `/summary`, `/weekly`, `/monthly` 호출이 없다.

연동 갭:

- 백엔드 daily 응답은 `users.newUsers`, `community.newPosts`, `revenue.totalRevenue`처럼 중첩 구조다.
- 현재 `SystemDashboardSection`은 `latest.newUsers`, `latest.newPosts`, `latest.totalRevenue`처럼 평면 필드를 읽는다.
- `adminApi`에서 응답을 평면 구조로 변환하지 않으므로, 현재 화면의 요약 카드와 차트 값이 `undefined` 또는 0처럼 보일 수 있다.

---

## 8. Redis 캐시

`getTodaySnapshot()`에만 Spring Cache가 적용된다.

| 캐시명 | 키 | TTL | 설정 위치 |
| --- | --- | --- | --- |
| `todayStats` | `'today'` | 1분 | `RedisConfig.cacheManager()` |

주의:

- `recordPayment()`는 `todayStats` 캐시를 직접 evict하지 않는다.
- `/summary`가 캐시된 직후 결제가 반영되면 최대 1분 동안 오래된 매출이 보일 수 있다.
- 현재 프론트는 `/summary`를 호출하지 않으므로 이 캐시는 백엔드 제공 API 기준의 캐시다.

---

## 9. 현재 한계 및 알려진 버그

### 🔴 Critical

**[DAU 원천 데이터 오류]** — 모든 활성 사용자 지표의 기반이 틀림

`activeUsers`는 `Users.lastLoginAt BETWEEN start AND end`로 집계한다.
그런데 `lastLoginAt`은 로그인마다 현재 시각으로 **단순 덮어쓰기**된다(`AuthService.java:64`).
배치가 18:00에 실행되므로, Day N에 로그인 후 Day N+1 09:00에 다시 로그인한 사용자는
`lastLoginAt`이 Day N+1로 바뀌어 Day N DAU 쿼리 범위에서 이탈한다.
매일 접속하는 충성 사용자일수록 DAU에서 빠질 가능성이 높다.
근본 해결: 로그인 이벤트를 별도 `login_events` 테이블에 append 후 `DISTINCT user_id` count.

**[결제-배치 충돌 → 활동 지표 영구 유실]** — 가장 심각

결제가 발생한 날 `recordPayment()`가 먼저 `DailyStatistics` row를 생성한다.
다음날 18:00 배치가 같은 날짜를 조회하면 이미 row가 존재하므로 skip한다.
결과: 해당 날의 `newUsers`, `activeUsers`, `newCareRequests` 등 모든 활동 지표가 영구 0.

```
Day N — 결제 발생 → recordPayment() → DailyStatistics(N) 생성 (payment 필드만 있음)
Day N+1 18:00 → aggregateStatisticsForDate(N) → 이미 있음 → skip
→ Day N은 payment 데이터만 있고 활동 지표 전부 0
```

**[Self-invocation으로 @Transactional 무력화]**

`StatisticsScheduler.backfill()`, `detectAndBackfillMissing()` 내부에서
`aggregateStatisticsForDate()`를 `this.method()` 형태로 호출.
Spring 프록시를 타지 않아 각 날짜별 트랜잭션 격리가 동작하지 않는다.

**[recordPayment 레이스 컨디션]**

동시 결제 진입 시 read-modify-write에 비관적 락이 없어 `totalRevenue` 유실 가능.

### 🟡 Warning

**[WAU/MAU = DAU 합산]** — 비즈니스 지표 왜곡

`weeklyRetentionRate`, `monthlyRetentionRate`는 DAU 합산값끼리의 비율이며
실제 WAU(고유 주간 활성 사용자) 대비 비율이 아니다.
7일 모두 접속한 사용자 1명이 WAU=7로 집계된다.

**[ISO 53주차 미처리]**

`calcWeeklyRetention`에서 전 주 계산 시 `prevWeek = 52` 하드코딩.
2020, 2026년 등 53주차가 있는 연도에서 마지막 주 retention이 오동작한다.

### 🟢 Info

3. 일별 테이블명은 `dailystatistics`이고 주/월 테이블명은 snake case라 명명 규칙이 일관되지 않다.
4. 관리자 통계 API에는 AdminAuditLog가 남지 않는다.
5. 프론트 대시보드는 백엔드 중첩 DTO를 평면 필드처럼 읽고 있다.
6. MissingPet, Comment, Location review, File 사용량은 현재 통계 지표에 없다.
7. `StatisticsScheduler` cron이 18:00이지만 `DailyStatistics` Javadoc에는 "매일 자정"으로 기술돼 있다.

---

## 10. DAU 원천 전환 이력 (2026-06-28)

### 배경
`Users.lastLoginAt`은 로그인마다 덮어쓰이므로, 하루에 2회 이상 로그인한 사용자가 배치 실행 전에
다시 로그인하면 전날 DAU에서 누락된다 (C0 버그 — `statistics-bug-fix` Step 5에서 cron 00:05로 임시 완화).

### 수정 내용 (statistics-login-events)
- `login_events` 테이블 신설 (append-only, 로그인 1회 = 행 1개)
- 인덱스: `(user_id, login_at)`, `(login_at)` 복합/단일 인덱스
- `AuthService.login()`, `OAuth2Service.processOAuth2Login()` 두 진입점에서 `LoginEvent` append 저장
- `StatisticsAggregator.activeUsers` 집계 변경: `Users.lastLoginAt` → `COUNT(DISTINCT login_events.user_id)`

### 보정 불가 범위
- **도입 이전 (~ 2026-06-27) 일별 통계의 `active_users`**: 보정 불가.
  - `Users.lastLoginAt`은 마지막 로그인 시각만 남기므로 역산 불가.
  - 과거 `active_users` 값은 하루 2회 이상 로그인 사용자가 누락된 과소 집계임.
  - 주간/월간 `active_users`도 DAU 합산 기반이므로 동일하게 과소 집계됨.
- 관리자 대시보드에서 2026-06-28 이전 `active_users`를 "추정값"으로 레이블 처리 권장.

### WAU/MAU activeUsers 잔존 한계
현재 WAU/MAU `activeUsers` = 해당 기간 DAU 합산 (DISTINCT 아님).
진정한 주/월 DISTINCT는 `login_events`를 기간 단위로 직접 GROUP BY해야 한다.
이 개선은 별도 태스크(`statistics-wau-mau-distinct`)로 분리한다.

---

## 11. 관련 문서

- `docs/architecture/관리자 대시보드 & 통계 시스템 아키텍처.md`
- `docs/architecture/Redis_캐싱_전략.md`
- `docs/domains/admin.md`
- `docs/domains/payment.md`
- `docs/refactoring/statistics/statistics-domain-review-2026-06-28.md` — 전체 리뷰 결과 및 개선 방향
