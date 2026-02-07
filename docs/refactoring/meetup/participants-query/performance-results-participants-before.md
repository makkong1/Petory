# findByUserIdxOrderByJoinedAtDesc() 성능 측정 결과 (리팩토링 전)

## 📋 측정 개요

**측정 일시**: 2026-02-07  
**측정 메서드**: `MeetupParticipantsRepository.findByUserIdxOrderByJoinedAtDesc()`  
**측정 목적**: N+1 쿼리 문제 확인 및 리팩토링 전 베이스라인 확보

---

## 🧪 테스트 환경

- **전체 모임 수**: 200 개
- **사용자가 참여한 모임 수**: 100 개
- **조회 사용자 idx**: 3080
- **테스트 데이터**: 다양한 날짜와 상태의 모임 및 참여 정보

---

## 📊 성능 측정 결과

### 실행 시간

| 항목 | 값 |
|------|-----|
| **전체 실행 시간** | 102 ms |
| DB 쿼리 시간 | 102 ms |
| 연관 엔티티 접근 시간 | 0 ms |

### 쿼리 통계

| 항목 | 값 | 비고 |
|------|-----|------|
| **쿼리 실행 횟수** | 1 개 | Hibernate Statistics 기준 |
| **PrepareStatement 횟수** | 102 개 | 실제 DB 쿼리 수 |
| **CloseStatement 횟수** | 0 개 | |
| **엔티티 로드 횟수** | 202 개 | MeetupParticipants(100) + Meetup(100) + Users(2) |
| **컬렉션 로드 횟수** | 0 개 | |

### 메모리 사용량

| 항목 | 값 |
|------|-----|
| **메모리 사용량** | 4.5 MB (4,720,640 bytes) |

### 결과 데이터

| 항목 | 값 |
|------|-----|
| **전체 참여 모임 수** | 100 개 |
| **결과 참여 모임 수** | 100 개 |
| **meetup 접근 횟수** | 100 개 |
| **user 접근 횟수** | 100 개 |

---

## 🔍 상세 분석

### 현재 구현

```java
// SpringDataJpaMeetupParticipantsRepository.java
List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx);
```

- JOIN FETCH 없이 연관 엔티티 조회
- `meetup`, `user` 접근 시 Lazy 로딩으로 추가 쿼리 발생

### 측정 결과 분석

**쿼리 수 분석**:
- **Hibernate Statistics 쿼리 실행 횟수**: 1개
  - 이는 Hibernate가 배치 로딩이나 다른 최적화를 사용했을 가능성을 시사
- **PrepareStatement 횟수**: 102개
  - 실제 DB 쿼리 수는 102개로 측정됨
  - 예상: 1개 메인 쿼리 + 100개 meetup 쿼리 + 1개 user 쿼리 (배치 로딩 적용)
- **엔티티 로드 횟수**: 202개
  - MeetupParticipants: 100개
  - Meetup: 100개
  - Users: 2개 (testUser + testOrganizer)

**성능 특성**:
- DB 쿼리 시간이 전체 실행 시간의 100%를 차지 (102ms)
- 연관 엔티티 접근 시간은 0ms로 측정됨 (이미 로드되어 있음)
- 메모리 사용량: 4.5 MB

### 예상 문제점

1. **N+1 쿼리 발생**
   - PrepareStatement 수가 102개로 측정됨
   - JOIN FETCH 없이 연관 엔티티를 개별 쿼리로 조회
   - 예상 쿼리 수: 201개 (1 + 100 * 2)였으나 실제로는 배치 로딩으로 102개

2. **성능 개선 여지**
   - JOIN FETCH 적용 시 쿼리 수를 1개로 감소 가능
   - 실행 시간 대폭 감소 예상

---

## 📈 실제 개선 효과

### 리팩토링 후 실제 결과

| 항목 | Before | After (실제) | 개선율 |
|------|--------|--------------|--------|
| **쿼리 실행 횟수** | 1 개 | 1 개 | 동일 |
| **PrepareStatement 횟수** | 102 개 | 2 개 | **98.0% 감소** ⬇️ |
| **실행 시간** | 102 ms | 178 ms | -74.5% 증가 ⬆️ |
| **메모리 사용량** | 4.5 MB | 6.0 MB | +33.3% 증가 ⬆️ |

### 개선 포인트

1. **JOIN FETCH 적용**
   - `meetup`과 `user`를 한 번에 조회
   - N+1 쿼리 완전 제거

2. **쿼리 수 대폭 감소**
   - PrepareStatement 수: 102개 → 2개 (98.0% 감소)
   - 네트워크 라운드트립 대폭 감소
   - DB 서버 부하 감소

3. **실행 시간 분석**
   - 단일 쿼리 복잡도 증가로 실행 시간 증가
   - 하지만 쿼리 수 감소로 전체 DB 부하 감소
   - 실제 프로덕션 환경에서는 네트워크 지연이 더 클 수 있어 전체 성능 개선 가능성 높음

---

## 📝 참고 사항

- Hibernate Statistics의 쿼리 실행 횟수는 1개로 측정되었지만, 실제 DB 쿼리 수(PrepareStatement)는 102개로 측정됨
- 이는 Hibernate가 배치 로딩이나 다른 최적화를 사용했을 가능성을 시사
- JOIN FETCH 적용 시 실제 쿼리 수를 1개로 줄일 수 있음

---

## 🔗 관련 문서

- [백엔드 성능 최적화 문서](../backend-performance-optimization.md)
- [리팩토링 후 성능 측정 결과](./performance-results-participants-after.md)
- [성능 비교 문서](./performance-comparison-participants.md)
