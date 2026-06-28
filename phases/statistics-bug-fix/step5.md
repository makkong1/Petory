# Step 5 — C0 임시 완화: cron 00:05 변경

## 전제

Step 1~4와 독립적으로 실행 가능하다.

---

## 목표 (임시 완화만)

배치 실행 시각을 18:00 → 00:05로 변경해 C0 버그의 race window를 줄인다.

**C0 버그 요약**:
`lastLoginAt`은 로그인마다 현재 시각으로 덮어 쓰인다. 배치가 18:00에 실행되면
Day N에 로그인한 사용자가 Day N+1 00:00~18:00 사이에 재로그인하면 Day N DAU에서 이탈한다.

**이 Step의 한계**:
- 00:05로 옮기면 Day N+1 00:05 이전 재로그인만 race window가 된다 (18시간 → 5분으로 축소).
- **근본 해결이 아니다.** 자정 직후 재로그인한 사용자는 여전히 누락된다.
- 근본 해결은 `statistics-login-events` 태스크 (login_events 테이블 도입).

---

## 변경 상세

### 1. `StatisticsScheduler.java` — cron 표현식 변경

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsScheduler.java`

#### 변경 전

```java
@Scheduled(cron = "0 0 18 * * ?")
```

#### 변경 후

```java
@Scheduled(cron = "0 5 0 * * ?")  // 매일 00:05 — DAU 집계 race window 최소화 (C0 임시 완화)
```

---

### 2. `StatisticsScheduler.java` — 클래스 Javadoc 수정

클래스 위 Javadoc의 "매일 18:00에" 문구를 "매일 00:05에"로 수정한다.

#### 변경 전

```java
/**
 * 통계 집계 스케줄러. 매일 18:00에 전날 일별 통계를 집계하고, 일요일·월말에 주간·월간 롤업을 실행한다. 누락된 날짜는 자동으로
 * 감지해 backfill한다.
 */
```

#### 변경 후

```java
/**
 * 통계 집계 스케줄러. 매일 00:05에 전날 일별 통계를 집계하고, 일요일·월말에 주간·월간 롤업을 실행한다. 누락된 날짜는 자동으로
 * 감지해 backfill한다.
 */
```

---

### 3. `DailyStatistics.java` — Javadoc 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/entity/DailyStatistics.java`

#### 변경 전

```java
/** 하루 단위로 집계한 플랫폼 통계 스냅샷. 매일 자정 스케줄러가 전날 데이터를 생성한다. */
```

#### 변경 후

```java
/** 하루 단위로 집계한 플랫폼 통계 스냅샷. 매일 00:05 스케줄러가 전날 데이터를 생성한다. */
```

---

## 운영 영향

| 항목 | 내용 |
|------|------|
| 변경 전 실행 시각 | 매일 18:00 |
| 변경 후 실행 시각 | 매일 00:05 |
| 집계 대상 | 동일 (`now().minusDays(1)` = 전날) |
| DB 부하 시간 | 자정 이후 5분 (트래픽 최저 구간) |
| 롤백 방법 | cron 표현식 `"0 0 18 * * ?"` 으로 되돌리기 |

---

## 후속 태스크: `statistics-login-events`

이 Step 이후에도 남는 C0 근본 원인 해결을 위한 별도 태스크:

```
phases/statistics-login-events/
  step1.md — LoginEvent 엔티티 + migration SQL
  step2.md — AuthService, OAuth2Service에 LoginEvent append
  step3.md — StatisticsAggregator: activeUsers → LoginEvent DISTINCT 집계 전환
  step4.md — 과거 통계 보정 불가 범위 명시 (docs 업데이트)
```

`statistics-login-events` 실행 후에는 이 Step(cron 변경)과 함께 동작하며,
`lastLoginAt` 기반 집계를 `login_events` 기반으로 완전 대체한다.

---

## AC (Acceptance Criteria)

```bash
./gradlew compileJava

# cron 변경 확인
grep -n "0 5 0" backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsScheduler.java
```
