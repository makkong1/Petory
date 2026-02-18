# Meetup 백엔드 성능 최적화 리팩토링

## 개요
Meetup 도메인의 백엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**문서 구조**:
- **리팩토링**: `nearby-meetups/`, `subquery-optimization/`, `duplicate-query-removal/`, `stream-operation-refactoring/`
- **트러블슈팅**: `participants-query/` - 참여자 조회 N+1 쿼리 (런타임 발견 이슈)

---

## 🔴 Critical (긴급) - 리팩토링

### 1. 인메모리 필터링 제거 - `getNearbyMeetups()` ✅ **해결 완료**

**파일**: `MeetupService.java` (Lines 187-343)

**현재 문제**:
- `findAllNotDeleted()`로 전체 meetup을 메모리에 로드
- Java에서 Haversine 거리 계산 수행 (모든 meetup에 대해)
- 여러 번의 Stream 연산으로 필터링

```java
// 현재 코드 (비효율적)
List<Meetup> allMeetups = meetupRepository.findAllNotDeleted(); // 전체 로드
allMeetups.stream()
    .filter(m -> calculateDistance(...) <= radius) // 메모리에서 필터링
    .filter(m -> m.getDate().isAfter(...))
    .collect(Collectors.toList());
```

**해결 방안**:
```java
// 개선된 코드 - DB 쿼리 활용
List<Meetup> nearbyMeetups = meetupRepository.findNearbyMeetups(lat, lng, radius, currentDate);
// SpringDataJpaMeetupRepository에 날짜/상태 필터링 추가된 쿼리 사용
```

**예상 효과**: O(n) → O(1) 메모리 사용, 쿼리 성능 10배 이상 개선

**실제 성능 결과**: [성능 비교 분석](./nearby-meetups/performance-comparison.md)

| 지표 | Before | After (Bounding Box) | 개선율 | 비고 |
|------|--------|---------------------|--------|------|
| 전체 실행 시간 | 486 ms | 273 ms | **43.8% 감소** | ✅ 개선 달성 |
| DB 쿼리 시간 | 241 ms | 143 ms | **40.7% 감소** | ✅ 개선 달성 |
| 메모리 사용량 | 1.48 MB | 0.21 MB | **85.8% 감소** | ✅ 기대치 달성 |
| 필터링/정렬 시간 | 20 ms | 0 ms | **100% 제거** | ✅ 완벽 |
| 스캔 행 수 | 2958 개 | 117 개 | **96.0% 감소** | ✅ 인덱스 활용 |

**성능 향상이 기대보다 낮은 이유 (초기 분석)**:
1. **DB 쿼리 복잡도**: Haversine 계산이 DB에서도 복잡하여 쿼리 시간이 크게 줄지 않음
2. ~~**인덱스 부재**~~: ✅ **해결됨** - 인덱스는 이미 존재했으나 `IS NOT NULL` 조건으로 활용하지 못함
3. **쿼리 최적화 한계**: 날짜/상태 필터링 추가로 쿼리가 더 복잡해짐
4. **데이터 분포**: 테스트 데이터가 작아(1,000개) DB 최적화 효과가 제한적

**추가 최적화 완료** ✅:
- [x] Bounding Box 방식 적용 ✅ - `idx_meetup_location` 인덱스 활용 성공
- [x] 쿼리 실행 계획 분석 및 최적화 ✅ - [인덱스 분석](./index-analysis.md), [쿼리 튜닝](./query-tuning.md)
- [x] EXPLAIN 실행 계획 확인 ✅ - [실행 계획 결과](./explain-results.md)

**최종 최적화 결과**:
- ✅ **인덱스 사용**: `idx_meetup_location` (type: range) - [인덱스 분석](./nearby-meetups/index-analysis.md)
- ✅ **스캔 행 수**: 2958개 → 117개 (**96% 감소**)
- ✅ **인덱스 조건 푸시다운**: `Using index condition` 활용
- ✅ **실제 성능 개선**: 전체 시간 43.8% 감소, DB 쿼리 40.7% 감소, 메모리 85.8% 감소

---

### 2. Admin 컨트롤러 인메모리 필터링

**파일**: `AdminMeetupController.java` (Lines 35-64)

**현재 문제**:
- 전체 meetup 로드 후 status/keyword 필터링을 메모리에서 수행

