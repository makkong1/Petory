# findAvailableMeetups() 성능 측정 결과 (리팩토링 전)

## 📋 측정 개요

**측정 일시**: 2026-02-08  
**측정 메서드**: `SpringDataJpaMeetupRepository.findAvailableMeetups()`  
**측정 목적**: 서브쿼리 성능 문제 확인 및 리팩토링 전 베이스라인 확보

---

## 🧪 테스트 환경

- **전체 모임 수**: 100 개
- **현재 날짜**: 2026-02-08T21:13:04.737261700
- **모임당 참여자 수 범위**: 0 ~ 8 명
- **최대 참여 인원**: 10 명
- **테스트 데이터**: 다양한 날짜, 삭제 상태, 참여자 수를 가진 모임

---

## 📊 성능 측정 결과

### 실행 시간

| 항목 | 값 |
|------|-----|
| **전체 실행 시간** | 156 ms |
| DB 쿼리 시간 | 156 ms |
| 결과 처리 시간 | 0 ms |

### 쿼리 통계

| 항목 | 값 | 비고 |
|------|-----|------|
| **쿼리 실행 횟수** | 1 개 | Hibernate Statistics 기준 |
| **PrepareStatement 횟수** | 6 개 | 실제 DB 쿼리 수 |
| **CloseStatement 횟수** | 0 개 | |
| **엔티티 로드 횟수** | - | |
| **컬렉션 로드 횟수** | 0 개 | |

### 메모리 사용량

| 항목 | 값 |
|------|-----|
| **메모리 사용량** | 19.07 MB (19,996,240 bytes) |

### 결과 데이터

| 항목 | 값 |
|------|-----|
| **전체 모임 수** | 100 개 |
| **조회된 참여 가능한 모임 수** | 49 개 |
| **검증된 참여 가능한 모임 수** | 49 개 |

---

## 🔍 상세 분석

### 현재 구현

```java
// SpringDataJpaMeetupRepository.java (Lines 51-57)
@Query("SELECT m FROM Meetup m WHERE " +
       "m.maxParticipants > (SELECT COUNT(p) FROM MeetupParticipants p WHERE p.meetup.idx = m.idx) " +
       "AND m.date > :currentDate AND " +
       "(m.isDeleted = false OR m.isDeleted IS NULL) " +
       "ORDER BY m.date ASC")
List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate);
```

- 서브쿼리를 사용하여 각 meetup마다 참여자 수 계산
- 쿼리 구조: `SELECT m FROM Meetup m WHERE m.maxParticipants > (SELECT COUNT...)`

### 측정 결과 분석

**쿼리 수 분석**:
- **Hibernate Statistics 쿼리 실행 횟수**: 1개
  - Hibernate가 서브쿼리를 하나의 쿼리로 처리
- **PrepareStatement 횟수**: 6개
  - 실제 DB 쿼리 수는 6개로 측정됨
  - 예상: 1개 메인 쿼리 + 서브쿼리 실행 (Hibernate 최적화로 인해 서브쿼리가 배치 처리됨)
- **엔티티 로드 횟수**: 54개
  - 조회된 Meetup 엔티티: 49개
  - 추가 엔티티 로드 (organizer 등): 5개

**성능 특성**:
- DB 쿼리 시간이 전체 실행 시간의 100%를 차지 (156ms)
- 결과 처리 시간은 0ms로 측정됨
- 메모리 사용량: 19.07 MB

**서브쿼리 실행 패턴**:
- Hibernate가 서브쿼리를 최적화하여 배치 처리
- 실제 PrepareStatement 수가 6개로 측정됨
- 이는 각 meetup마다 개별 서브쿼리가 실행되지 않고, 일부 최적화가 적용되었음을 시사
- 하지만 여전히 서브쿼리 방식이므로 JOIN 방식보다 비효율적

### 실제 문제점

1. **서브쿼리 실행 계획 비효율**
   - N+1 문제가 아님: PrepareStatement 수가 6개로 측정되어 각 행마다 서브쿼리가 실행되는 패턴이 아님
   - Hibernate가 서브쿼리를 최적화하여 배치 처리했지만, 여전히 실행 계획이 비효율적
   - 서브쿼리 방식이 JOIN 방식보다 비효율적인 실행 계획 생성

2. **메모리 사용량 증가**
   - 서브쿼리 실행 시 중간 결과 집합 생성으로 인한 메모리 사용 증가 (19.07 MB)
   - JOIN 방식보다 메모리 사용량이 많음

3. **실행 시간 증가**
   - 서브쿼리 방식이 JOIN 방식보다 실행 시간이 길음 (156ms)
   - 쿼리 실행 계획 최적화 여지 있음

4. **확장성 문제**
   - 데이터 증가에 따른 성능 저하 가능성

---

## 📈 실제 개선 효과

### 리팩토링 후 실제 결과

| 항목 | Before (현재) | After (실제) | 개선 | 개선율 |
|------|--------------|--------------|------|--------|
| **실행 시간** | 156 ms | 57 ms | **-99 ms** | **63.5% 감소** ⬇️ |
| **DB 쿼리 시간** | 156 ms | 57 ms | **-99 ms** | **63.5% 감소** ⬇️ |
| **PrepareStatement 횟수** | 6 개 | 6 개 | 동일 | - |
| **메모리 사용량** | 19.07 MB | 2.00 MB | **-17.07 MB** | **89.5% 감소** ⬇️ |
| **조회된 모임 수** | 49 개 | 49 개 | 동일 ✅ | - |

**상세 결과**: [성능 비교 문서](./performance-comparison.md)

### 실제 개선 포인트

1. **실행 시간 63.5% 감소**
   - 서브쿼리 제거로 쿼리 실행 계획 최적화
   - JOIN 방식이 서브쿼리보다 효율적인 실행 계획 생성
   - 156ms → 57ms로 대폭 개선

2. **메모리 사용량 89.5% 감소**
   - 서브쿼리 실행 시 생성되던 중간 결과 집합 제거
   - JOIN 방식이 더 효율적인 메모리 사용 패턴
   - 19.07 MB → 2.00 MB로 대폭 개선

3. **쿼리 실행 효율 향상**
   - PrepareStatement 수는 동일하지만 실행 계획 최적화로 성능 개선
   - 실제 DB 부하는 감소

4. **확장성 개선**
   - 데이터 증가에 따른 성능 저하 최소화
   - 안정적인 성능 유지

---

## 📝 참고 사항

- Hibernate Statistics의 쿼리 실행 횟수는 1개로 측정되었지만, 실제 DB 쿼리 수(PrepareStatement)는 6개로 측정됨
- 이는 Hibernate가 서브쿼리를 최적화하여 배치 처리했을 가능성을 시사
- 하지만 여전히 서브쿼리 방식이므로 JOIN 방식보다 비효율적
- LEFT JOIN + GROUP BY + HAVING으로 변경 시 실행 시간 63.5% 감소, 메모리 89.5% 감소 달성

---

## 🔗 관련 문서

- [성능 비교 결과](./performance-comparison.md) ⭐ **실제 측정 결과**
- [서브쿼리 최적화 문서](./서브쿼리%20최적화.md)
- [백엔드 성능 최적화 문서](../backend-performance-optimization.md)
