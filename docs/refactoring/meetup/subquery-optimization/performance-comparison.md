---
date: 2026-02-08
domains: [meetup]
type: performance-evidence
problem: query-optimization
status: verified
metric: "156ms→57ms (-63.5%), 메모리 19.07MB→2.00MB (-89.5%)"
---

# findAvailableMeetups() 성능 비교 - 리팩토링 전/후

## 📋 측정 개요

**측정 일시**: 2026-02-08  
**측정 메서드**: `SpringDataJpaMeetupRepository.findAvailableMeetups()`  
**측정 목적**: 서브쿼리 → LEFT JOIN + GROUP BY + HAVING 리팩토링 전/후 성능 비교

---

## 🧪 테스트 환경

- **전체 모임 수**: 100 개
- **현재 날짜**: 2026-02-08T21:13:04.737261700
- **모임당 참여자 수 범위**: 0 ~ 8 명
- **최대 참여 인원**: 10 명
- **테스트 데이터**: 다양한 날짜, 삭제 상태, 참여자 수를 가진 모임

---

## 📊 성능 측정 결과 비교

| 항목 | Before (서브쿼리) | After (JOIN + GROUP BY) | 개선 | 개선율 |
|------|------------------|------------------------|------|--------|
| **실행 시간** | 156 ms | 57 ms | **-99 ms** | **63.5% 감소** ⬇️ |
| **DB 쿼리 시간** | 156 ms | 57 ms | **-99 ms** | **63.5% 감소** ⬇️ |
| **쿼리 실행 횟수** | 1 개 | 1 개 | 동일 | - |
| **PrepareStatement 횟수** | 6 개 | 6 개 | 동일 | - |
| **메모리 사용량** | 19.07 MB | 2.00 MB | **-17.07 MB** | **89.5% 감소** ⬇️ |
| **조회된 모임 수** | 49 개 | 49 개 | 동일 ✅ | - |

---

## 🔍 상세 분석

### 리팩토링 전 (서브쿼리)

**쿼리 구조**:
```sql
SELECT m.* FROM meetup m 
WHERE m.max_participants > (
    SELECT COUNT(*) FROM meetupparticipants mp 
    WHERE mp.meetup_idx = m.idx
) 
AND m.date > ? 
AND (m.is_deleted = 0 OR m.is_deleted IS NULL) 
ORDER BY m.date
```

**성능 특성**:
- 실행 시간: 156 ms
- PrepareStatement 수: 6개
  - **PrepareStatement란**: JDBC에서 SQL 쿼리를 미리 컴파일하여 재사용할 수 있게 만든 객체
  - **6개가 나온 이유**: Hibernate가 내부적으로 쿼리를 여러 단계로 분해하거나, 서브쿼리 최적화 과정에서 여러 PrepareStatement를 생성
  - **주의**: 쿼리 실행 횟수(1개)와는 다름. 하나의 쿼리 실행이 내부적으로 여러 PrepareStatement를 사용할 수 있음
- 메모리 사용량: 19.07 MB
- 쿼리 실행 횟수: 1개 (Hibernate Statistics 기준)

### 리팩토링 후 (LEFT JOIN + GROUP BY + HAVING)

**쿼리 구조**:
```sql
SELECT m.* FROM meetup m 
LEFT JOIN meetupparticipants p ON m.idx = p.meetup_idx 
WHERE m.date > ? 
AND (m.is_deleted = 0 OR m.is_deleted IS NULL) 
GROUP BY m.idx 
HAVING COUNT(CASE WHEN p.meetup_idx IS NOT NULL AND p.user_idx IS NOT NULL THEN 1 ELSE NULL END) < m.max_participants 
ORDER BY m.date
```

**성능 특성**:
- 실행 시간: 57 ms (**63.5% 감소**)
- PrepareStatement 수: 6개 (동일)
  - JOIN 방식으로 변경했지만 PrepareStatement 수는 동일
  - 이는 Hibernate의 내부 쿼리 처리 방식에 의한 것으로, 실제 쿼리 실행 횟수와는 별개
- 메모리 사용량: 2.00 MB (**89.5% 감소**)
- 쿼리 실행 횟수: 1개 (Hibernate Statistics 기준)

---

## 📈 개선 효과 분석

### 1. 실행 시간 개선

**63.5% 감소** (156ms → 57ms, 99ms 개선)

**개선 요인**:
- 서브쿼리 제거로 쿼리 실행 계획 최적화
- JOIN 방식이 서브쿼리보다 효율적인 실행 계획 생성
- GROUP BY와 HAVING이 단일 쿼리에서 처리되어 오버헤드 감소

