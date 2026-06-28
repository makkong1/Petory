# Step 3 — C3/W3: recordPayment 비관적 락 + DataIntegrityViolationException 재시도 처리

## 전제

Step 1, 2와 독립적으로 실행 가능하다.

---

## 목표

동시 결제 진입 시 `recordPayment`의 read-modify-write 패턴에서 발생하는 두 가지 문제를 수정한다.

1. **C3 (lost update)**: 동시 UPDATE 경합 → `totalRevenue`, `transactionCount` 유실
2. **W3 (duplicate key)**: row가 없을 때 동시 INSERT → `DataIntegrityViolationException` 상위 전파

**수정 방향**: `findByStatDate` → 비관적 락 버전 `findByStatDateForUpdate`로 교체.
동시 INSERT 경합은 catch 후 재시도(1회).

---

## 변경 상세

### 1. `SpringDataJpaDailyStatisticsRepository.java` — 락 쿼리 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/repository/SpringDataJpaDailyStatisticsRepository.java`

추가할 import:
```java
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
```

추가할 메서드 (기존 `findByStatDate` 아래에):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM DailyStatistics d WHERE d.statDate = :date")
@RepositoryMethod("일별 통계: 날짜로 조회 (비관적 락)")
Optional<DailyStatistics> findByStatDateForUpdate(@Param("date") LocalDate date);
```

---

### 2. `DailyStatisticsRepository.java` — 포트 인터페이스에 메서드 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/repository/DailyStatisticsRepository.java`

추가할 메서드:
```java
Optional<DailyStatistics> findByStatDateForUpdate(LocalDate statDate);
```

---

### 3. `JpaDailyStatisticsAdapter.java` — 어댑터에 위임 구현 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/repository/JpaDailyStatisticsAdapter.java`

추가할 메서드:
```java
@Override
public Optional<DailyStatistics> findByStatDateForUpdate(LocalDate statDate) {
    return jpaRepository.findByStatDateForUpdate(statDate);
}
```

---

### 4. `StatisticsService.java` — `recordPayment` 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/statistics/service/StatisticsService.java`

추가할 import:
```java
import org.springframework.dao.DataIntegrityViolationException;
```

#### 변경 전

```java
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
```

#### 변경 후

```java
@Transactional
public void recordPayment(BigDecimal amount) {
    LocalDate today = LocalDate.now();
    try {
        DailyStatistics stats = dailyStatisticsRepository.findByStatDateForUpdate(today)
                .orElse(DailyStatistics.builder().statDate(today).build());

        BigDecimal newRevenue = stats.getTotalRevenue().add(amount);
        long newCount = stats.getTransactionCount() + 1;
        BigDecimal newAvg = newRevenue.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        stats.setTotalRevenue(newRevenue);
        stats.setTransactionCount(newCount);
        stats.setAvgTransaction(newAvg);
        dailyStatisticsRepository.save(stats);
    } catch (DataIntegrityViolationException e) {
        // 동시 INSERT 경합: 상대방이 먼저 insert한 row에 락 잡고 재시도
        DailyStatistics stats = dailyStatisticsRepository.findByStatDateForUpdate(today)
                .orElseThrow(() -> new IllegalStateException("통계 row 재조회 실패: " + today));

        BigDecimal newRevenue = stats.getTotalRevenue().add(amount);
        long newCount = stats.getTransactionCount() + 1;
        BigDecimal newAvg = newRevenue.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        stats.setTotalRevenue(newRevenue);
        stats.setTransactionCount(newCount);
        stats.setAvgTransaction(newAvg);
        dailyStatisticsRepository.save(stats);
    }
}
```

---

## 동작 설명

| 시나리오 | 동작 |
|---------|------|
| 동시 결제 10건 (row 이미 존재) | 첫 번째 트랜잭션이 `SELECT FOR UPDATE` → 락 획득. 나머지는 대기 → 순차 처리. lost update 없음 |
| 동시 결제 2건 (row 미존재) | 둘 다 `findByStatDateForUpdate` → 없음 → INSERT 시도 → 한 쪽이 DataIntegrityViolationException → catch → 재조회 후 락 획득 → 처리 |
| 단일 결제 (정상 경로) | 기존과 동일, 락 overhead만 추가 |

---

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
```

포트-어댑터 구조 일관성 확인:
```bash
# 3개 파일 모두 findByStatDateForUpdate 시그니처 존재하는지 확인
grep -n "findByStatDateForUpdate" \
  backend/main/java/com/linkup/Petory/domain/statistics/repository/DailyStatisticsRepository.java \
  backend/main/java/com/linkup/Petory/domain/statistics/repository/JpaDailyStatisticsAdapter.java \
  backend/main/java/com/linkup/Petory/domain/statistics/repository/SpringDataJpaDailyStatisticsRepository.java
```

---

## 주의

- `@Lock(PESSIMISTIC_WRITE)`는 트랜잭션 안에서만 동작한다. `recordPayment`는 이미 `@Transactional`이므로 정상.
- `DataIntegrityViolationException` 재시도는 **1회**만 수행한다. 2회 연속 중복 INSERT는 극히 드문 시나리오이며, 그 경우 `orElseThrow`로 상위 전파한다.
- `PetCoinEscrowService.releaseToProvider`는 이미 `@Transactional`이므로 `recordPayment` 호출 시 외부 트랜잭션이 존재한다. 락 획득 후 에스크로 트랜잭션 커밋 시 락 해제된다.
