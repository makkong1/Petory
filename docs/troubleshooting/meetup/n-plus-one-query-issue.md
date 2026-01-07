# 모임 조회 시 N+1 쿼리 문제

## 문제 상황

### 1. 모임 목록 조회 시 N+1 문제 (Organizer)

반경 기반 모임 조회(`getNearbyMeetups()`) 시 성능 저하 발생:
- 전체 시간 200ms 중 DB 쿼리 시간이 200ms로 100% 차지
- 모임 74개 조회 후 각 모임의 organizer를 개별 쿼리로 조회 (74번의 추가 쿼리)
- 총 75번의 DB 왕복 발생

### 2. 모임 상세 조회 시 잠재적 N+1 문제 (Participants)

**참고**: 실제 로그에서는 확인되지 않았으나, 이론적으로 발생 가능한 문제로 예방적 해결 적용

모임 상세 조회(`getMeetupById()`) 시 참가자 수만큼 users 쿼리 발생 가능:
- `MeetupConverter.toDTO()`에서 `meetup.getParticipants()` 접근 시 LAZY 로딩 발생
- 각 participant의 `user` 필드도 LAZY 로딩으로 개별 쿼리 발생 가능
- 참가자가 많을수록 쿼리 수 급증 (최대 인원 10명이지만 확장성 고려)

**실제 발생 여부**: 로그에서 확인되지 않음 (모임 최대 인원 10명으로 제한되어 있어 실제 영향은 제한적)

---

## 원인 분석

### 1. 모임 목록 조회 문제

**원인:**
- `findAllNotDeleted()` 메서드가 JOIN FETCH를 사용하지 않음
- `Meetup` 엔티티의 `organizer` 필드가 `@ManyToOne` LAZY 로딩
- `MeetupConverter.toDTO()`에서 `organizer.getIdx()`, `organizer.getUsername()` 접근 시 LAZY 로딩 발생
- 결과: 1번의 모임 조회 + 74번의 organizer 조회 = 총 75번의 DB 왕복

**엔티티 구조:**
```java
@Entity
public class Meetup {
    @ManyToOne
    @JoinColumn(name = "organizer_idx", nullable = false)
    private Users organizer; // LAZY 로딩
}
```

**Converter에서의 접근:**
```java
public MeetupDTO toDTO(Meetup meetup) {
    Users organizer = meetup.getOrganizer(); // LAZY 로딩 발생
    return MeetupDTO.builder()
            .organizerIdx(organizer != null ? organizer.getIdx() : null)
            .organizerName(organizer != null ? organizer.getUsername() : null)
            .build();
}
```

### 2. 모임 상세 조회 문제 (잠재적)

**원인:**
- `getMeetupById()`가 `findById()`만 사용하여 JOIN FETCH 없음
- `Meetup` 엔티티의 `participants` 필드가 `@OneToMany` LAZY 로딩
- `MeetupConverter.toDTO()`에서 `meetup.getParticipants()` 접근 시 LAZY 로딩 발생 가능
- 각 `MeetupParticipants`의 `user` 필드도 LAZY 로딩으로 개별 쿼리 발생 가능
- 결과: 1번의 모임 조회 + N번의 participants 조회 + N번의 user 조회 (이론적)

**참고**: 현재 모임 최대 인원이 10명으로 제한되어 있어 실제 영향은 제한적이지만, 확장성과 일관성을 위해 예방적 해결 적용

**엔티티 구조:**
```java
@Entity
public class Meetup {
    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL)
    private List<MeetupParticipants> participants; // LAZY 로딩
}

@Entity
public class MeetupParticipants {
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // LAZY 로딩
}
```

---

## 로그 결과 (문제 발생 시)

### 1. 모임 목록 조회

```
Hibernate: select m1_0.idx,m1_0.created_at,... from meetup m1_0 where m1_0.is_deleted=0 or m1_0.is_deleted is null
Hibernate: select u1_0.idx,u1_0.birth_date,... from users u1_0 where u1_0.idx=?  (74번 반복)
2025-12-28T11:12:56.508+09:00  INFO ... : [성능 측정] getNearbyMeetups - 전체시간: 200ms, DB쿼리: 200ms, 필터링/정렬: 0ms, DTO변환: 0ms, 메모리사용: 3MB, 전체건수: 74, 결과건수: 0
```

**문제점:**
- 쿼리 수: 75번 (모임 1번 + organizer 74번)
- DB 쿼리 시간이 전체 시간의 100% 차지
- 네트워크 왕복 오버헤드 증가

