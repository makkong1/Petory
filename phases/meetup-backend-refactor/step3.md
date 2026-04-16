# Step 3 — High: findAvailableMeetups 쿼리 교체 + 위치·키워드·주최자 LIMIT + getMeetupParticipants 존재 확인

## 목표
1. `findAvailableMeetups` — GROUP BY/HAVING 제거, `currentParticipants < maxParticipants AND status = RECRUITING` 직접 비교로 교체 (메모리 페이징 위험 해소)
2. `getMeetupsByLocation` / `searchMeetupsByKeyword` / `getMeetupsByOrganizer` — 서비스 레이어에서 결과 상한(`MAX_LIST_SIZE = 500`) 적용
3. `getMeetupParticipants` — 모임 존재·삭제 여부 확인 추가

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|---|---|---|
| 2-5 | `SpringDataJpaMeetupRepository#findAvailableMeetups` | `@EntityGraph` + `GROUP BY` + `Pageable` → Hibernate 메모리 페이징(HHH90003004). 데이터 증가 시 전량 로드 후 앱 메모리에서 잘림 |
| 2-4 | `getMeetupsByLocation` 등 3개 메서드 | 전량 `List` 반환, LIMIT 없음. 현재 프론트 연동 없으나 OOM·응답 지연 위험. 이번 라운드는 **서비스 레이어 상한**만 적용, 풀 페이징은 후속 PR |
| 2-9 | `MeetupService#getMeetupParticipants:251` | 삭제된 모임 참가자도 반환 가능 |

---

## 변경 상세

### 1. `SpringDataJpaMeetupRepository#findAvailableMeetups` — 쿼리 교체

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java`

현재 쿼리:
```java
@EntityGraph(attributePaths = {"organizer"})
@Query("SELECT DISTINCT m FROM Meetup m LEFT JOIN m.participants p " +
       "WHERE m.date > :currentDate " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "GROUP BY m.idx " +
       "HAVING COUNT(p) < m.maxParticipants " +
       "ORDER BY m.date ASC")
List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate, Pageable pageable);
```

변경 후:
```java
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
       "WHERE m.date > :currentDate " +
       "AND m.currentParticipants < m.maxParticipants " +
       "AND m.status = :recruiting " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "ORDER BY m.date ASC")
List<Meetup> findAvailableMeetups(
    @Param("currentDate") LocalDateTime currentDate,
    @Param("recruiting") MeetupStatus recruiting,
    Pageable pageable);
```

변경 이유:
- `GROUP BY m.idx HAVING COUNT(p)` 제거 → `currentParticipants` 컬럼 직접 비교로 단순화
- `@EntityGraph` + `Pageable` 조합 제거 → `JOIN FETCH` + `Pageable`로 교체 (컬렉션 없이 단일 조인이므로 메모리 페이징 없음)
- `status = RECRUITING` 추가 — CLOSED/COMPLETED 모임 제외

> `MeetupStatus` import 추가 필요.

시그니처가 바뀌므로 **호출 체인 3곳 수정**:

#### 1a. `MeetupRepository` 인터페이스
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/MeetupRepository.java`

```java
// 변경 전
List<Meetup> findAvailableMeetups(LocalDateTime currentDate, Pageable pageable);

// 변경 후
List<Meetup> findAvailableMeetups(LocalDateTime currentDate, MeetupStatus recruiting, Pageable pageable);
```

#### 1b. `JpaMeetupAdapter`
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/JpaMeetupAdapter.java`

```java
// 변경 전
@Override
public List<Meetup> findAvailableMeetups(LocalDateTime currentDate, Pageable pageable) {
    return jpaRepository.findAvailableMeetups(currentDate, pageable);
}

// 변경 후
@Override
public List<Meetup> findAvailableMeetups(LocalDateTime currentDate, MeetupStatus recruiting, Pageable pageable) {
    return jpaRepository.findAvailableMeetups(currentDate, recruiting, pageable);
}
```

#### 1c. `MeetupService` 호출부 2곳
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

```java
// getAvailableMeetups() — 무인자 레거시 버전 (:379)
List<Meetup> meetups = meetupRepository.findAvailableMeetups(
    LocalDateTime.now(), MeetupStatus.RECRUITING, Pageable.unpaged());

// getAvailableMeetups(Pageable) — 페이징 버전 (:388)
List<Meetup> list = meetupRepository.findAvailableMeetups(
    LocalDateTime.now(), MeetupStatus.RECRUITING, pageable);
```

---

### 2. 위치·키워드·주최자 조회 — 서비스 레이어 LIMIT 상한

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

상수 선언 (클래스 상단, `DEFAULT_NEARBY_MAX_RESULTS` 옆에):
```java
/** 위치·키워드·주최자 목록 조회 상한 (OOM 방지) */
public static final int MAX_LIST_SIZE = 500;
```

각 메서드에 상한 적용:
```java
// getMeetupsByLocation
public List<MeetupDTO> getMeetupsByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
    List<Meetup> meetups = meetupRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
    return convertToDTOs(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
}

// searchMeetupsByKeyword
public List<MeetupDTO> searchMeetupsByKeyword(String keyword) {
    List<Meetup> meetups = meetupRepository.findByKeyword(keyword);
    return convertToDTOs(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
}

// getMeetupsByOrganizer
public List<MeetupDTO> getMeetupsByOrganizer(Long organizerIdx) {
    List<Meetup> meetups = meetupRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx);
    return convertToDTOs(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
}
```

> `List.subList`는 뷰 반환이므로 원본 List 변경 없음. 풀 페이징 전환 시 이 상한 코드를 제거하고 Pageable로 교체.

---

### 3. `MeetupService#getMeetupParticipants` — 모임 존재 확인 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:251`)

현재 코드:
```java
public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
    return convertToParticipantDTOs(
            meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx));
}
```

변경 후:
```java
public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
    meetupRepository.findByIdWithOrganizer(meetupIdx)
            .orElseThrow(MeetupNotFoundException::new);
    return convertToParticipantDTOs(
            meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx));
}
```

> `findByIdWithOrganizer`는 소프트 삭제 조건이 포함되어 있으므로 삭제된 모임은 `MeetupNotFoundException` 반환.

---

## Acceptance Criteria

```bash
# 컴파일 통과
./gradlew compileJava

# 수동 시나리오 (서버 기동 후)
# a. GET /api/meetups/available → RECRUITING + 인원 미달 모임만 반환 확인
# b. CLOSED/COMPLETED 모임이 available 목록에 포함되지 않는지 확인
# c. 삭제된 모임 idx로 참가자 조회 → 404 반환 확인
```

---

## 주의 사항

- Step 1, 2가 완료(컴파일 통과)된 상태에서 진행.
- `findAvailableMeetups` 시그니처 변경 → 인터페이스·어댑터·서비스 3곳 동시 수정 필요.
- `MeetupStatus` import가 각 파일에 없으면 추가.
- `getMeetupsByOrganizer`는 현재 `List` 반환 메서드이므로 어댑터/인터페이스 수정 불필요 (서비스 레이어만).
