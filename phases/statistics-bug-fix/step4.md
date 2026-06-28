# Step 4 — W2: ISO 53주차 수정

## 전제

Step 1~3과 독립적으로 실행 가능하다.

---

## 목표

`calcWeeklyRetention`에서 전 주 계산 시 `prevWeek = 52`로 하드코딩된 부분을 수정한다.
ISO 8601 기준 일부 연도(2020, 2026 등)는 53주차가 존재하며, 이 경우 잘못된 주차 데이터를 참조해 retention 계산이 오동작한다.

---

## 배경

ISO 주차 계산 기준: 해당 연도의 첫 번째 목요일이 포함된 주가 1주차.
따라서 12월 28일이 항상 52주차 또는 53주차에 속하며, 해당 날짜의 ISO 주차 수가 그 해의 최대 주차다.

```
LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
```

2026년 예시: 12월 28일이 53주차 → 2026년은 53주차 있음.
현재 코드는 2026년 1주차의 전 주를 52주차로 조회 → 53주차 데이터 누락.

---

## 변경 상세

### `StatisticsScheduler.java` — `calcWeeklyRetention` 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsScheduler.java`

> Step 1 이후에도 `calcWeeklyRetention`은 `StatisticsScheduler`에 남아 있다 (rollupWeekly에서 호출).

#### 변경 전

```java
private BigDecimal calcWeeklyRetention(int year, int weekNumber, long currentWau) {
    int prevWeek = weekNumber - 1;
    int prevYear = year;
    if (prevWeek == 0) {
        prevYear--;
        prevWeek = 52;
    }
    return weeklyStatisticsRepository.findByYearAndWeekNumber(prevYear, prevWeek)
            .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
            : calcRate(currentWau, prev.getActiveUsers()))
            .orElse(BigDecimal.ZERO);
}
```

#### 변경 후

```java
private BigDecimal calcWeeklyRetention(int year, int weekNumber, long currentWau) {
    int prevWeek = weekNumber - 1;
    int prevYear = year;
    if (prevWeek == 0) {
        prevYear--;
        // ISO 8601: 해당 연도 12월 28일의 주차 수가 그 해 최대 주차 (52 또는 53)
        prevWeek = LocalDate.of(prevYear, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
    return weeklyStatisticsRepository.findByYearAndWeekNumber(prevYear, prevWeek)
            .map(prev -> prev.getActiveUsers() == 0 ? BigDecimal.ZERO
            : calcRate(currentWau, prev.getActiveUsers()))
            .orElse(BigDecimal.ZERO);
}
```

> `IsoFields`는 이미 `import java.time.temporal.IsoFields;`로 import 돼 있다 (`StatisticsScheduler.java:9`).

---

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
```

로직 검증 (단위 테스트로 확인):
```java
// 2026년 1주차의 전주 = 2025년 52주차 (2025는 52주까지)
LocalDate.of(2025, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) // → 52

// 2021년 1주차의 전주 = 2020년 53주차 (2020은 53주까지)
LocalDate.of(2020, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) // → 53
```

---

## 주의

- `LocalDate.of(prevYear, 12, 28)`은 어느 해든 존재하는 날짜이므로 DateTimeException 없음.
- 1줄 수정이며 기존 테스트가 있다면 연말 경계값 케이스를 추가하는 것이 좋다.
