# 통계 도메인 재설계 스펙

**날짜:** 2026-04-18  
**상태:** 승인됨  
**목적:** 운영 모니터링 + 비즈니스 의사결정 지원

---

## 1. 배경 및 목표

### 현재 문제
- 단일 `DailyStatistics` 테이블에 9개 카운터만 존재
- 주/월별 집계 없음 (앱에서 합산 필요)
- `total_revenue` 항상 0 (결제 도메인 미연결)
- 비율 지표 없음 (완료율, 리텐션 등)
- 지표 추가 시 스키마 변경 + backfill 필요
- Integer 오버플로우 위험

### 목표
- 일별 / 주별 / 월별 3단계 집계 구조
- 비율 지표, 매출, 사용자 세그먼트 포함
- 매출은 이벤트 즉시 반영, 나머지는 배치
- 누락 날짜 자동 감지 및 backfill

---

## 2. 데이터 모델

### 2.1 `daily_statistics` (일별, 1년 보관)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| stat_date | DATE UNIQUE NOT NULL | 집계 날짜 |
| new_users | BIGINT DEFAULT 0 | 신규 가입자 |
| active_users | BIGINT DEFAULT 0 | DAU |
| new_providers | BIGINT DEFAULT 0 | 신규 서비스 제공자 |
| new_care_requests | BIGINT DEFAULT 0 | 케어 요청 수 |
| completed_cares | BIGINT DEFAULT 0 | 케어 완료 수 |
| cancelled_cares | BIGINT DEFAULT 0 | 케어 취소 수 |
| care_completion_rate | DECIMAL(5,2) DEFAULT 0 | 완료/(완료+취소) × 100 |
| total_revenue | DECIMAL(15,2) DEFAULT 0 | 일 매출 (이벤트 즉시 반영) |
| transaction_count | BIGINT DEFAULT 0 | 결제 건수 |
| avg_transaction | DECIMAL(15,2) DEFAULT 0 | 평균 거래금액 |
| new_posts | BIGINT DEFAULT 0 | 신규 게시글 |
| new_meetups | BIGINT DEFAULT 0 | 신규 모임 |
| meetup_participants | BIGINT DEFAULT 0 | 모임 참여자 수 |
| new_reports | BIGINT DEFAULT 0 | 신고 접수 |
| resolved_reports | BIGINT DEFAULT 0 | 신고 처리 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### 2.2 `weekly_statistics` (주별, 무기한)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| year | INT NOT NULL | |
| week_number | INT NOT NULL | ISO 주차 (1~53) |
| start_date | DATE NOT NULL | 해당 주 월요일 |
| end_date | DATE NOT NULL | 해당 주 일요일 |
| weekly_retention_rate | DECIMAL(5,2) DEFAULT 0 | 주간 재방문율 |
| (daily_statistics와 동일 집계 컬럼들) | | daily 합산 |
| UNIQUE (year, week_number) | | |

### 2.3 `monthly_statistics` (월별, 무기한)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| year | INT NOT NULL | |
| month | INT NOT NULL | 1~12 |
| monthly_retention_rate | DECIMAL(5,2) DEFAULT 0 | 월간 재방문율 |
| churn_rate | DECIMAL(5,2) DEFAULT 0 | 이탈율 |
| (daily_statistics와 동일 집계 컬럼들) | | daily 합산 |
| UNIQUE (year, month) | | |

---

## 3. 집계 전략

### 3.1 배치 (Batch)

```
Cron: 0 5 0 * * ?  (매일 00:05)

1. 어제 날짜로 daily_statistics 집계 (9개 → 14개 항목)
2. 어제가 주 마지막 날(일요일)이면 weekly_statistics 롤업
3. 어제가 월 마지막 날이면 monthly_statistics 롤업

롤업 방식:
- count 항목: daily 합산
- rate 항목: 재계산 (합산 불가)
- 비율 (care_completion_rate): completed / (completed + cancelled) × 100
- 리텐션: 해당 기간 재방문 유저 / 기간 시작 시 활성 유저 × 100
- churn_rate: 1 - retention_rate
```

### 3.2 이벤트 드리븐 (매출만)

```
결제 완료 이벤트 발생 시:
→ 당일 daily_statistics.total_revenue += 결제금액
→ transaction_count += 1
→ avg_transaction = total_revenue / transaction_count
```

결제 도메인의 서비스에서 `StatisticsService.recordPayment()` 호출.

### 3.3 누락 감지 및 자동 Backfill

```
배치 실행 시:
→ 최근 7일 중 stat_date 없는 날짜 탐지
→ 자동으로 해당 날짜 backfill 실행
→ 누락 감지 로그 기록
```

---

## 4. 캐시 전략

| 캐시 키 | TTL | 대상 |
|---------|-----|------|
| `stats:today` | 1분 | 오늘 실시간 스냅샷 |
| `stats:daily:{date}` | 24시간 | 확정된 일별 통계 |
| `stats:monthly:{year}:{month}` | 1시간 | 월별 통계 |

---

## 5. API 설계

### 권한
- 통계 전체: **MASTER 전용**

### 엔드포인트

```
GET /api/admin/statistics/daily
  Params: startDate (YYYY-MM-DD), endDate (YYYY-MM-DD), default 최근 30일
  Returns: List<DailyStatisticsResponse>

GET /api/admin/statistics/weekly
  Params: year, startWeek, endWeek
  Returns: List<WeeklyStatisticsResponse>

GET /api/admin/statistics/monthly
  Params: year
  Returns: List<MonthlyStatisticsResponse>

GET /api/admin/statistics/summary
  Returns: 오늘 실시간 스냅샷 (Redis 1분 캐시)

POST /api/admin/statistics/backfill
  Params: startDate, endDate
  Returns: "집계 완료 메시지"
```

### 응답 구조 (DailyStatisticsResponse)

```json
{
  "statDate": "2026-04-17",
  "users": {
    "newUsers": 12,
    "activeUsers": 340,
    "newProviders": 3
  },
  "care": {
    "newRequests": 25,
    "completed": 18,
    "cancelled": 4,
    "completionRate": 81.8
  },
  "revenue": {
    "totalRevenue": 450000,
    "transactionCount": 18,
    "avgTransaction": 25000
  },
  "community": {
    "newPosts": 47,
    "newMeetups": 5,
    "meetupParticipants": 32
  },
  "moderation": {
    "newReports": 2,
    "resolvedReports": 1
  }
}
```

---

## 6. 데이터 보관 정책

| 테이블 | 보관 기간 | 삭제 방식 |
|--------|---------|---------|
| daily_statistics | 1년 | 배치로 만료 행 삭제 |
| weekly_statistics | 무기한 | - |
| monthly_statistics | 무기한 | - |

---

## 7. 기존 코드 변경 범위

| 대상 | 변경 내용 |
|------|---------|
| `DailyStatistics` 엔티티 | Integer → BIGINT, 컬럼 추가 |
| `DailyStatisticsRepository` | 쿼리 메서드 추가 |
| `WeeklyStatistics` 엔티티 | 신규 생성 |
| `MonthlyStatistics` 엔티티 | 신규 생성 |
| `StatisticsScheduler` | 롤업 로직 + 누락 감지 추가 |
| `StatisticsService` | 매출 이벤트 메서드 추가 |
| `AdminStatisticsController` | 엔드포인트 추가, 권한 MASTER로 통일 |
| 결제 서비스 | `StatisticsService.recordPayment()` 호출 추가 |