**해결 방안**:
```java
// Repository에 메서드 추가
@Query("SELECT m FROM Meetup m WHERE " +
       "(:status IS NULL OR m.status = :status) AND " +
       "(:keyword IS NULL OR m.title LIKE %:keyword% OR m.description LIKE %:keyword%) " +
       "ORDER BY m.createdAt DESC")
Page<Meetup> findByStatusAndKeyword(
    @Param("status") MeetupStatus status,
    @Param("keyword") String keyword,
    Pageable pageable
);
```

---

## 🔴 트러블슈팅 (런타임 발견 이슈)

### 3. N+1 쿼리 해결 - `findByUserIdxOrderByJoinedAtDesc()` ✅ **해결 완료**

**파일**: `SpringDataJpaMeetupParticipantsRepository.java` (Line 23)

**발견 경로**: 참여 모임 목록 조회 시 PrepareStatement 수 급증 (102개) → 프로파일링으로 N+1 발견

**문제 원인**:
- JOIN FETCH 없이 연관 엔티티 조회
- `meetup`, `user` 접근 시 Lazy Loading으로 추가 쿼리 발생
- 참여 모임 100개 기준: PrepareStatement 102개

```java
// Before
List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx);
```

**해결**:
```java
@Query("SELECT mp FROM MeetupParticipants mp " +
       "JOIN FETCH mp.meetup m " +
       "JOIN FETCH mp.user u " +
       "WHERE mp.user.idx = :userIdx " +
       "ORDER BY mp.joinedAt DESC")
List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(@Param("userIdx") Long userIdx);
```

**성능 측정 결과**:
- PrepareStatement 수: 102개 → 2개 (**98.0% 감소**)
- 상세: [participants-query/performance-comparison-participants.md](./participants-query/performance-comparison-participants.md)

---

## 🟠 High Priority - 리팩토링

### 4. 서브쿼리 최적화 - `findAvailableMeetups()` ✅ **리팩토링 완료**

**파일**: `SpringDataJpaMeetupRepository.java` (Lines 51-57)

**리팩토링 전 문제**:
```java
// 서브쿼리 사용 (실행 계획 비효율)
(SELECT COUNT(p) FROM MeetupParticipants p WHERE p.meetup.idx = m.idx) < m.maxParticipants
```

**리팩토링 후 해결**:
```java
@Query("SELECT m FROM Meetup m " +
       "LEFT JOIN m.participants p " +
       "WHERE m.date > :currentDate " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "GROUP BY m.idx " +
       "HAVING COUNT(p) < m.maxParticipants " +
       "ORDER BY m.date ASC")
List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate);
```

**리팩토링 결과**:
- ✅ 서브쿼리 → LEFT JOIN + GROUP BY + HAVING으로 변경 완료
- ✅ 실행 시간: 156ms → 57ms (**63.5% 감소**)
- ✅ 메모리 사용량: 19.07 MB → 2.00 MB (**89.5% 감소**)
- 📊 [성능 비교 결과](./subquery-optimization/performance-comparison.md)
- 📊 [리팩토링 전 성능 측정 결과](./subquery-optimization/performance-results-before.md)

---

### 5. Converter N+1 방지

**파일**: `MeetupParticipantsConverter.java` (Lines 11-21)

**문제**: `toDTO()`에서 연관 엔티티 접근 시 Lazy Loading으로 추가 쿼리 발생

**해결 방안**: 호출하는 모든 쿼리에서 JOIN FETCH 사용 확인

---

## 🟡 Medium Priority

### 6. 중복 DB 쿼리 제거 ✅ **리팩토링 완료**

**파일**: `MeetupService.java` (Lines 237-297)

**리팩토링 전 문제**:
```java
// 현재: 같은 meetup 두 번 조회
Meetup meetup = meetupRepository.findById(meetupIdx); // 첫 번째 조회
// ... 처리 ...
meetup = meetupRepository.findById(meetupIdx); // 두 번째 조회 (불필요)
```

**리팩토링 후 해결**:
```java
// 첫 번째 조회
Meetup meetup = meetupRepository.findById(meetupIdx);
// ... 처리 ...
// 영속성 컨텍스트 새로고침 (중복 DB 쿼리 제거)
entityManager.refresh(meetup);
```

**리팩토링 결과**:
- ✅ 중복 `findById()` 호출 제거
- ✅ `entityManager.refresh()` 사용으로 영속성 컨텍스트 동기화
- 📊 [상세 리팩토링 문서](./duplicate-query-removal.md)

---

### 7. Stream 연산 최적화 ✅ **리팩토링 완료**

**파일**: `MeetupService.java`

**리팩토링 전 문제**:
- 여러 메서드에서 동일한 Stream 변환 로직 반복 (7개 메서드)
- 코드 중복으로 인한 유지보수 어려움
- 가독성 저하