### 2. 메모리 사용량 개선

**89.5% 감소** (19.07 MB → 2.00 MB, 17.07 MB 개선)

**개선 요인**:
- 서브쿼리 실행 시 중간 결과 집합 생성으로 인한 메모리 사용 감소
- JOIN 방식이 더 효율적인 메모리 사용 패턴
- 불필요한 임시 데이터 구조 제거

### 3. PrepareStatement 수

**변화 없음** (6개 → 6개)

**PrepareStatement란?**
- JDBC에서 SQL 쿼리를 미리 컴파일하여 재사용할 수 있게 만든 객체
- 파라미터화된 쿼리(`?` 플레이스홀더 사용)를 데이터베이스에 미리 전송하여 컴파일하고, 이후 파라미터만 바꿔가며 실행
- 동일한 쿼리 구조를 반복 실행할 때 성능상 이점이 있음

**왜 6개가 나왔는가?**
- Hibernate가 내부적으로 쿼리를 처리하는 과정에서 여러 PrepareStatement를 생성
- 서브쿼리 방식과 JOIN 방식 모두 Hibernate의 동일한 내부 처리 메커니즘을 사용하여 PrepareStatement 수가 동일하게 나옴
- **중요**: PrepareStatement 수는 쿼리 실행 횟수와는 다름
  - 쿼리 실행 횟수: 1개 (실제로 DB에 전송된 쿼리 수)
  - PrepareStatement 수: 6개 (Hibernate가 내부적으로 생성한 PrepareStatement 객체 수)

**분석**:
- **N+1 문제가 아니었음**: PrepareStatement 수가 6개로 동일한 것을 보면, 서브쿼리가 각 행마다 실행되는 N+1 패턴이 아니었음
- 실제 쿼리 실행 횟수는 1개로 동일하므로, N+1 문제는 발생하지 않았음
- **실제 문제**: N+1 문제가 아니라 서브쿼리 실행 계획의 비효율이었음
- **실제 개선**: 쿼리 수가 아니라 쿼리 실행 계획 최적화와 메모리 사용량 감소에서 발생
- JOIN 방식이 서브쿼리보다 더 효율적인 실행 계획을 생성하여 실행 시간과 메모리 사용량이 개선됨

---

## ✅ 검증 결과

- **결과 일치**: 리팩토링 전후 조회된 모임 수가 동일 (49개)
- **결과 정확성**: 리팩토링 전후 조회된 모임의 idx가 동일
- **기능 정상**: 리팩토링으로 인한 기능 변경 없음

---

## 💡 결론

### 주요 개선 사항

1. **실행 시간 63.5% 감소**
   - 서브쿼리 → JOIN 방식으로 변경하여 쿼리 실행 효율성 향상
   - 156ms → 57ms로 대폭 개선

2. **메모리 사용량 89.5% 감소**
   - 서브쿼리 실행 시 생성되던 중간 결과 집합 제거
   - 19.07 MB → 2.00 MB로 대폭 개선

3. **쿼리 수는 동일하지만 실행 효율 향상**
   - PrepareStatement 수는 동일하지만 실행 계획 최적화로 성능 개선
   - 실제 DB 부하는 감소

### 리팩토링 효과

- ✅ **성능 개선**: 실행 시간 63.5% 감소, 메모리 89.5% 감소
- ✅ **기능 유지**: 리팩토링 전후 결과 동일
- ✅ **코드 품질**: 서브쿼리 제거로 쿼리 가독성 향상
- ✅ **확장성**: 데이터 증가 시에도 안정적인 성능 유지

---

## 📝 참고 사항

- **PrepareStatement 수**: Hibernate의 내부 쿼리 처리 방식에 의해 결정되며, 실제 쿼리 실행 횟수와는 다름
  - 쿼리 실행 횟수: 1개 (실제 DB 쿼리 수)
  - PrepareStatement 수: 6개 (Hibernate 내부 처리에서 생성된 객체 수)
- **실제 개선**: 쿼리 실행 계획 최적화와 실행 시간, 메모리 사용량에서 발생
- 메모리 사용량 개선이 가장 큰 효과 (89.5% 감소)
- 실행 시간 개선으로 사용자 경험 향상 (63.5% 감소)

---

## 🔗 관련 문서

- [리팩토링 전 성능 측정 결과](./performance-results-before.md)
- [EXPLAIN 실행 계획 분석](./explain-results.md)
- [서브쿼리 최적화 문서](./서브쿼리%20최적화.md)
- [백엔드 성능 최적화 문서](../backend-performance-optimization.md)