### 2. 모임 상세 조회 (잠재적 문제)

**이론적 발생 가능한 쿼리:**
```
Hibernate: select m1_0.idx,... from meetup m1_0 where m1_0.idx=?
Hibernate: select p1_0.meetup_idx,p1_0.user_idx,... from meetupparticipants p1_0 where p1_0.meetup_idx=?
Hibernate: select u1_0.idx,... from users u1_0 where u1_0.idx=?  (N번 반복)
```

**잠재적 문제점:**
- 쿼리 수: 1 + N + N번 (모임 1번 + participants N번 + user N번)
- 참가자가 많을수록 쿼리 수 급증
- **실제 로그에서는 확인되지 않음** (모임 최대 인원 10명 제한)

---

## 해결 방법

### 1. 모임 목록 조회 해결

**해결:**
- `MeetupRepository.findAllNotDeleted()`에 `JOIN FETCH m.organizer` 추가
- 한 번의 쿼리로 모임과 organizer를 함께 조회

**수정 코드:**
```java
// Before
@Query("SELECT m FROM Meetup m WHERE m.isDeleted = false OR m.isDeleted IS NULL")
List<Meetup> findAllNotDeleted();

// After
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.isDeleted = false OR m.isDeleted IS NULL")
List<Meetup> findAllNotDeleted();
```

### 2. 모임 상세 조회 해결 (예방적)

**해결:**
- `MeetupRepository`에 `findByIdWithDetails()` 메서드 추가
- `organizer`, `participants`, `participants.user`를 모두 JOIN FETCH로 한 번에 조회
- 실제 발생하지 않았지만 확장성과 일관성을 위해 예방적 해결 적용

**추가 코드:**
```java
@Query("SELECT DISTINCT m FROM Meetup m " +
        "LEFT JOIN FETCH m.organizer " +
        "LEFT JOIN FETCH m.participants p " +
        "LEFT JOIN FETCH p.user " +
        "WHERE m.idx = :idx")
Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);
```

**Service 수정:**
```java
// Before
public MeetupDTO getMeetupById(Long meetupIdx) {
    Meetup meetup = meetupRepository.findById(meetupIdx)
            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
    return converter.toDTO(meetup);
}

// After
public MeetupDTO getMeetupById(Long meetupIdx) {
    Meetup meetup = meetupRepository.findByIdWithDetails(meetupIdx)
            .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
    return converter.toDTO(meetup);
}
```

**주의사항:**
- `DISTINCT` 사용: JOIN FETCH로 인한 중복 행 제거
- `LEFT JOIN FETCH`: participants가 없는 경우도 포함

---

## 해결 후 로그 결과

### 1. 모임 목록 조회

```
Hibernate: select m1_0.idx,m1_0.created_at,...,o1_0.idx,o1_0.username,... from meetup m1_0 join users o1_0 on o1_0.idx=m1_0.organizer_idx where m1_0.is_deleted=0 or m1_0.is_deleted is null
2025-12-28T12:29:59.241+09:00  INFO ... : [성능 측정] getNearbyMeetups - 전체시간: 68ms, DB쿼리: 65ms, 필터링/정렬: 2ms, DTO변환: 0ms, 메모리사용: 2MB, 전체건수: 74, 결과건수: 0
```

**개선 효과:**
- 쿼리 수: 75번 → 1번 (98.7% 감소)
- 전체 시간: 200ms → 68ms (66% 개선)
- DB 쿼리 시간: 200ms → 65ms (67.5% 개선)
- 메모리: 3MB → 2MB

### 2. 모임 상세 조회 (예방적 해결)

**해결 후 쿼리:**
```
Hibernate: select distinct m1_0.idx,...,o1_0.idx,...,p1_0.meetup_idx,p1_0.user_idx,...,u1_0.idx,... 
           from meetup m1_0 
           left join users o1_0 on o1_0.idx=m1_0.organizer_idx 
           left join meetupparticipants p1_0 on m1_0.idx=p1_0.meetup_idx 
           left join users u1_0 on u1_0.idx=p1_0.user_idx 
           where m1_0.idx=?
```

**예상 개선 효과:**
- 쿼리 수: 1 + N + N번 → 1번 (N+1 문제 완전 해결)
- 네트워크 왕복: 대폭 감소
- 응답 시간: 크게 개선
- **참고**: 실제 발생하지 않았지만 확장성과 일관성을 위해 적용

---