**리팩토링 후 해결**:
```java
// 공통 메서드 추출
private List<MeetupDTO> convertToDTOs(List<Meetup> meetups) {
    return meetups.stream()
            .map(converter::toDTO)
            .collect(Collectors.toList());
}

// 사용 예시
public List<MeetupDTO> getAllMeetups() {
    // ...
    List<MeetupDTO> result = convertToDTOs(meetups);
    // ...
}
```

**리팩토링 결과**:
- ✅ 중복 코드 제거 (7개 메서드 → 공통 메서드 2개)
- ✅ 유지보수성 향상 (변경 시 한 곳만 수정)
- ✅ 가독성 향상 (비즈니스 로직 명확화)
- 📊 [상세 리팩토링 문서](./stream-operation-refactoring.md)

---

### 8. 데이터베이스 인덱스 추가

**Entity 클래스에 추가 필요**:
```java
@Table(name = "meetup", indexes = {
    @Index(name = "idx_meetup_deleted", columnList = "is_deleted"),
    @Index(name = "idx_meetup_date", columnList = "date"),
    @Index(name = "idx_meetup_status", columnList = "status"),
    @Index(name = "idx_meetup_organizer", columnList = "organizer_idx"),
    @Index(name = "idx_meetup_location", columnList = "latitude,longitude")
})
public class Meetup { ... }
```

---

### 9. 성능 측정 코드 AOP 추출

**파일**: `MeetupService.java` (Lines 155-174, 188-309, 469-488, 495-516, 521-542)

**현재**: 각 메서드에 중복된 성능 측정 코드

**해결**:
```java
@Aspect
@Component
public class PerformanceAspect {
    @Around("@annotation(Timed)")
    public Object measureTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        log.info("{} executed in {}ms", joinPoint.getSignature(), System.currentTimeMillis() - start);
        return result;
    }
}
```

---

### 10. 캐싱 적용

```java
@Cacheable(value = "meetups", key = "#id")
public MeetupDTO getMeetupById(Long id) { ... }

@Cacheable(value = "allMeetups", unless = "#result.isEmpty()")
public List<MeetupDTO> getAllMeetups() { ... }

@CacheEvict(value = {"meetups", "allMeetups"}, allEntries = true)
public void createMeetup(...) { ... }
```

---

## 🟢 Low Priority

### 11. LIKE 쿼리 최적화

**파일**: `SpringDataJpaMeetupRepository.java` (Lines 44-49)

**문제**: `%keyword%` 패턴은 인덱스 사용 불가

**해결**: MySQL FULLTEXT 인덱스 + `MATCH...AGAINST` 사용

---

### 12. 불필요한 save 제거

**파일**: `MeetupService.java` (Lines 75-87)

```java
// 현재: 두 번 save
Meetup saved = meetupRepository.save(meetup);
saved.setCurrentParticipants(1);
meetupRepository.save(saved); // 불필요

// 개선: 한 번만 save
meetup.setCurrentParticipants(1); // save 전에 설정
Meetup saved = meetupRepository.save(meetup);
```

---

## 체크리스트

- [x] `getNearbyMeetups()` DB 쿼리로 변경 ✅ [성능 비교](./nearby-meetups/performance-comparison.md)
- [x] 인덱스 활용 최적화 ✅ Bounding Box 방식으로 `idx_meetup_location` 활용 [인덱스 분석](./nearby-meetups/index-analysis.md)
- [x] 쿼리 실행 계획 분석 ✅ [EXPLAIN 결과](./nearby-meetups/explain-results.md)
- [x] N+1 쿼리 해결 (트러블슈팅) ✅ [성능 비교](./participants-query/performance-comparison-participants.md)
- [ ] Admin 필터링 DB 쿼리로 이동
- [x] 서브쿼리 → JOIN + GROUP BY 변경 ✅ [리팩토링 완료](./subquery-optimization/서브쿼리%20최적화.md)
- [x] 중복 쿼리 제거 ✅ [리팩토링 완료](./duplicate-query-removal.md)
- [x] Stream 연산 최적화 ✅ [리팩토링 완료](./stream-operation-refactoring.md)
- [ ] 캐싱 적용
- [ ] 성능 측정 AOP 추출

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| 메모리 사용 | O(n) 전체 로드 | O(1) 필요한 것만 |
| 쿼리 수 | N+1 다수 발생 | 1개 쿼리 |
| 응답 시간 | ~500ms (추정) | ~50ms (추정) |
